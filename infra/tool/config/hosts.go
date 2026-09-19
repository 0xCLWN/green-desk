package config

import (
	"os"
	"sort"

	"gopkg.in/yaml.v3"
)

func LoadHosts(path string) (*Hosts, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var h Hosts
	if err := yaml.Unmarshal(data, &h); err != nil {
		return nil, err
	}
	if h.All.Hosts == nil {
		h.All.Hosts = make(map[string]Node)
	}
	return &h, nil
}

func SaveHosts(path string, h *Hosts) error {
	data, err := yaml.Marshal(h)
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0644)
}

// HostNames returns sorted list of host names (inventory keys).
func (h *Hosts) HostNames() []string {
	names := make([]string, 0, len(h.All.Hosts))
	for name := range h.All.Hosts {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

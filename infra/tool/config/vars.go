package config

import (
	"os"

	"gopkg.in/yaml.v3"
)

func LoadVars(path string) (*Vars, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var v Vars
	if err := yaml.Unmarshal(data, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

func SaveVars(path string, v *Vars) error {
	data, err := yaml.Marshal(v)
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0644)
}

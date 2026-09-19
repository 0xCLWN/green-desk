package config

import (
	"os"

	"gopkg.in/yaml.v3"
)

const vaultHeader = `# Encrypt before committing:
#   ansible-vault encrypt inventory/group_vars/all/vault.yml

`

func LoadVault(path string) (*Vault, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var v Vault
	if err := yaml.Unmarshal(data, &v); err != nil {
		return nil, err
	}
	if v.Clients == nil {
		v.Clients = make(map[string]VaultClient)
	}
	if v.Nodes == nil {
		v.Nodes = make(map[string]VaultNode)
	}
	if v.RelayClients == nil {
		v.RelayClients = make(map[string]VaultRelay)
	}
	return &v, nil
}

func SaveVault(path string, v *Vault) error {
	data, err := yaml.Marshal(v)
	if err != nil {
		return err
	}
	out := append([]byte(vaultHeader), data...)
	return os.WriteFile(path, out, 0600)
}

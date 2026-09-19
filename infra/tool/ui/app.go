package ui

import (
	"path/filepath"

	tea "github.com/charmbracelet/bubbletea"
	"clwn/infra-tool/config"
)

// App holds filesystem paths and the loaded config (mutated in-place by wizards).
type App struct {
	infraDir  string
	hostsPath string
	varsPath  string
	vaultPath string
	hosts     *config.Hosts
	vars      *config.Vars
	vault     *config.Vault
}

func NewApp(infraDir string) (*App, error) {
	hostsPath := filepath.Join(infraDir, "inventory", "hosts.yml")
	varsPath := filepath.Join(infraDir, "inventory", "group_vars", "all", "vars.yml")
	vaultPath := filepath.Join(infraDir, "inventory", "group_vars", "all", "vault.yml")

	hosts, err := config.LoadHosts(hostsPath)
	if err != nil {
		return nil, err
	}
	vars, err := config.LoadVars(varsPath)
	if err != nil {
		return nil, err
	}
	vault, err := config.LoadVault(vaultPath)
	if err != nil {
		return nil, err
	}

	app := &App{
		infraDir:  infraDir,
		hostsPath: hostsPath,
		varsPath:  varsPath,
		vaultPath: vaultPath,
		hosts:     hosts,
		vars:      vars,
		vault:     vault,
	}

	if err := app.initSecrets(); err != nil {
		return nil, err
	}

	return app, nil
}

func (a *App) Run() error {
	p := tea.NewProgram(newModel(a), tea.WithAltScreen())
	_, err := p.Run()
	return err
}

func (a *App) saveAll() error {
	if err := config.SaveHosts(a.hostsPath, a.hosts); err != nil {
		return err
	}
	if err := config.SaveVars(a.varsPath, a.vars); err != nil {
		return err
	}
	return config.SaveVault(a.vaultPath, a.vault)
}

func (a *App) initSecrets() error {
	if a.vault.AdminUsername == "" || a.vault.AdminUsername == "CHANGE_ME" {
		a.vault.AdminUsername = "admin"
		return config.SaveVault(a.vaultPath, a.vault)
	}
	return nil
}

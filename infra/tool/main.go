package main

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"

	"clwn/infra-tool/ui"
)

func main() {
	// Resolve infra/ dir: the tool lives at infra/tool/, so the parent is infra/.
	// Support running from infra/ with `go run ./tool` or from infra/tool/ with `go run .`.
	exe, _ := os.Executable()
	exeDir := filepath.Dir(exe)

	infraDir := findInfraDir(exeDir)
	if infraDir == "" {
		fmt.Fprintln(os.Stderr, "Could not find infra/ directory. Run from inside infra/ or infra/tool/.")
		os.Exit(1)
	}

	app, err := ui.NewApp(infraDir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to load config: %v\n", err)
		os.Exit(1)
	}

	if err := app.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
}

// findInfraDir walks up from dir looking for a directory that contains inventory/hosts.yml.
func findInfraDir(dir string) string {
	for i := 0; i < 4; i++ {
		if _, err := os.Stat(filepath.Join(dir, "inventory", "hosts.yml")); err == nil {
			return dir
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			break
		}
		dir = parent
	}
	// Fallback: try cwd
	cwd, _ := os.Getwd()
	_ = runtime.GOOS
	for i := 0; i < 4; i++ {
		if _, err := os.Stat(filepath.Join(cwd, "inventory", "hosts.yml")); err == nil {
			return cwd
		}
		parent := filepath.Dir(cwd)
		if parent == cwd {
			break
		}
		cwd = parent
	}
	return ""
}

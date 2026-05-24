//go:build !darwin

package ui

func StartupEnabled() bool    { return false }
func EnableStartup() error     { return nil }
func DisableStartup() error    { return nil }

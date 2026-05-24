//go:build !linux && !darwin && !windows

package ui

func EnableSystemProxy(_ int) error { return nil }
func DisableSystemProxy()            {}
func SystemProxyAvailable() bool     { return false }
func NetAdminAvailable() bool        { return true }
func CleanupStaleProxy(_ int)        {}

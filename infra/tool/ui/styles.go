package ui

import "github.com/charmbracelet/lipgloss"

var (
	clrViolet = lipgloss.Color("#A78BFA")
	clrGray   = lipgloss.Color("#6B7280")
	clrGreen  = lipgloss.Color("#10B981")
	clrRed    = lipgloss.Color("#EF4444")
	clrAmber  = lipgloss.Color("#F59E0B")
	clrDark   = lipgloss.Color("#111827")
	clrWhite  = lipgloss.Color("#F9FAFB")
	clrFaint  = lipgloss.Color("#374151")

	sBold = lipgloss.NewStyle().Foreground(clrViolet).Bold(true)
	sDim  = lipgloss.NewStyle().Foreground(clrGray)
	sOK   = lipgloss.NewStyle().Foreground(clrGreen)
	sErr  = lipgloss.NewStyle().Foreground(clrRed)
	sWarn = lipgloss.NewStyle().Foreground(clrAmber)
	sNorm = lipgloss.NewStyle().Foreground(clrWhite)

	sMenuSel = lipgloss.NewStyle().Foreground(clrViolet).Bold(true)
	sMenuKey = lipgloss.NewStyle().Foreground(clrFaint)
)

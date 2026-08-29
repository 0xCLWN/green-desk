MODULE := github.com/0xCLWN/xray-core
BIN    := xray-tray

# Pass vless:// keys as positional args — no named flags needed:
#   make mac 'vless://...'
#   make all 'vless://key1' 'vless://key2'   (first key wins)
KEYS     := $(filter vless%,$(MAKECMDGOALS))
KEY1     := $(firstword $(KEYS))
KEY_X    := $(if $(KEY1),-X $(MODULE)/extra.Keys=$(KEY1))
LDF      := -s -w $(KEY_X)

# Extract the name after # from the key and use it as a binary name suffix.
# (# can't appear literally in Makefile source, so get it via printf.)
H        := $(shell printf '\043')
KEY_NAME := $(if $(KEY1),$(shell printf '%s' '$(KEY1)' | cut -d'$(H)' -f2-))
SFX      := $(if $(KEY_NAME),-$(KEY_NAME))

.PHONY: all mac windows windows-cross linux linux-tui

# mac + windows (zig cross-compile) + linux-tui are cross-compilable from any host.
# linux (GTK systray) requires a Linux host with GTK dev headers installed.
all: mac windows-cross linux linux-tui

mac:
	go build -tags systray \
		-ldflags '$(LDF)' \
		-o $(BIN)$(SFX) ./main

# native Windows (run on a Windows machine with Go installed)
windows:
	go build -tags systray \
		-ldflags '-H windowsgui $(LDF)' \
		-o $(BIN)$(SFX).exe ./main

# cross-compile Windows from any host — requires zig in PATH
windows-cross:
	CGO_ENABLED=1 GOOS=windows GOARCH=amd64 \
	CC='zig cc -target x86_64-windows-gnu' \
	CXX='zig c++ -target x86_64-windows-gnu' \
	go build -tags systray \
		-ldflags '-H windowsgui $(LDF)' \
		-o $(BIN)$(SFX).exe ./main

# GTK systray — requires CGO + GTK dev headers; native Linux only
linux:
	CGO_ENABLED=1 GOOS=linux GOARCH=amd64 go build -tags systray \
		-ldflags '$(LDF)' \
		-o $(BIN)-linux$(SFX) ./main

# Terminal UI — fully static, cross-compilable from any host
linux-tui:
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
		-ldflags '$(LDF)' \
		-o $(BIN)-linux-tui$(SFX) ./main

# Swallow vless:// keys passed as positional args.
# Pattern rules can't match them (Make strips "vless://" as a dir prefix before matching),
# so .DEFAULT catches anything that has no explicit rule instead.
.DEFAULT:
	@:

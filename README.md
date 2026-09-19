# clwn

Monorepo for the **green** VPN stack: a protocol engine, mobile/desktop clients, and a node
admin panel, unified from four previously-separate repos (all under the `0xCLWN` GitHub org)
with full commit history preserved via `git subtree`.

```
clwn/
├── protocols/
│   └── xray-core/    # VLESS/VMess/Trojan/... engine, fork of xtls/Xray-core (MPL-2.0)
├── apps/
│   ├── mobile/       # Android VPN client "green" (Kotlin + Go/gomobile bridge)
│   ├── desktop/      # macOS/Windows VPN client "green" (Kotlin Compose Multiplatform)
│   └── panel/        # VPN node admin panel, fork of MHSanaei/3x-ui (GPL-3.0)
├── infra/
│   ├── ansible/      # VPS provisioning (site.yml, inventory/, roles/xui/)
│   └── tool/         # Go CLI for key generation / deployment / node scanning
└── docs/
```

## Components

| Path | What it is | Stack | Upstream |
|---|---|---|---|
| `protocols/xray-core` | Proxy/protocol engine | Go | fork of [xtls/Xray-core](https://github.com/xtls/Xray-core) (MPL-2.0) |
| `apps/mobile` | Android client | Kotlin + Go (gomobile) | original app, module `swiss/core` |
| `apps/desktop` | Desktop client (macOS/Windows) | Kotlin Compose Multiplatform | original app |
| `apps/panel` | VPN node admin panel | Go + Vue | fork of [MHSanaei/3x-ui](https://github.com/MHSanaei/3x-ui) (GPL-3.0) |
| `infra/ansible` | VPS provisioning for `apps/panel` nodes | Ansible | — |
| `infra/tool` | Key generation / deploy / node-scan CLI | Go | — |

`protocols/` is intentionally its own top-level category so additional engines (e.g. sing-box,
wireguard-go) can be added as siblings to `xray-core` later without reshuffling the layout.

## Build tooling — deliberately independent, not a shared workspace

Each Go module (`protocols/xray-core`, `apps/mobile/go`, `apps/panel`, `infra/tool`) keeps its
own `go.mod`/`go.sum` and builds standalone; there is **no root `go.work`**. That was tried
during setup and reverted: putting them in one workspace forces Go to compute one joint
dependency resolution across all of them, which broke two components outright —

- `apps/mobile`'s vendored `tun2socks` requires an older `gvisor` API than the version pulled in
  transitively for `apps/panel`/`protocols/xray-core`, causing an actual compile error
  (`ForwarderHandler` signature mismatch) once the workspace forced a shared version.
- `apps/panel` hit an "ambiguous import" between `github.com/ugorji/go` and
  `github.com/ugorji/go/codec` that only appears when its module graph is merged with
  `protocols/xray-core`'s.

None of the four modules actually need a live in-repo import of another at build time — `apps/mobile`
pins `protocols/xray-core` as a normal versioned dependency (bump it with
`go get github.com/0xCLWN/xray-core@<commit>` when you want a newer engine build), and
`apps/panel`/`infra/tool` don't depend on it at all. So independent modules, each buildable in
isolation, is the correct shape here, not a shortcut.

`apps/mobile` and `apps/desktop` similarly keep independent Gradle builds (Android vs. JVM-desktop
targets) — no attempt to unify them into one Gradle multi-project build.

## `apps/desktop` and xray-core

`apps/desktop` used to pull in xray-core as a git submodule; that's been replaced with a plain
relative path into `protocols/xray-core` (see `apps/desktop/Makefile`'s `XRAY_DIR`). Pull
upstream changes into `protocols/xray-core` directly and rebuild with `make xray-mac` /
`xray-windows` / `xray-linux` from `apps/desktop/`.

## infra/ secrets

`infra/inventory/group_vars/all/vault.yml` holds real per-node secrets (admin passwords, API
tokens, Reality keys) and is gitignored — never commit it unencrypted. Copy
`vault.yml.example`, fill in real values, then `ansible-vault encrypt vault.yml` before it ever
touches git.

## History

Each component was imported with `git subtree add` (no squash), so full original commit history
is preserved — just not visible via plain `git log -- <path>` for merge commits; use
`git log <merge-commit>^2` to walk a component's imported history, or `git log --all`.

Large regenerable build artifacts were stripped from history before import: `xray-tray` /
`xray_temp` prebuilt binaries from `protocols/xray-core`, and `.aar` gomobile build outputs from
`apps/mobile`.

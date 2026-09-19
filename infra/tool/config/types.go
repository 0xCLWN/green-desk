package config

// Hosts mirrors inventory/hosts.yml
type Hosts struct {
	All struct {
		Hosts map[string]Node `yaml:"hosts"`
	} `yaml:"all"`
}

type Node struct {
	AnsibleHost string  `yaml:"ansible_host"`
	AnsibleUser string  `yaml:"ansible_user"`
	SSHKeyPath  string  `yaml:"ansible_ssh_private_key_file,omitempty"`
	PanelPort   int     `yaml:"xui_panel_port"`
	Inbound     Inbound `yaml:"inbound"`
}

type Inbound struct {
	Port          int      `yaml:"port"`
	Tag           string   `yaml:"tag"`
	RealityTarget string   `yaml:"reality_target"`
	ServerNames   []string `yaml:"reality_server_names"`
	Fingerprint   string   `yaml:"fingerprint"`
}

// Vars mirrors inventory/group_vars/all/vars.yml
type Vars struct {
	Image     string   `yaml:"xui_image"`
	Container string   `yaml:"xui_container_name"`
	DBPath    string   `yaml:"xui_db_path"`
	SubPort   int      `yaml:"xui_sub_port"`
	SubPath   string   `yaml:"xui_sub_path"`
	SubHTTPS  bool     `yaml:"xui_sub_https"`
	Clients   []Client `yaml:"clients"`
	Paths     []Path   `yaml:"paths"`
}

type Client struct {
	Name   string `yaml:"name"`
	Enable *bool  `yaml:"enable,omitempty"`
}

type Path struct {
	Name string   `yaml:"name"`
	Hops []string `yaml:"hops"`
}

// Vault mirrors inventory/group_vars/all/vault.yml
type Vault struct {
	AdminUsername string                 `yaml:"vault_xui_admin_username"`
	Clients       map[string]VaultClient `yaml:"vault_clients"`
	Nodes         map[string]VaultNode   `yaml:"vault_nodes"`
	RelayClients  map[string]VaultRelay  `yaml:"vault_relay_clients"`
}

type VaultClient struct {
	UUID  string `yaml:"uuid"`
	SubID string `yaml:"sub_id"`
}

type VaultNode struct {
	AdminPassword string   `yaml:"admin_password"`
	APIToken      string   `yaml:"api_token"`
	WebBasePath   string   `yaml:"web_base_path"`
	PrivateKey    string   `yaml:"reality_private_key"`
	PublicKey     string   `yaml:"reality_public_key"`
	ShortIDs      []string `yaml:"reality_short_ids"`
}

type VaultRelay struct {
	UUID string `yaml:"uuid"`
}

package main

import (
	"encoding/json"
	"fmt"
	"github.com/0x1488/xray-core/extra"
	"io"
	"log"
	"log/slog"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
	"time"

	"github.com/0x1488/xray-core/common/cmdarg"
	"github.com/0x1488/xray-core/common/errors"
	clog "github.com/0x1488/xray-core/common/log"
	"github.com/0x1488/xray-core/common/platform"
	"github.com/0x1488/xray-core/core"
	"github.com/0x1488/xray-core/main/commands/base"
	xui "github.com/0x1488/xray-core/main/ui"
)

var cmdRun = &base.Command{
	UsageLine: "{{.Exec}} run [-c config.json] [-confdir dir]",
	Short:     "Run Xray with config, the default command",
	Long: `
Run Xray with config, the default command.

The -config=file, -c=file flags set the config files for
Xray. Multiple assign is accepted. Keys (i.e. "vless://abcdefh123456#name")
are mapped into a default config.

The -confdir=dir flag sets a dir with multiple json config

The -format=json flag sets the format of config files.
Default "auto".

The -test flag tells Xray to test config files only,
without launching the server.

The -dump flag tells Xray to print the merged config.
	`,
}

func init() {
	cmdRun.Run = executeRun // break init loop
	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds)
}

var (
	configFiles cmdarg.Arg
	configDir   string
	dump        = cmdRun.Flag.Bool("dump", false, "Dump merged config only, without launching Xray server.")
	test        = cmdRun.Flag.Bool("test", false, "Test config file only, without launching Xray server.")
	format      = cmdRun.Flag.String("format", "auto", "Format of input file.")

	/* We have to do this here because Golang's Test will also need to parse flag, before
	 * main func in this file is run.
	 */
	_ = func() bool {
		cmdRun.Flag.Var(&configFiles, "config", "Config path for Xray.")
		cmdRun.Flag.Var(&configFiles, "c", "Short alias of -config")
		cmdRun.Flag.StringVar(&configDir, "confdir", "", "A dir with multiple json config")
		return true
	}()
)

func executeRun(_ *base.Command, _ []string) {
	if *dump {
		clog.ReplaceWithSeverityLogger(clog.Severity_Warning)
		os.Exit(dumpConfig())
	}
	if *test {
		printVersion()
		if _, err := startXray(); err != nil {
			fmt.Println("Failed to parse config:", err)
			os.Exit(23)
		}
		fmt.Println("Configuration OK.")
		os.Exit(0)
	}

	xui.Start(&xui.Deps{
		ConfigFiles:  &configFiles,
		DefaultKey:   extra.Keys,
		Port:         &extra.PortInt,
		AutoEnable:   extra.Enabled == "true",
		AutoSysProxy: extra.SysProxy == "true",
		AutoStartup:  extra.OnStartup == "true",
		StartXray: func() (io.Closer, error) {
			srv, err := startXray()
			if err != nil {
				return nil, err
			}
			if err := srv.Start(); err != nil {
				return nil, err
			}
			return srv, nil
		},
		ValidateKey:  func(key string) error { _, err := extra.Parse(key); return err },
		ParseName:    parseName,
		PrintVersion: printVersion,
	})
}

func dumpConfig() int {
	files := getConfigFilePath(false)
	if config, err := core.GetMergedConfig(files); err != nil {
		fmt.Println(err)
		time.Sleep(1 * time.Second)
		return 23
	} else {
		fmt.Print(config)
	}
	return 0
}

func fileExists(file string) bool {
	info, err := os.Stat(file)
	return err == nil && !info.IsDir()
}

func dirExists(file string) bool {
	if file == "" {
		return false
	}
	info, err := os.Stat(file)
	return err == nil && info.IsDir()
}

func getRegepxByFormat() string {
	switch strings.ToLower(*format) {
	case "json":
		return `^.+\.(json|jsonc)$`
	case "toml":
		return `^.+\.toml$`
	case "yaml", "yml":
		return `^.+\.(yaml|yml)$`
	default:
		return `^.+\.(json|jsonc|toml|yaml|yml)$`
	}
}

func readConfDir(dirPath string) {
	confs, err := os.ReadDir(dirPath)
	if err != nil {
		log.Fatalln(err)
	}
	for _, f := range confs {
		matched, err := regexp.MatchString(getRegepxByFormat(), f.Name())
		if err != nil {
			log.Fatalln(err)
		}
		if matched {
			configFiles.Set(path.Join(dirPath, f.Name()))
		}
	}
}

func getConfigFilePath(verbose bool) cmdarg.Arg {
	if dirExists(configDir) {
		if verbose {
			log.Println("Using confdir from arg:", configDir)
		}
		readConfDir(configDir)
	} else if envConfDir := platform.GetConfDirPath(); dirExists(envConfDir) {
		if verbose {
			log.Println("Using confdir from env:", envConfDir)
		}
		readConfDir(envConfDir)
	}

	if len(configFiles) > 0 {
		return configFiles
	}

	if workingDir, err := os.Getwd(); err == nil {
		suffixes := []string{".json", ".jsonc", ".toml", ".yaml", ".yml"}
		for _, suffix := range suffixes {
			configFile := filepath.Join(workingDir, "config"+suffix)
			if fileExists(configFile) {
				if verbose {
					log.Println("Using default config: ", configFile)
				}
				return cmdarg.Arg{configFile}
			}
		}
	}

	if configFile := platform.GetConfigurationPath(); fileExists(configFile) {
		if verbose {
			log.Println("Using config from env: ", configFile)
		}
		return cmdarg.Arg{configFile}
	}

	if verbose {
		log.Println("Using config from STDIN")
	}
	if extra.Keys != "" {
		slog.Info("Using default config", "file", extra.Keys)
		return cmdarg.Arg{extra.Keys}
	}
	return cmdarg.Arg{"stdin:"}
}

func getConfigFormat() string {
	f := core.GetFormatByExtension(*format)
	if f == "" {
		f = "auto"
	}
	return f
}

func startXray() (core.Server, error) {
	configFiles := getConfigFilePath(true)

	c, err := core.LoadConfig(getConfigFormat(), interpretKeysAsConfigFiles(configFiles))
	if err != nil {
		return nil, errors.New("failed to load config files: [", configFiles.String(), "]").Base(err)
	}

	server, err := core.New(c)
	if err != nil {
		return nil, errors.New("failed to create server").Base(err)
	}

	return server, nil
}

func interpretKeysAsConfigFiles(args cmdarg.Arg) cmdarg.Arg {
	args = slices.Clone(args)
	for i, arg := range args {
		if strings.HasPrefix(arg, "vless://") {
			parsed, err := extra.Parse(arg)
			if err != nil {
				slog.Error("Failed to parse key", "key", arg, "error", err)
				continue
			}
			data, err := json.MarshalIndent(parsed, "", "  ")
			if err != nil {
				slog.Error("Failed to marshal key", "key", arg, "error", err)
				continue
			}
			args[i] = string(data)
		}
	}
	return args
}

func parseName(deeplink string) string {
	if idx := strings.LastIndex(deeplink, "#"); idx != -1 {
		if name, err := url.PathUnescape(deeplink[idx+1:]); err == nil && name != "" {
			return name
		}
	}
	return ""
}

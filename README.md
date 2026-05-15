## xray fork with a proper keys syntax support

## Run

*Requires Golang 1.26+, you could install it from here https://go.dev/doc/install*

```bash
go run -ldflags='-X main.defaultPort=10808' github.com/0x1488/Xray-core/main -c 'vless://abc123#name' 
```

## Build Static Binary

```bash
go build -ldflags='-w -s -X main.defaultPort=10808 -X main.defaultConfigFiles=vless://abc123#name' -o xray ./main
```

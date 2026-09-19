#!/bin/sh

set -xue
 
CGO_ENABLED=1 \
  GOOS=windows \
  GOARCH=amd64 \
  CC="zig cc -target x86_64-windows-gnu" \
  CXX="zig c++ -target x86_64-windows-gnu" \
  go build -tags systray -ldflags '-H windowsgui -s -w -X github.com/0xCLWN/xray-core/extra.Keys=vless://bbb1e12c-a84b-4017-b54c-d77ec8f902ec@circumflexx.isgood.host:443?type=tcp&security=reality&pbk=NDna9Zqb6eyRM3GVAVrLoi42q9vUL2bDRMToyWY74VQ&fp=chrome&sni=singlehunter2.isgood.host&sid=bc45&spx=%2F&flow=xtls-rprx-vision#shvedi-meh' -o xray-tray.exe ./main

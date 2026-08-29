#!/bin/sh

set -xue
 
CGO_ENABLED=1 \
  GOOS=windows \
  GOARCH=amd64 \
  CC="zig cc -target x86_64-windows-gnu" \
  CXX="zig c++ -target x86_64-windows-gnu" \
  go build -tags systray -ldflags '-H windowsgui -s -w -X github.com/0xCLWN/xray-core/extra.Keys=vless://b5a3d6a6-0ad8-49d9-915f-018c436e10f8@circumflexx.isgood.host:443?security=reality&encryption=none&pbk=NDna9Zqb6eyRM3GVAVrLoi42q9vUL2bDRMToyWY74VQ&headerType=&fp=firefox&spx=%2FBYlC9BOduhkbRGA&type=tcp&flow=xtls-rprx-vision&sni=singlehunter2.isgood.host&sid=d190e2#shvedi-allochka' -o xray-allochka-firefox.exe ./main

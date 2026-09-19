package main

import (
	"encoding/json"
	"fmt"
	"github.com/0xCLWN/xray-core/extra"
	"log"
)

func main() {
	const key = "vless://bfa80d2c-f47a-4ed5-80c2-2b79de211ad4@zigger.isgood.host:443?type=tcp&security=reality&pbk=PGrn5_AbZHEdq1gErQfjZWsV9SsjvTa60LJhFxfSywk&fp=firefox&sni=pupa.goose-referee.com&sid=3409&spx=%2F&flow=xtls-rprx-vision#zigga-maria"
	cfg, err := extra.Parse(key)
	if err != nil {
		log.Fatal(err)
	}
	data, _ := json.MarshalIndent(cfg, "", "  ")
	fmt.Println(string(data))
}

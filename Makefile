XRAY_DIR ?= xray-core
RESOURCES := composeApp/src/desktopMain/resources/xray

.PHONY: xray-mac xray-mac-intel xray-windows xray-linux run package clean

# build xray binaries and place them in resources
xray-mac:
	cd $(XRAY_DIR) && \
	CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 \
	go build -trimpath -ldflags="-s -w" -o $(CURDIR)/$(RESOURCES)/xray-darwin-arm64 ./main
	@echo "Built xray-darwin-arm64"

xray-windows:
	cd $(XRAY_DIR) && \
	CGO_ENABLED=0 GOOS=windows GOARCH=amd64 \
	go build -trimpath -ldflags="-s -w" -o $(CURDIR)/$(RESOURCES)/xray-windows-amd64.exe ./main
	@echo "Built xray-windows-amd64.exe"

xray-linux:
	cd $(XRAY_DIR) && \
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
	go build -trimpath -ldflags="-s -w" -o $(CURDIR)/$(RESOURCES)/xray-linux-amd64 ./main
	@echo "Built xray-linux-amd64"

xray-all: xray-mac xray-windows xray-linux

# run the app in dev mode (needs at least one xray binary built)
run: xray-mac
	./gradlew :composeApp:run

# run with baked keys for testing: make run-baked KEYS="vless://...#Name"
run-baked: xray-mac
	./gradlew :composeApp:run -PbakedKeys="$(KEYS)"

# package distributable (no baked keys)
package-mac: xray-mac
	./gradlew :composeApp:packageDmg

package-windows: xray-windows
	./gradlew :composeApp:packageMsi

# package with baked keys: make package-mac-baked KEYS="vless://...#Name,vless://...#Name2"
package-mac-baked: xray-mac
	./gradlew :composeApp:packageDmg -PbakedKeys="$(KEYS)"

package-windows-baked: xray-windows
	./gradlew :composeApp:packageMsi -PbakedKeys="$(KEYS)"

clean:
	./gradlew clean
	rm -f $(RESOURCES)/xray-*

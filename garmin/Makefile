# FMD Remote — Garmin Connect IQ
# Linux-only Makefile (headless; no simulator needed to compile)

SDK_VERSION ?= 8.2.1
SDK_FILE    ?= connectiq-sdk-lin-8.2.1-2025-06-19-f69b94140.zip
DEVICE      ?= fenix7
SDK_DIR     := sdk

.PHONY: sdk devices build clean

$(SDK_DIR)/sdk.zip:
	mkdir -p $(SDK_DIR)
	curl -sL -o $@ https://developer.garmin.com/downloads/connect-iq/sdks/$(SDK_FILE)

sdk: $(SDK_DIR)/sdk.zip
	cd $(SDK_DIR) && unzip -q -o sdk.zip

# One-time, requires a (free) Garmin login:
#   https://www.garmin.com/en-US/account/  then:
#   curl -s https://raw.githubusercontent.com/lindell/connect-iq-sdk-manager-cli/master/install.sh | sh
#   connect-iq-sdk-manager-cli login
#   connect-iq-sdk-manager-cli agreement accept
# Device files land in ~/.Garmin/ConnectIQ/Devices where monkeyc finds them.
devices:
	connect-iq-sdk-manager-cli device download --manifest manifest.xml

build: sdk
	mkdir -p build
	$(SDK_DIR)/bin/monkeyc -f monkey.jungle -o build/fmd-remote-$(DEVICE).prg -d $(DEVICE) -w

clean:
	rm -rf build

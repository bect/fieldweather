# Makefile to simplify Android builds

.PHONY: help debug prod install-debug install-prod clean build-hub-local build-hub-orangepi

help:
	@echo "Available commands:"
	@echo "  make debug             - Build the debug APK"
	@echo "  make prod              - Build the production (release) APK"
	@echo "  make install-debug     - Build and install the debug APK via adb"
	@echo "  make install-prod      - Build and install the production APK via adb"
	@echo "  make clean             - Clean the build directories"
	@echo "  make build-hub-local   - Build the Go Server for this machine"
	@echo "  make build-hub-orangepi- Build the Go Server for Orange Pi Zero (ARM32)"

debug:
	./gradlew assembleDebug

prod:
	./gradlew assembleRelease

install-debug: debug
	adb install -r app/build/outputs/apk/debug/app-debug.apk

install-prod: prod
	adb install -r app/build/outputs/apk/release/app-release.apk

clean:
	./gradlew clean
	cd weather-recorder-hub-c && make clean
	rm -f weather-recorder-hub-c/hub-local weather-recorder-hub-c/hub-orangepi

build-hub-local:
	@echo "Building C Hub for local machine..."
	cd weather-recorder-hub-c && make CC=gcc TARGET=hub-local

build-hub-orangepi:
	@echo "Building C Hub for Orange Pi Zero LTS (ARM32)..."
	@echo "Note: Ensure 'gcc-arm-linux-gnueabihf' is installed for C cross-compilation."
	cd weather-recorder-hub-c && make CC=arm-linux-gnueabihf-gcc TARGET=hub-orangepi

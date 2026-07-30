# Makefile to simplify Android builds

.PHONY: help debug prod install-debug install-prod clean

help:
	@echo "Available commands:"
	@echo "  make debug         - Build the debug APK"
	@echo "  make prod          - Build the production (release) APK"
	@echo "  make install-debug - Build and install the debug APK via adb"
	@echo "  make install-prod  - Build and install the production APK via adb"
	@echo "  make clean         - Clean the build directories"

debug:
	./gradlew assembleDebug

prod:
	./gradlew assembleRelease

install-debug: debug
	adb install -r app/build/outputs/apk/debug/app-debug.apk

install-prod: prod
	adb install -r app/build/outputs/apk/release/app-release-unsigned.apk

clean:
	./gradlew clean

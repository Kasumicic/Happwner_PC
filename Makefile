SHELL := /usr/bin/env bash

APP_NAME := Happwner PC
APP_VERSION := $(shell sed -n 's/^appVersion=//p' gradle.properties)
MACHINE_ARCH := $(shell uname -m)
GRADLE := ./gradlew
APP_DIR := desktop/build/compose/binaries/main/app
APP_BUNDLE := $(APP_DIR)/$(APP_NAME)
DIST_DIR := dist
ARCH_WORK := build/packaging/arch
LINUX_ARCHIVE := $(DIST_DIR)/happwner-pc-$(APP_VERSION)-linux-$(MACHINE_ARCH).tar.gz
ARCH_SOURCE := $(ARCH_WORK)/happwner-pc-$(APP_VERSION)-linux-$(MACHINE_ARCH).tar.gz

.DEFAULT_GOAL := help

.PHONY: help run test app linux deb arch clean artifacts check-linux check-version

help:
	@echo "Happwner PC build targets"
	@echo
	@echo "  make run       Run the application from sources"
	@echo "  make test      Run all automated tests"
	@echo "  make app       Build a portable application directory"
	@echo "  make linux     Build a portable Linux tar.gz archive"
	@echo "  make deb       Build a DEB package for Debian/Ubuntu"
	@echo "  make arch      Build an Arch Linux pkg.tar.zst package"
	@echo "  make artifacts Show generated package paths"
	@echo "  make clean     Remove Gradle build output and dist/"

check-linux:
	@test "$$(uname -s)" = "Linux" || { echo "This target must run on Linux."; exit 1; }

check-version:
	@test -n "$(APP_VERSION)" || { echo "appVersion is missing in gradle.properties."; exit 1; }

run:
	$(GRADLE) :desktop:run

test:
	$(GRADLE) test

app: check-linux check-version
	$(GRADLE) :desktop:createDistributable
	@test -x "$(APP_BUNDLE)/bin/$(APP_NAME)"
	@echo "Portable application: $(APP_BUNDLE)"

linux: app
	@mkdir -p "$(DIST_DIR)"
	tar -C "$(APP_DIR)" -czf "$(LINUX_ARCHIVE)" "$(APP_NAME)"
	@echo "Linux archive: $(LINUX_ARCHIVE)"

deb: check-linux check-version
	@command -v dpkg-deb >/dev/null || { echo "Install dpkg (Arch: sudo pacman -S dpkg)."; exit 1; }
	$(GRADLE) :desktop:packageDeb
	@mkdir -p "$(DIST_DIR)"
	@find desktop/build/compose/binaries/main/deb -maxdepth 1 -type f -name '*.deb' -exec cp -f {} "$(DIST_DIR)/" \;
	@echo "DEB packages:"
	@find "$(DIST_DIR)" -maxdepth 1 -type f -name '*.deb' -print

arch: app
	@test "$(MACHINE_ARCH)" = "x86_64" || { echo "The Arch PKGBUILD currently supports x86_64 only."; exit 1; }
	@command -v makepkg >/dev/null || { echo "Install Arch build tools: sudo pacman -S --needed base-devel."; exit 1; }
	@mkdir -p "$(ARCH_WORK)" "$(DIST_DIR)"
	tar -C "$(APP_DIR)" -czf "$(ARCH_SOURCE)" "$(APP_NAME)"
	cp packaging/arch/PKGBUILD packaging/arch/happwner-pc.desktop packaging/arch/TERMS packaging/arch/icons/*.png "$(ARCH_WORK)/"
	cd "$(ARCH_WORK)" && HAPPWNER_VERSION="$(APP_VERSION)" makepkg --force --cleanbuild --clean --noconfirm
	cp -f "$(ARCH_WORK)"/*.pkg.tar.zst "$(DIST_DIR)/"
	@echo "Arch packages:"
	@find "$(DIST_DIR)" -maxdepth 1 -type f -name '*.pkg.tar.zst' -print

artifacts:
	@find "$(DIST_DIR)" -maxdepth 1 -type f \( -name '*.tar.gz' -o -name '*.deb' -o -name '*.pkg.tar.zst' \) -print 2>/dev/null || true

clean:
	$(GRADLE) clean
	rm -rf "$(DIST_DIR)" "$(ARCH_WORK)"

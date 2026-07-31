SHELL := /usr/bin/env bash

APP_NAME := Happwner PC
APP_VERSION := $(shell sed -n 's/^appVersion=//p' gradle.properties)
MACHINE_ARCH := $(shell uname -m)
GRADLE := ./gradlew
APP_DIR := desktop/build/compose/binaries/main/app
APP_BUNDLE := $(APP_DIR)/$(APP_NAME)
DIST_DIR := dist
RELEASE_DIR := release
ARCH_WORK := build/packaging/arch
ARCH_PKGREL := $(shell sed -n 's/^pkgrel=//p' packaging/arch/PKGBUILD)
LINUX_ARCHIVE := $(DIST_DIR)/happwner-pc-$(APP_VERSION)-linux-$(MACHINE_ARCH).tar.gz
ARCH_SOURCE := $(ARCH_WORK)/happwner-pc-$(APP_VERSION)-linux-$(MACHINE_ARCH).tar.gz
ARCH_PACKAGE := $(DIST_DIR)/happwner-pc-bin-$(APP_VERSION)-$(ARCH_PKGREL)-$(MACHINE_ARCH).pkg.tar.zst
DEB_OUTPUT := desktop/build/compose/binaries/main/deb
RPM_OUTPUT := desktop/build/compose/binaries/main/rpm

.DEFAULT_GOAL := help

.PHONY: help run test app linux deb rpm arch release release-check-tools clean artifacts check-linux check-version

help:
	@printf '\n\033[1;36mHappwner PC %s\033[0m — build targets\n\n' "$(APP_VERSION)"
	@echo
	@printf '  \033[1mmake run\033[0m       Run the application from sources\n'
	@printf '  \033[1mmake test\033[0m      Run all automated tests\n'
	@printf '  \033[1mmake app\033[0m       Build a portable application directory\n'
	@printf '  \033[1mmake linux\033[0m     Build a portable Linux tar.gz archive\n'
	@printf '  \033[1mmake deb\033[0m       Build a DEB package for Debian/Ubuntu\n'
	@printf '  \033[1mmake rpm\033[0m       Build an RPM package for Fedora/openSUSE\n'
	@printf '  \033[1mmake arch\033[0m      Build an Arch Linux pkg.tar.zst package\n'
	@printf '  \033[1;32mmake release\033[0m   Test and build every Linux release artifact\n'
	@printf '  \033[1mmake artifacts\033[0m Show generated package paths\n'
	@printf '  \033[1mmake clean\033[0m     Remove Gradle build output, dist/, and release/\n\n'

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

rpm: check-linux check-version
	@command -v rpmbuild >/dev/null || { echo "Install RPM tools (Arch: sudo pacman -S rpm-tools)."; exit 1; }
	$(GRADLE) :desktop:packageRpm
	@mkdir -p "$(DIST_DIR)"
	@find "$(RPM_OUTPUT)" -maxdepth 1 -type f -name '*.rpm' -exec cp -f {} "$(DIST_DIR)/" \;
	@echo "RPM packages:"
	@find "$(DIST_DIR)" -maxdepth 1 -type f -name '*.rpm' -print

arch: app
	@test "$(MACHINE_ARCH)" = "x86_64" || { echo "The Arch PKGBUILD currently supports x86_64 only."; exit 1; }
	@command -v makepkg >/dev/null || { echo "Install Arch build tools: sudo pacman -S --needed base-devel."; exit 1; }
	@mkdir -p "$(ARCH_WORK)" "$(DIST_DIR)"
	tar -C "$(APP_DIR)" -czf "$(ARCH_SOURCE)" "$(APP_NAME)"
	cp packaging/arch/PKGBUILD packaging/arch/happwner-pc.desktop packaging/arch/TERMS packaging/arch/icons/*.png "$(ARCH_WORK)/"
	cd "$(ARCH_WORK)" && HAPPWNER_VERSION="$(APP_VERSION)" makepkg --force --cleanbuild --clean --noconfirm
	cp -f "$(ARCH_WORK)"/*.pkg.tar.zst "$(DIST_DIR)/"
	@test -f "$(ARCH_PACKAGE)"
	@echo "Arch package: $(ARCH_PACKAGE)"

release-check-tools: check-linux check-version
	@missing=0; \
	for tool in dpkg-deb rpmbuild makepkg sha256sum; do \
		if ! command -v "$$tool" >/dev/null; then \
			printf 'Missing release tool: %s\n' "$$tool"; \
			missing=1; \
		fi; \
	done; \
	if test "$$missing" -ne 0; then \
		printf '\nArch Linux: sudo pacman -S --needed base-devel dpkg rpm-tools\n'; \
		exit 1; \
	fi

release: release-check-tools
	@printf '\n\033[1;36m==> Happwner PC %s release\033[0m\n\n' "$(APP_VERSION)"
	@rm -rf "$(RELEASE_DIR)"
	@mkdir -p "$(RELEASE_DIR)"
	$(MAKE) test
	$(MAKE) linux
	$(MAKE) deb
	$(MAKE) rpm
	$(MAKE) arch
	@cp "$(LINUX_ARCHIVE)" "$(ARCH_PACKAGE)" "$(RELEASE_DIR)/"
	@find "$(DEB_OUTPUT)" -maxdepth 1 -type f -name "*$(APP_VERSION)*.deb" -exec cp -f {} "$(RELEASE_DIR)/" \;
	@find "$(RPM_OUTPUT)" -maxdepth 1 -type f -name "*$(APP_VERSION)*.rpm" -exec cp -f {} "$(RELEASE_DIR)/" \;
	@sed 's/(RELEASE_$(APP_VERSION)_RU.md)/(RELEASE_NOTES_RU.md)/' docs/RELEASE_$(APP_VERSION).md > "$(RELEASE_DIR)/RELEASE_NOTES.md"
	@sed 's/(RELEASE_$(APP_VERSION).md)/(RELEASE_NOTES.md)/' docs/RELEASE_$(APP_VERSION)_RU.md > "$(RELEASE_DIR)/RELEASE_NOTES_RU.md"
	@cd "$(RELEASE_DIR)" && sha256sum *.tar.gz *.deb *.rpm *.pkg.tar.zst > SHA256SUMS
	@printf '\n\033[1;32mRelease is ready: %s/\033[0m\n\n' "$(RELEASE_DIR)"
	@find "$(RELEASE_DIR)" -maxdepth 1 -type f -printf '  %-64f %10s bytes\n' | sort
	@printf '\nAttach the package files, RELEASE_NOTES.md, RELEASE_NOTES_RU.md, and SHA256SUMS to the GitHub release.\n\n'

artifacts:
	@find "$(DIST_DIR)" "$(RELEASE_DIR)" -maxdepth 1 -type f \( -name '*.tar.gz' -o -name '*.deb' -o -name '*.rpm' -o -name '*.pkg.tar.zst' -o -name 'SHA256SUMS' -o -name 'RELEASE_NOTES*.md' \) -print 2>/dev/null | sort || true

clean:
	$(GRADLE) clean
	rm -rf "$(DIST_DIR)" "$(ARCH_WORK)" "$(RELEASE_DIR)"

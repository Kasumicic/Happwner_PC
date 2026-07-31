<p align="center">
  <img src="desktop/src/main/resources/happwner-pc.png" width="168" alt="Happwner PC logo">
</p>

<h1 align="center">Happwner PC</h1>

<p align="center">
  A local subscription server for your computer and trusted home network
</p>

<p align="center">
  <img alt="Version 0.1.5 Preview" src="https://img.shields.io/badge/version-0.1.5%20Preview-35d0eb?style=flat-square">
  <img alt="Linux" src="https://img.shields.io/badge/Linux-supported-35d0eb?style=flat-square&logo=linux&logoColor=white">
  <img alt="Windows planned" src="https://img.shields.io/badge/Windows-planned-55515d?style=flat-square&logo=windows">
  <img alt="JDK 21" src="https://img.shields.io/badge/JDK-21-55515d?style=flat-square&logo=openjdk">
  <a href="https://github.com/Kasumicic/Happwner_PC/actions/workflows/ci.yml"><img alt="CI status" src="https://github.com/Kasumicic/Happwner_PC/actions/workflows/ci.yml/badge.svg"></a>
</p>

<p align="center">
  <a href="README_RU.md">Русский</a> · <b>English</b> ·
  <a href="docs/RELEASE_0.1.5.md">Release notes</a> ·
  <a href="docs/CORE_AUDIT.md">Core audit</a> ·
  <a href="docs/CLIENT_COMPATIBILITY.md">Client compatibility</a>
</p>

> [!IMPORTANT]
> **0.1.5 Preview** is the first Linux preview. Core functionality is implemented and covered by automated tests, while compatibility testing across VPN clients and desktop environments is still in progress.

Happwner PC fetches a provider subscription, decrypts and transforms it, then exposes it through a stable local URL. Use that URL in a VPN client on the same computer or share it with phones, TVs, and other devices on a trusted home network.

This is a non-commercial desktop fork of the Android application [Happwner](https://github.com/Omegaplexx/Happwner). Android, Xposed, and LSPatch features have been removed; the portable cryptography, conversion logic, and subscription Bridge remain.

## Screenshots

### Subscriptions

<p align="center">
  <img src="docs/screenshots/subscriptions.png" alt="Happwner PC subscriptions tab" width="900">
</p>

### Settings

<p align="center">
  <img src="docs/screenshots/settings.png" alt="Happwner PC settings tab" width="900">
</p>

## Highlights

- **Local or household-wide.** Safe `127.0.0.1` mode is the default; LAN access is explicitly enabled.
- **Stable URLs.** Addresses such as `http://127.0.0.1:8166/sub/<UUID>` survive upstream subscription changes.
- **Happ and third-party formats.** HTTP(S), `happ://add`, `happ://crypt`–`crypt5`, `v2raytun://crypt`, `v2raytun://import`, `incy://add`, and `incy://import` are supported.
- **Provider compatibility.** Requests carry `x-hwid` and a configurable `User-Agent`; responses with `Encrypt-Tag` are decrypted.
- **Profile conversion.** Optional Base64, JSON-to-URI, and Xray-to-sing-box transformations are available; Base64 decoding is enabled by default.
- **Subscription inspection.** Checks report HTTP status, response size, discovered profiles and protocols, or a clear processing error.
- **Direct profile copying.** Fetch, decrypt, transform, and copy the complete processed subscription without importing its URL.
- **Safe diagnostics.** An in-memory activity log shows clients, timings, response details, and transformations; copied reports mask addresses and omit subscription secrets.
- **Desktop-friendly UI.** Dark theme, tabs, LAN QR codes, HWID generation, system tray, login startup, and working native clipboard shortcuts.
- **Network diagnostics.** Select a LAN interface and identify the process/PID occupying the desired port.

## Quick start

JDK 21 is required to build the project. Packaged applications include a Java runtime.

### Arch Linux

```bash
sudo pacman -S --needed jdk21-openjdk base-devel
make arch
sudo pacman -U dist/happwner-pc-bin-0.1.5-3-x86_64.pkg.tar.zst
```

Happwner PC will appear in the application menu and can also be launched with:

```bash
happwner-pc
```

### Other Linux distributions

Build a portable archive:

```bash
make linux
tar -xzf dist/happwner-pc-0.1.5-linux-$(uname -m).tar.gz
./Happwner\ PC/bin/Happwner\ PC
```

For Debian/Ubuntu:

```bash
make deb
sudo apt install ./dist/happwner-pc_0.1.5_amd64.deb
```

The exact DEB filename may differ slightly; `make artifacts` lists generated files. Run `make help` for all available targets.

For Fedora/openSUSE:

```bash
make rpm
```

To test the project and build the portable archive, DEB, RPM, and Arch package in one command:

```bash
sudo pacman -S --needed base-devel dpkg rpm-tools
make release
```

Release-ready packages, release notes, and `SHA256SUMS` are written to the Git-ignored `release/` directory.

### Run from source

```bash
make run
make test
```

## Usage

1. Select **Add subscription** and enter the upstream URL.
2. Enter an HWID and User-Agent if required, or generate a new HWID.
3. Select **Check** to confirm that the response contains profiles.
4. Copy the local URL into NekoBox, Hiddify, v2rayNG, or another compatible client, or select **Copy profiles** to place the complete processed response on the clipboard.
5. To serve other devices, enable **Home network (LAN)** and import the URL using its QR code.

The default listener is `127.0.0.1:8166`. If another device cannot open `/health`, allow inbound TCP traffic to the selected port in the operating-system firewall.

Manual Android testing confirms that LAN URLs work in Exclave, Husi, Happ, and NekoBox. Incy rejects the URL because its own network policy blocks cleartext HTTP. See the [client compatibility table](docs/CLIENT_COMPATIBILITY.md) for the recorded conditions and explanation.

## Security

LAN mode has no authentication. Anyone on the local network who obtains a `/sub/<UUID>` URL can read that subscription. Enable it only on a trusted home network and **never expose the port to the Internet**.

Legacy links containing arbitrary upstream URLs are restricted to loopback clients, preventing LAN devices from using the application as an unrestricted HTTP proxy.

Configuration is stored under `%APPDATA%/HappwnerPC` on Windows and `${XDG_CONFIG_HOME:-~/.config}/happwner-pc` on Linux.

Copied profiles contain connection credentials. Treat the clipboard contents as secret and clear clipboard history after pasting them into a trusted client.

## Preview status

The cryptographic algorithms, link formats, and HTTP processing pipeline have been compared with the Android original and covered by automated tests. Deliberate desktop differences are documented in the [core compatibility audit](docs/CORE_AUDIT.md).

Before a stable release, the project still needs:

- continued checks across client versions, including Hiddify, v2rayNG, and Karing;
- tray and startup smoke tests on Windows, GNOME, and other Linux desktop environments;
- CI and Windows installer builds;
- firewall instructions for common operating systems.

See [TODO_EN.md](TODO_EN.md) for the complete roadmap.

## Development

```bash
./gradlew test
./gradlew :desktop:createDistributable
```

GitHub Actions runs the test suite on Linux and Windows for every push to `main` and every pull request. The **Release builds** workflow builds portable Linux and Windows archives, DEB, RPM, Arch Linux, and MSI packages. Tag pushes matching `v*` publish automatically; a manual run only stores workflow artifacts unless **Upload artifacts to the GitHub Release** is enabled and the target tag is specified.

The project has two modules:

- `core` — link formats, cryptography, and converters;
- `desktop` — Compose Desktop UI, HTTP server, persistence, tray, and startup integration.

## Credits and terms

The cryptography and conversion core is based on work by **Omegaplex** and **slavrom21** in the original Happwner project.

Non-commercial use, copying, modification, and distribution are permitted with attribution and a link to the [original project](https://github.com/Omegaplexx/Happwner). Commercial use requires permission from the original author.

The software is intended for personal use with subscriptions you are authorized to access and for sharing within a trusted household. The authors accept no responsibility for user actions.

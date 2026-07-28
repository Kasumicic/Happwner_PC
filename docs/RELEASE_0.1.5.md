# Happwner PC 0.1.5 Preview

[Русская версия](RELEASE_0.1.5_RU.md)

This is the first preview release of Happwner PC: a desktop subscription Bridge for Linux that serves decrypted and transformed subscriptions locally or across a trusted home network.

## What is ready

- stable local subscription URLs;
- optional household LAN access with interface selection and QR codes;
- Happ, v2RayTun, INCY, HTTP, and HTTPS source handling;
- Happ response decryption and Base64/JSON/Xray transformations;
- subscription validation with profile and protocol inspection;
- dark desktop interface, system tray, login startup, and persistent settings;
- portable Linux archive, Arch Linux, DEB, and RPM packages.

## Installation

Choose the package for your distribution:

| Distribution | File |
| --- | --- |
| Arch Linux | `happwner-pc-bin-0.1.5-3-x86_64.pkg.tar.zst` |
| Debian/Ubuntu | `happwner-pc_0.1.5_amd64.deb` |
| Fedora/openSUSE | `happwner-pc-0.1.5-1.x86_64.rpm` |
| Other x86_64 Linux | `happwner-pc-0.1.5-linux-x86_64.tar.gz` |

### Arch Linux

```bash
sudo pacman -U happwner-pc-bin-0.1.5-3-x86_64.pkg.tar.zst
```

### Debian/Ubuntu

```bash
sudo apt install ./happwner-pc_0.1.5_amd64.deb
```

### Fedora

```bash
sudo dnf install ./happwner-pc-0.1.5-1.x86_64.rpm
```

### openSUSE

```bash
sudo zypper install ./happwner-pc-0.1.5-1.x86_64.rpm
```

### Portable archive

```bash
tar -xzf happwner-pc-0.1.5-linux-x86_64.tar.gz
./Happwner\ PC/bin/Happwner\ PC
```

All packages include a Java runtime. Verify downloads with the accompanying `SHA256SUMS` file:

```bash
sha256sum -c SHA256SUMS
```

## Before reporting a problem

Please include:

- Linux distribution and desktop environment;
- VPN client name and version;
- whether the server is in local or LAN mode;
- the visible error message or HTTP status;
- steps that reproduce the problem.

Do not publish source subscription URLs, HWIDs, decrypted profiles, or other credentials.

## Preview limitations

This release has automated coverage for the subscription core and HTTP server, but it has not yet been smoke-tested with every target VPN client or desktop environment. Windows packages are planned for a later preview.

For implementation details and differences from Android Happwner 1.3, read the core compatibility audit in the repository documentation.

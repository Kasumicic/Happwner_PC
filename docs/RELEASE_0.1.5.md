# Happwner PC 0.1.5 Preview

This is the first preview release of Happwner PC: a desktop subscription Bridge for Linux that serves decrypted and transformed subscriptions locally or across a trusted home network.

## What is ready

- stable local subscription URLs;
- optional household LAN access with interface selection and QR codes;
- Happ, v2RayTun, INCY, HTTP, and HTTPS source handling;
- Happ response decryption and Base64/JSON/Xray transformations;
- subscription validation with profile and protocol inspection;
- dark desktop interface, system tray, login startup, and persistent settings;
- portable Linux archive, Arch Linux package, and DEB build targets.

## Installation

### Arch Linux package

```bash
sudo pacman -U happwner-pc-bin-0.1.5-3-x86_64.pkg.tar.zst
```

### Portable Linux archive

```bash
tar -xzf happwner-pc-0.1.5-linux-x86_64.tar.gz
./Happwner\ PC/bin/Happwner\ PC
```

The packages include a Java runtime.

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

For implementation details and differences from Android Happwner 1.3, read the [core compatibility audit](CORE_AUDIT.md).

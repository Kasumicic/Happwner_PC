<p align="center">
  <b>English</b> |
  <a href="README_RU.md">Русский</a>
</p>

# Happwner PC

A desktop subscription server for Windows and Linux. It fetches Happ subscriptions, decrypts them, and serves them to VPN clients on the same computer or across a trusted home network.

This is a non-commercial fork of the Android application [Happwner](https://github.com/Omegaplexx/Happwner). Android, Xposed, and LSPatch features have been removed; the portable cryptography, conversion logic, and subscription Bridge remain.

## Features

- stable URLs such as `http://127.0.0.1:8166/sub/<UUID>`;
- LAN serving through an address such as `http://192.168.x.x:8166`;
- HTTP(S), `happ://add`, `happ://crypt`–`crypt5`, `v2raytun://crypt`, `v2raytun://import`, `incy://add`, and `incy://import` inputs;
- provider requests with `x-hwid` and a configurable `User-Agent`;
- Happ response decryption using the `Encrypt-Tag` header;
- optional Base64, JSON-to-URI, and Xray-to-sing-box transformations;
- Compose Desktop management window, system tray, and login startup;
- loopback compatibility with legacy `/url=...&hwid=...&ua=...` Bridge links.

## Build and run

JDK 21 is required to build the project. Packaged applications include a runtime, so end users do not need to install Java.

```bash
./gradlew :desktop:run
./gradlew test
./gradlew :desktop:createDistributable
./gradlew :desktop:packageDistributionForCurrentOS
```

Packages are written to `desktop/build/compose/binaries`.

## Usage

1. Add a subscription with its name, source URL, HWID, and User-Agent.
2. Keep loopback mode for clients on this PC, or explicitly enable Home network (LAN).
3. Copy the stable subscription URL into NekoBox, Hiddify, v2rayNG, or another compatible client.
4. If another device cannot open `/health`, allow inbound TCP traffic to the selected port in the operating-system firewall.

The default listener is `127.0.0.1:8166`. LAN mode listens on all IPv4 interfaces and displays detected private addresses.

## Security

The selected home-network mode has no authentication. Anyone on the LAN who obtains a `/sub/<UUID>` URL can read that subscription. Enable it only on a trusted home network and never expose the port to the Internet.

Legacy links containing arbitrary upstream URLs are restricted to loopback clients, preventing LAN devices from using the PC as an unrestricted HTTP proxy.

Configuration is stored under `%APPDATA%/HappwnerPC` on Windows and `${XDG_CONFIG_HOME:-~/.config}/happwner-pc` on Linux.

## Project layout

- `core` — link formats, cryptography, and converters;
- `desktop` — Compose Desktop UI, HTTP server, persistence, tray, and startup integration.

## Credits and terms

The cryptography and conversion core is based on work by **Omegaplex** and **slavrom21** in the original Happwner project.

Non-commercial use, copying, modification, and distribution are permitted with attribution and a link to the [original project](https://github.com/Omegaplexx/Happwner). Commercial use requires permission from the original author.

The software is intended for personal use with subscriptions you are authorized to access and for sharing within a trusted household. The authors accept no responsibility for user actions.

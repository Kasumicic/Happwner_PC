# Changelog

[Русская версия](CHANGELOG_RU.md)

All notable changes to Happwner PC are documented in this file.

## Unreleased

### Added

- an in-memory diagnostics tab with request origin, client address, response status, duration, profile/protocol counts, and applied transformations;
- a sanitized copyable diagnostic report that excludes source URLs, HWIDs, subscription IDs, and profile contents;
- direct copying of the complete fetched, decrypted, and transformed subscription response;
- a manual Android client compatibility table covering Exclave, Husi, Happ, NekoBox, and Incy.

## 0.1.5 - 2026-07-26

First public Linux preview.

### Added

- local and trusted-LAN subscription server with stable `/sub/<UUID>` URLs;
- subscription management UI with dark, light, and system themes;
- provider response inspection with profile/protocol counts and error details;
- HWID generation and reuse of the most recently saved HWID;
- selectable LAN interface and QR codes for importing household URLs;
- occupied-port diagnostics with process information when available;
- Linux StatusNotifierItem tray integration, login startup, and complete-exit actions;
- Arch Linux, DEB, and portable Linux build targets;
- RPM packaging and a single `make release` workflow with SHA-256 checksums;
- generated compatibility tests for all embedded Happ, v2RayTun, and AES-GCM keys.

### Changed

- enabled Base64 decoding for new subscriptions by default;
- preserved VLESS TLS, Reality, and transport fields beyond the Android 1.3 converter;
- used the original `Happ/3.26.1` User-Agent preset as the desktop default;
- restricted legacy arbitrary-upstream Bridge URLs to loopback clients.

### Fixed

- application icon, description, and searchable metadata in the KDE application menu;
- KDE Plasma 6 tray activation and context menu behavior;
- server shutdown and port release on complete exit;
- Linux copy, paste, cut, and select-all shortcuts across keyboard layouts;
- subscription dialog overflow at the default window size;
- resize artifacts and the visible maximize-to-window transition when hiding.

### Known limitations

- Windows packaging and CI are not available yet;
- tray and startup behavior still require smoke testing outside KDE Plasma;
- compatibility with real subscriptions in all target VPN clients is not yet fully verified.

See [the 0.1.5 Preview release notes](docs/RELEASE_0.1.5.md) for the user-facing summary.

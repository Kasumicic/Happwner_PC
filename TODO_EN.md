# Happwner PC — TODO

[Русская версия](TODO.md)

## P0 — nearest usable release

- [x] Replace Compose `Tray` with a native implementation: AppIndicator/DBusMenu on Linux and a system backend on Windows.
- [x] Add a fallback **Complete exit** button to the main window.
- [x] Verify with an automated test that exiting stops the HTTP server and releases its port.
- [x] Verify StatusNotifierItem registration, DBusMenu structure, and the exit command in KDE Plasma 6.
- [x] Separate KDE tray actions: left click opens the window through `Activate()`, right click opens DBusMenu.
- [ ] Manually test the context menu on Windows, GNOME, and other AppIndicator environments.
- [x] Add subscription deletion confirmation.
- [x] Validate the source URL and port before saving.
- [x] Add a **Check subscription** action with a clear HTTP status, response size, and processing error.
- [x] Inspect checked responses: count profiles and protocols, warn about empty responses, and show a response excerpt.
- [x] Show the time and result of the latest request for every subscription.
- [x] Split subscriptions and settings into tabs, use a dark theme by default, and add theme selection.
- [x] Add HWID generation/reuse and fix the system clipboard on Linux.

## P1 — installation and home network

- [x] Add a custom icon for the application, window, tray, and installers.
- [x] Configure GitHub Actions for Linux and Windows tests and builds.
- [ ] Publish MSI and ZIP packages for Windows, plus DEB and portable archives for Linux.
- [x] Diagnose occupied ports and show the process name/PID when available.
- [ ] Add instructions for Windows Firewall, ufw, and firewalld.
- [x] Allow selecting a LAN interface on computers with multiple IPv4 addresses.
- [x] Add a QR code for LAN links to simplify phone imports.

## P2 — reliability and security

- [ ] Cache the latest successful subscription and serve it while the provider is temporarily unavailable.
- [ ] Add scheduled subscription refreshes and display expiry/traffic data from `Subscription-Userinfo`.
- [ ] Add optional token protection for LAN links while retaining the unauthenticated mode.
- [ ] Add settings export/import with backup support.
- [ ] Exclude HWIDs, source URLs, and subscription contents from diagnostic logs.
- [ ] Add IPv6 and, after client compatibility testing, a local mDNS name.

## Testing

- [x] Add generated cryptographic vectors for every embedded `happ://crypt`–`crypt5`, v2rayTun, and AES-GCM subscription key.
- [ ] Add real anonymized INCY and encrypted-link fixtures from clients/providers.
- [ ] Test NekoBox, Hiddify, v2rayNG, Husi, and Karing with local and LAN addresses.
- [ ] Test timeouts, responses larger than 32 MiB, concurrent requests, and cache recovery.
- [ ] Smoke-test startup, tray behavior, and uninstallation on Windows and major Linux desktop environments.

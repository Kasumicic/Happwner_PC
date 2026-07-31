# Happwner PC 0.1.6

[Русская версия](RELEASE_0.1.6_RU.md)

Happwner PC 0.1.6 is the first stable public desktop release, with automated Linux and Windows packages, safe diagnostics, subscription traffic information, and robust profile conversion.

## What's new

- Windows MSI and portable ZIP builds produced by GitHub Actions;
- an in-memory diagnostics tab and a sanitized report that omits subscription secrets;
- direct copying of the complete fetched, decrypted, and transformed profile list;
- `Subscription-Userinfo` traffic, quota, remaining allowance, and expiry display;
- author and project contacts in Settings;
- Android client compatibility notes, including the temporary Incy direct-mode workaround;
- multi-outbound Xray JSON conversion and corrected IPv6 profile links;
- additional tests for response size limits, parallel clients, and disabled subscriptions.

## Downloads

Choose the package for your system from the GitHub Release assets:

| System | File |
| --- | --- |
| Windows x64 installer | `Happwner PC-0.1.6.msi` |
| Windows x64 portable | `happwner-pc-0.1.6-windows-x64.zip` |
| Arch Linux x86_64 | `happwner-pc-bin-0.1.6-3-x86_64.pkg.tar.zst` |
| Debian/Ubuntu x86_64 | `happwner-pc_0.1.6_amd64.deb` |
| Fedora/openSUSE x86_64 | `happwner-pc-0.1.6-1.x86_64.rpm` |
| Other Linux x86_64 | `happwner-pc-0.1.6-linux-x86_64.tar.gz` |

Exact native package filenames can vary slightly. All packages include a Java runtime. Verify downloads with the accompanying `SHA256SUMS` file.

## Testing notes

Automated tests cover the subscription core, HTTP server, converters, encryption compatibility, response limits, and concurrent clients. Manual Android testing confirms LAN URLs in Exclave, Husi, Happ, and NekoBox. Incy blocks cleartext LAN HTTP; see the [client compatibility table](CLIENT_COMPATIBILITY.md) for the direct-mode workaround and its limitations.

Windows tray, startup, installer, and real-client behavior still need manual testing on a Windows machine. Do not expose the LAN server directly to the public Internet, and do not publish source URLs, HWIDs, copied profiles, or other credentials in bug reports.

# VPN client compatibility

[Русская версия](CLIENT_COMPATIBILITY_RU.md)

This page records manual checks of Happwner PC URLs in real VPN clients. It is not a test of every client version, device, or subscription format; results may vary with the client, Android version, and upstream data.

## Android over the home network

Test conditions on August 1, 2026:

- the phone and computer are connected to the same home network;
- the client imports a URL such as `http://192.168.1.x:8166/sub/<UUID>`;
- **Decode Base64** and **JSON to URI** are enabled in Happwner PC.

| Client | Result | Note |
|---|---|---|
| Exclave | ✅ Works | The subscription is added and its profiles are recognized. |
| Husi | ✅ Works | The subscription is added and its profiles are recognized. |
| Happ | ✅ Works | The subscription is added and its profiles are recognized. |
| NekoBox | ✅ Works | The subscription is added and its profiles are recognized. |
| Incy | ⚠️ Rejects the HTTP URL | Import reports `CLEARTEXT communication to 192.168.1.x not permitted…`. The client blocks plain HTTP through its Android network policy before contacting the server. |

## Why Incy reports CLEARTEXT

Happwner PC deliberately serves plain HTTP in home-network mode: traffic stays inside the trusted LAN, while HTTPS would require a certificate trusted by every device. An Android application can completely disable cleartext HTTP in its network configuration. Happwner PC cannot override a restriction enforced by the receiving client, and the Incy attempt will usually not appear in **Diagnostics** because the request never leaves the phone.

Available options:

- use one of the verified clients in the table;
- select **Copy profiles** and import the contents manually if the client supports it;
- wait for Incy to allow local HTTP addresses or expose a corresponding setting.

Support for `incy://add` and `incy://import` means Happwner PC can parse such an **upstream** link. It does not force the Incy application to accept the `http://…` LAN URL served by Happwner PC.

> [!CAUTION]
> Do not expose the Happwner PC port to the Internet to work around this restriction. Use a trusted VPN into the home network for remote access.

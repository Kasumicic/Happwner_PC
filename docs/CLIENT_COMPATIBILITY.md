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

## Incy direct mode

A temporary workaround is to let Incy fetch the upstream URL directly from the provider without contacting the Happwner PC LAN server. To make the provider see Incy and Happwner PC as the same device:

1. Open the Incy settings and copy its current HWID.
2. Edit the corresponding subscription in Happwner PC and paste that HWID.
3. Configure the same User-Agent in both Incy and Happwner PC.
4. Add the **original provider URL** to Incy instead of `http://192.168.1.x:8166/sub/<UUID>`.
5. Refresh the subscription in both applications and confirm that the provider returns the expected profiles.

Do not generate another HWID after binding unless necessary: some providers bind subscriptions to a device identifier or limit the number of devices.

> [!NOTE]
> In direct mode, Incy bypasses Happwner PC. **Decode Base64**, **JSON to URI**, and the other desktop transformations do not affect Incy's response. This method works only when Incy can understand the upstream link, encryption, and provider response format by itself.

Available options:

- use one of the verified clients in the table;
- use the Incy direct mode described above;
- select **Copy profiles** and import the contents manually if the client supports it;
- wait for Incy to allow local HTTP addresses or expose a corresponding setting.

Support for `incy://add` and `incy://import` means Happwner PC can parse such an **upstream** link. It does not force the Incy application to accept the `http://…` LAN URL served by Happwner PC.

> [!CAUTION]
> Upstream URLs and processed profiles contain access credentials. Do not publish them or expose the Happwner PC port to the Internet to work around this restriction. Use a trusted VPN into the home network for remote access.

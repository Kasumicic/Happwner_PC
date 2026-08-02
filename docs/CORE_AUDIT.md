# Happwner PC core compatibility audit

[Русская версия](CORE_AUDIT_RU.md)

Android port audit date: 2026-07-26

Latest Xray and sing-box documentation review: 2026-08-02

Reference: Android Happwner 1.3, local repository `../Happwner`, commit `e9412c8`.

## Ported core

| Area | Result | Verification |
| --- | --- | --- |
| Happ `crypt`–`crypt4` links | Equivalent | `HappCrypto.kt` differs only in Android/JVM compatibility imports. Automated tests encrypt and decrypt a link with every one of the four bundled RSA keys. |
| Happ `crypt5` links | Equivalent | Algorithm and all embedded keys match the Android source. Automated tests generate and decrypt a ChaCha20-Poly1305/RSA link with every one of the 36 bundled keys. |
| Encrypted subscription bodies | Equivalent | All ten AES-128-GCM keys, fixed IV, `?key=` selection and case-insensitive `Encrypt-Tag` lookup match. Covered by a generated encrypted-body test. |
| v2RayTun encrypted links | Equivalent | Source differs only in Android/JVM compatibility imports. All three bundled RSA keys are exercised by automated tests. |
| `happ://add`, v2RayTun imports and INCY links | Equivalent or extended | The original parsing code is retained. `SourceResolver` additionally unwraps nested links for a desktop subscription source and can serve an embedded static profile. |
| Base64 and JSON-to-URI conversion | Equivalent or extended | Conversion order matches Android: Base64, Xray filtering/conversion, JSON-to-URI. The PC fork additionally preserves VLESS TLS, Reality and transport fields that the Android 1.3 converter can drop. |
| Xray-to-sing-box | Extended and hardened | Current flat Xray schemas are supported after the port, and conversions without equivalent sing-box wire semantics are rejected safely. |

## Xray and sing-box conformance

The converter is additionally checked against the official [Xray transport](https://xtls.github.io/en/config/transport.html), [Xray TLS](https://xtls.github.io/en/config/transports/tls.html), [sing-box V2Ray Transport](https://sing-box.sagernet.org/configuration/shared/v2ray-transport/), and [sing-box TLS](https://sing-box.sagernet.org/configuration/shared/tls/) specifications.

The conversion rule is to avoid producing a configuration that looks valid while changing the wire protocol or weakening a requested TLS check. Xray RAW HTTP camouflage and Xray QUIC are therefore not substituted with similarly named but incompatible sing-box transports. A fixed `echConfigList` is copied as `ech.config` content, not as a file path. DNS-based Xray ECH, Xray certificate pinning, and other TLS constraints without exact equivalents produce an unsupported result so the original profile can be preserved instead of being corrupted silently.

If one input contains both supported and unsupported proxy outbounds, full conversion is rejected as a unit as well: individual profiles no longer disappear from the result unnoticed.

Processing results now carry explicit loss counters. In `JSON → URI`, an unrepresentable profile remains as its original JSON and the UI shows a warning. In `Xray → sing-box`, skips in a mixed subscription are shown on the subscription card and in diagnostics; if no Xray profile can be retained, the Bridge returns an explicit processing error instead of an empty subscription.

The current sing-box schema is also followed for DNS-over-HTTP/3 (`type: h3`) and remote rule-set downloads through `http_client` instead of deprecated `download_detour`. WireGuard validates required keys and peer endpoints before conversion; when Xray omits local addresses, its documented defaults are emitted with the CIDR prefixes required by sing-box.

For `JSON → URI`, legacy VLESS flow `xtls-rprx-vision-udp443` is normalized, IDN names are converted to ASCII, and Hysteria2 preserves multi-port/port hopping, fixed ECH, and a single Xray certificate pin. Hysteria2 fields that the official URI cannot represent are not converted with silent loss.

### Intentional limitations

- Full Xray-to-sing-box conversion rejects a legacy outbound with multiple `vnext`, `servers`, or users: one sing-box outbound cannot represent that structure without changing its meaning. `JSON → URI` continues to expand it into separate links.
- DNS-based Xray ECH and Xray certificate pinning are not substituted with approximate sing-box fields that have different semantics.
- VMess URIs are still emitted in the legacy Base64 JSON format for compatibility with widely deployed clients; moving to the current Xray URL standard requires a separate client compatibility pass.

## Bridge behavior

The desktop server preserves the functional Bridge chain:

1. Resolve or decrypt the source link.
2. Request the provider with `x-hwid` and the configured `User-Agent`.
3. Limit the response to 32 MiB.
4. Decrypt an encrypted subscription body.
5. Apply the selected conversions.
6. Forward `Subscription-Userinfo`, `Content-Disposition`, `Profile-Update-Interval`, and `Profile-Title`.

Intentional desktop differences:

- stable saved URLs (`/sub/<UUID>`) are available in addition to the legacy `/url=...&hwid=...&ua=...` form;
- legacy arbitrary URLs are restricted to loopback clients;
- loopback is the safe default, while LAN exposure must be enabled explicitly;
- listener address and port are configurable;
- provider failures return HTTP 502/504 instead of an HTTP 200 body containing `Error: ...`;
- all successful 2xx provider responses are accepted, not only 200;
- malformed encrypted responses fail visibly instead of silently returning encrypted data;
- Android services, intents, notifications, tiles, Xposed hooks and Happ patching are intentionally outside the desktop product.

## Release assessment

The portable core is suitable for the first stable desktop release: source parity is high and the cryptographic JVM port is covered by generated vectors for every bundled RSA key.

Items that still require continued real-client or platform verification after release:

- import/update smoke tests in NekoBox, Hiddify, v2rayNG, Husi and Karing;
- non-KDE Linux tray testing;
- firewall instructions for LAN mode;
- timeout testing against real slow and stalled connections.

The oversized-response limit and concurrent client request handling are now covered by automated tests.

These are release-engineering and integration risks, not known missing decryption algorithms.

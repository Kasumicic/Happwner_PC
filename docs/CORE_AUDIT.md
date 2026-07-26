# Happwner PC core compatibility audit

Audit date: 2026-07-26

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
| Xray-to-sing-box | Equivalent | `SingBoxConverter.kt` is unchanged from Android 1.3. |

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

The portable core is suitable for a first preview release: source parity is high and the cryptographic JVM port is covered by generated vectors for every bundled RSA key.

Items still requiring real-client or platform verification before calling the application stable:

- import/update smoke tests in NekoBox, Hiddify, v2rayNG, Husi and Karing;
- Windows and non-KDE tray testing;
- Windows installers and automated CI builds;
- firewall instructions for LAN mode;
- timeout, oversized-response and concurrent-request stress tests.

These are release-engineering and integration risks, not known missing decryption algorithms.

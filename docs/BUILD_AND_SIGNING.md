# Build and signing — Karoon Tunnel

**Current version:** 2.0.0 (versionCode 5)  
**applicationId:** `app.karoon.tunnel`

## Mandatory project policy

Read [`AGENTS.md`](../AGENTS.md) and [`RELEASE_SIGNING_POLICY.md`](RELEASE_SIGNING_POLICY.md) before changing app code or producing an APK. The signing identity must remain stable and versionCode must increase for every app update intended for distribution.

## Prerequisites

- Android Studio Ladybug+ or command-line SDK
- JDK 17
- Android SDK 34
- NDK **26.1.10909125** (pinned in `app/build.gradle.kts`)
- CMake / NDK build tools for `hev-socks5-tunnel`

Clone with submodules if native trees are incomplete:

```bash
git clone --recursive https://github.com/Karoongh/android-vpn.git
```

Ensure `app/src/main/jni/hev-socks5-tunnel/third-part/` contains `lwip`, `yaml`, and `hev-task-system`.

## Debug APK

```bash
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/
```

GitHub Actions workflow `.github/workflows/android-build.yml` produces the `karoon-tunnel-debug-apk` artifact on each push to `main`.

## Canonical signing identity — do not change

All update APKs use the fixed project keystore:

| Setting | Value |
|---|---|
| Base64 keystore | `keystore/karoon-debug.jks.base64` |
| Decoded keystore | `keystore/karoon-debug.jks` |
| Alias | `karoon` |
| Store/key password | `karoon_tunnel_debug` |
| SHA-1 | `04:F8:E1:3B:FC:12:52:2F:59:E5:B8:E4:E4:9F:B1:82:C7:D8:07:BD` |
| SHA-256 | `06:F0:3D:3A:65:52:18:3C:F9:A9:FC:E2:76:C1:E9:49:0E:94:2C:67:FA:51:F8:85:A5:BA:EC:26:FD:BF:D1:B0` |

The workflow restores the keystore from the canonical Base64 file. Gradle signs both configured build types with the same signing configuration. Never generate a new key, use a machine-local debug key, or replace this file for an ordinary update.

**Security warning:** this development keystore and its credentials are present in the public repository. Treat the signing key as publicly exposed. It preserves update compatibility but is not suitable as a confidential production/Play Store signing key. Any migration to a private key requires explicit maintainer approval and a planned breaking migration. Do not silently rotate the key.

## Versioning

- Increase `versionCode` by at least 1 for every app-code change prepared for distribution.
- Never reuse or decrease versionCode.
- Update `versionName` to the matching human-readable version.
- Update `CHANGELOG.md` for user-facing releases.
- CI rejects app-code pull requests unless versionCode is strictly higher than the base branch.
- Documentation-only changes do not need a version bump.

## applicationId change (v2.0.0)

From version **2.0.0**, `applicationId` is `app.karoon.tunnel`. Older builds used `com.example.sshvpn`. Android treats these as **different apps**. Users must install the new package separately (or uninstall the old one).

Kotlin source package / `namespace` remains `com.example.sshvpn` unless you later migrate packages.

## Release checklist

1. Confirm `applicationId` remains `app.karoon.tunnel`.
2. Confirm versionCode is greater than the previously distributed build.
3. Build with the canonical keystore and configured Gradle signing.
4. Verify the APK signer SHA-256 matches the fingerprint above.
5. Test an update install over the previous version on a physical device.
6. Update `CHANGELOG.md`.

## Signature mismatch on install

`Package signature does not match the installed app` means the new APK is signed with a different key than the installed app **with the same applicationId**. Do not ask users to uninstall as a routine workaround; first investigate why the signer changed. Uninstalling removes app data and should only be a deliberate migration workaround.

## Related docs

- [`AGENTS.md`](../AGENTS.md) — mandatory instructions for AI agents and contributors
- [`RELEASE_SIGNING_POLICY.md`](RELEASE_SIGNING_POLICY.md) — authoritative signing/versioning policy
- [`README.md`](../README.md) — product overview
- [`PRIVACY.md`](../PRIVACY.md) — privacy policy
- [`docs/PLAY_STORE.md`](PLAY_STORE.md) — Play Console guidance
- [`CHANGELOG.md`](../CHANGELOG.md) — version history

# Build and signing — Karoon Tunnel

**Current version:** 1.9 (versionCode 4)

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

## Sample keystore (development only)

The repository includes a **development** keystore under `keystore/` so incremental installs do not require uninstall. Passwords and alias are documented in `keystore/SIGNING.md` if present.

**Do not use this keystore for Play Store production.** Generate your own upload key and enroll in Play App Signing.

## Release checklist

1. Change `applicationId` away from `com.example.sshvpn` for production.
2. Configure `signingConfigs` with your upload keystore (store secrets in CI, not in git).
3. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
4. `./gradlew :app:bundleRelease` for Play (AAB) or `assembleRelease` for sideload APK.
5. Verify ABI splits / universal package size and crash-free open on a physical device.

## Signature mismatch on install

`Package signature does not match the installed app` means the new APK is signed with a different key than the installed app. Uninstall the old app (data is wiped) or always sign with the same key for updates.

## Related docs

- [README.md](../README.md) — product overview
- [PRIVACY.md](../PRIVACY.md) — privacy policy
- [docs/PLAY_STORE.md](PLAY_STORE.md) — Play Console guidance
- [CHANGELOG.md](../CHANGELOG.md) — version history

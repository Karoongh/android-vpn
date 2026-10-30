# APK signing and versioning policy

**Project:** Karoon Tunnel  
**Android applicationId:** `app.karoon.tunnel`  
**Canonical signing alias:** `karoon`  
**Canonical keystore source:** `keystore/karoon-debug.jks.base64`

This document is the release policy for maintainers, human contributors, CI, and all AI coding agents.

## 1. One permanent signing identity

All APKs that are intended to update the installed Karoon Tunnel app must be signed with the same certificate. Android only accepts an in-place update when the package name and signing identity are compatible.

The repository currently uses one fixed keystore:

- Base64 source: `keystore/karoon-debug.jks.base64`
- Decoded build file: `keystore/karoon-debug.jks`
- Alias: `karoon`
- Gradle signing configuration: `app/build.gradle.kts`
- CI restoration: `.github/workflows/android-build.yml`

The current certificate fingerprints are:

- SHA-1: `04:F8:E1:3B:FC:12:52:2F:59:E5:B8:E4:E4:9F:B1:82:C7:D8:07:BD`
- SHA-256: `06:F0:3D:3A:65:52:18:3C:F9:A9:FC:E2:76:C1:E9:49:0E:94:2C:67:FA:51:F8:85:A5:BA:EC:26:FD:BF:D1:B0`

**Do not change this key or its credentials.** Do not generate a replacement key, use a local Android debug key, or sign an update with another certificate. A key change can make every existing installation reject the new APK.

A signing-key change is allowed only as an explicitly approved breaking migration. It must include a migration plan, clear user instructions, and updated fingerprints in this document and `docs/BUILD_AND_SIGNING.md`.

## 2. Important security limitation of the current key

The current keystore is stored in the public repository as a Base64 file and its development credentials are documented. Therefore, treat this certificate as **publicly exposed**, not as a secret suitable for a high-trust production or Play Store release.

This policy preserves compatibility with existing sideloaded installations. It does not make the exposed key confidential. Anyone who obtains the public keystore and credentials may be able to sign an APK that Android accepts as an update. Do not use this key for a sensitive production distribution channel. Before moving to a private production signing key, plan and approve a deliberate migration; do not silently rotate it.

## 3. VersionCode must increase

Android uses `versionCode` to decide whether an APK is a newer update.

- For every app-code change prepared for distribution, increase `versionCode` above the previous released/main value.
- Never decrease or reuse a versionCode.
- Update `versionName` to the corresponding user-facing version.
- Update `CHANGELOG.md` for each user-facing release.
- Documentation-only changes do not require a version bump.
- CI rejects a pull request that changes files under `app/` without increasing versionCode.

The values are maintained in `app/build.gradle.kts`. Do not change applicationId as part of ordinary version upgrades.

## 4. Rules for AI agents and contributors

All AI agents must read the repository-root `AGENTS.md` before making changes. These requirements apply to Codex, Claude Code, Copilot coding agents, and other tools that support repository instructions.

The instructions are also enforced where practical by GitHub Actions: the build restores the canonical keystore, checks its certificate fingerprint, and validates versionCode increases on app-code pull requests.

Repository instructions cannot technically control an unrelated external agent that ignores them. Branch protection / required status checks should be enabled by the repository owner to prevent merging around CI.

## 5. Release verification

Before distributing an APK:

1. Build with the repository's Gradle signing configuration.
2. Verify the APK with Android `apksigner`.
3. Confirm the SHA-256 signer fingerprint matches the value above.
4. Confirm applicationId is `app.karoon.tunnel`.
5. Confirm versionCode is greater than the previously distributed APK.
6. Test installation as an update over the previous version.

A successful CI build proves that the artifact was built and signed. It does not prove installation or runtime behavior on a device.

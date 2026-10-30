# Repository-wide instructions for AI coding agents

These rules apply to every AI agent and automated coding assistant working in this repository. Read this file before editing code, building APKs, or preparing a release.

## Android application identity and signing — mandatory

- Keep `applicationId = "app.karoon.tunnel"`. Do not change it unless the maintainer explicitly approves a planned app-identity migration.
- Every installable APK intended to update an existing Karoon Tunnel installation MUST use the project's existing signing certificate.
- The canonical keystore source is `keystore/karoon-debug.jks.base64`; CI decodes it to `keystore/karoon-debug.jks`. The alias is `karoon`. The signing configuration and credentials are already wired into `app/build.gradle.kts`.
- Never generate a new keystore, use a machine-local debug keystore, substitute another alias, or silently change signing credentials.
- Never delete, replace, regenerate, or re-encode the canonical keystore. A signing-key migration is a breaking release operation and requires explicit maintainer approval, a documented migration plan, and a deliberate user reinstall/update strategy.
- Do not create a second signing identity for debug/release builds. Keep the configured signing identity consistent for all APKs distributed as updates.
- Before publishing an APK, verify its signer certificate fingerprint against `docs/BUILD_AND_SIGNING.md`. A build that has a different signer MUST NOT be distributed.
- Do not print keystore bytes, passwords, or private-key material in logs, issues, PRs, or generated documentation.

## Versioning — mandatory for app changes

- `versionCode` in `app/build.gradle.kts` is the Android update-order number. It MUST be strictly greater than the previous released/main-branch value for every app-code change intended for distribution.
- Never reuse, decrease, reset, or leave `versionCode` unchanged when preparing an app update. Android rejects an update whose versionCode is not higher.
- Increment `versionCode` by at least 1 for each new app release. Keep it an integer.
- Update `versionName` to the corresponding human-readable release version and update `CHANGELOG.md` for user-visible changes.
- CI checks PRs that modify `app/` and rejects them unless versionCode increases. Do not bypass or weaken this check.
- Documentation-only changes do not require an app version bump.

## Required release checks

1. Read `docs/RELEASE_SIGNING_POLICY.md` and `docs/BUILD_AND_SIGNING.md`.
2. Build using the repository's Gradle configuration and canonical keystore.
3. Verify the APK signature and expected certificate fingerprint.
4. Confirm applicationId and that versionCode is higher than the previously released build.
5. Never claim an APK was device-tested unless it was actually installed and tested on a physical/emulated device.

If a requested change conflicts with these rules, stop and ask the maintainer instead of silently changing signing identity or version history.

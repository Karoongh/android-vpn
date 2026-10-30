# Changelog

All notable changes to **Karoon Tunnel** are documented here.

Format inspired by [Keep a Changelog](https://keepachangelog.com/).
Versioning uses app `versionName` with monotonic `versionCode`.

## [2.1.1] — 2026-10-30 (versionCode 7)

### Changed

- Keep ordinary diagnostic entries in a rolling one-hour in-memory window.
- Retain critical errors independently for six hours, with a five-minute breadcrumb snapshot for troubleshooting.
- Highlight errors and warnings in the Logs screen; cap the persisted error journal at 256 KB.
- Remove the previous all-events disk log so routine heartbeats and probe results do not accumulate.

### Added

- Share diagnostic logs as a TXT attachment through the native Android Sharesheet.
- Show a privacy warning before sharing logs.
- Expose only the app-private temporary log folder through a non-exported FileProvider.

### Changed

- Enable AndroidX and add AndroidX Core FileProvider support.

## [2.0.0] — 2026-10-23 (versionCode 5)

### Changed

- **applicationId** set to `app.karoon.tunnel` (production-ready; no longer `com.example.sshvpn`)
- **versionName** `2.0.0` / **versionCode** `5`
- README: short Persian section for GitHub visitors
- Play / build docs aligned with the new id and version

### Notes

- Installing coexists with, but does not replace, older packages signed as `com.example.sshvpn`
- Kotlin package / namespace remains `com.example.sshvpn` for source stability; only the install id changed

## [1.9] — 2026-10-23 (versionCode 4)

### Added

- Multi-protocol support: **VLESS**, **VMess**, **Trojan**, **Shadowsocks** via embedded Xray-core
- Import for `vless://`, `vmess://`, `trojan://`, `ss://`, `ssh://`, multi-line and Base64 subscription-style text
- Protocol picker in Add / Edit config
- Protocol badges on the profile list and home subtitle
- Startup error screen (instead of silent close) when UI initialization fails

### Fixed

- Service start/stop actions aligned with `ACTION_START` / `ACTION_STOP` and `EXTRA_CONFIG`
- Traffic counters use `TunnelStats.uplink` / `downlink`
- Log display uses `LogStore.text()`
- Launcher `roundIcon` points at a valid drawable resource
- Safer profile display names when username is empty (e.g. VLESS)

### Changed

- English-first documentation for GitHub and store readiness

## [1.0] – [1.8] — 2026-10

### Added (earlier releases)

- SSH VPN over Android `VpnService` + hev-socks5-tunnel
- Local SOCKS5 bridge and JSch SSH client
- Multi-profile storage
- Bottom navigation: Home, Configs, Stats, Logs, More
- Live uplink/downlink charts
- Keyboard-interactive SSH support
- Fixed debug signing keystore for update-friendly installs
- GitHub Actions CI builds

---

## Unreleased

- Broader on-device validation of Xray protocols with production share links
- Publisher-owned Play App Signing upload key (do not use the sample debug keystore on Play)

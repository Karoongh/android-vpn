# Changelog

All notable changes to **Karoon Tunnel** are documented here.

Format inspired by [Keep a Changelog](https://keepachangelog.com/).
Versioning uses a simple `MAJOR.MINOR` app `versionName` with monotonic `versionCode`.

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
- Production applicationId and Play App Signing keystore (publisher responsibility)

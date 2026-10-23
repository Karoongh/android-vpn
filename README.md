# Karoon Tunnel

**Version 1.9** (versionCode 4)

Open-source Android VPN client. You bring your own servers and configuration links. The app does **not** sell, host, or include any VPN service accounts.

[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](https://github.com/Karoongh/android-vpn)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Features

- **Protocols:** SSH (Direct / UDPGW), VLESS, VMess, Trojan, Shadowsocks
- **Import formats:** `vless://` · `vmess://` · `trojan://` · `ss://` · `ssh://` · JSON · key=value · Base64 / multi-line subscriptions
- **Multiple profiles** — save, select, edit, delete
- **Device-wide tunnel** via Android `VpnService` + hev-socks5-tunnel (no root)
- **Live traffic charts** (uplink / downlink) and session totals
- **Copyable logs** for troubleshooting
- **Speed / stats tab**
- **English UI**

### Architecture (high level)

```
Apps → Android VpnService (TUN)
     → hev-socks5-tunnel (tun2socks)
     → local SOCKS5
         ├─ SSH path: JSch (direct-tcpip)
         └─ VLESS / VMess / Trojan / SS: embedded Xray-core
     → your remote server → Internet
```

---

## Requirements

| Item | Value |
| --- | --- |
| Android | 8.0 (API 26) or newer |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Root | Not required |
| Account | None — you supply your own configs |

---

## Install (users)

1. Download a signed APK from [GitHub Actions artifacts](https://github.com/Karoongh/android-vpn/actions) or a published release.
2. Enable installation from the source you trust (unknown sources / Files app).
3. Install and open **Karoon Tunnel**.
4. Grant the **VPN connection** permission when prompted.
5. On Android 13+, allow notifications if you want the connection status notification.

**Configs → Import link** and paste a share URL, or **Add new** and pick a protocol.

---

## Build from source

```bash
git clone --recursive https://github.com/Karoongh/android-vpn.git
cd android-vpn
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/
```

- NDK **26.1.10909125** (or compatible) and Android SDK 34
- Details: [docs/BUILD_AND_SIGNING.md](docs/BUILD_AND_SIGNING.md)

For Play Store / production, use your **own** upload keystore. Do not ship the sample debug keystore in production builds.

---

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | Connect to your proxy/SSH server |
| `ACCESS_NETWORK_STATE` | Detect network changes / reconnect behavior |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the VPN service alive with a visible notification |
| `POST_NOTIFICATIONS` (API 33+) | Optional status notification |
| VPN service (`BIND_VPN_SERVICE`) | System VPN API — user must approve each time or use Always-on VPN |

The app does not request location, contacts, camera, microphone, or SMS.

---

## Privacy

- **No analytics SDK**, no ads, no third-party tracking.
- Configs and passwords are stored **only on the device** (SharedPreferences).
- Traffic is sent only to **servers you configure**.
- Full policy: [PRIVACY.md](PRIVACY.md)

---

## Security notes

- You are responsible for the trustworthiness of the servers and links you import.
- SSH host key verification is currently relaxed for usability; treat untrusted networks accordingly.
- Credentials are not encrypted at rest beyond normal Android app storage isolation.
- This software is provided as-is; see [LICENSE](LICENSE).

---

## What this app is not

- Not a commercial VPN subscription service
- Does not include free public nodes or “one-tap unlimited VPN”
- Does not guarantee anonymity, unblock every site, or hide activity from your configured server operator

These statements are intentional for store compliance and user clarity.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Play Store listing notes

See [docs/PLAY_STORE.md](docs/PLAY_STORE.md) for suggested store text, Data safety answers, and release checklist.

## License

MIT — see [LICENSE](LICENSE).

### Third-party

- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) — MIT
- [mwiede/jsch](https://github.com/mwiede/jsch) — BSD-style
- [Xray-core](https://github.com/XTLS/Xray-core) — MPL-2.0 (embedded binary for non-SSH protocols)

---

**Karoon Tunnel** · v1.9 · Open source · Bring your own server

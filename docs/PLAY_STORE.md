# Google Play preparation — Karoon Tunnel v2.0.0

This guide helps you publish **Karoon Tunnel** in a way that matches Google Play policies for VPN apps. It is not legal advice.

## Current app identity

| Field | Value |
| --- | --- |
| Display name | Karoon Tunnel |
| versionName | **2.0.0** |
| versionCode | **5** |
| applicationId | **`app.karoon.tunnel`** |
| minSdk | 26 |
| targetSdk | 34 |

> Use **Play App Signing** and your own **upload keystore**. Do not upload builds signed only with the sample `keystore/karoon-debug.jks` from this repository.

## Suggested store listing (English)

### Short description (≤80 characters)

```
BYO-server VPN client: SSH, VLESS, VMess, Trojan, Shadowsocks
```

### Full description

```
Karoon Tunnel is an open-source VPN client for Android. You connect using
servers and links that you already have. The app does not provide VPN
servers, subscriptions, or accounts.

Supported protocols
• SSH (Direct and UDPGW)
• VLESS, VMess, Trojan, Shadowsocks

Import standard share links: vless:// vmess:// trojan:// ss:// ssh://
Save multiple profiles, view live traffic stats, and copy logs for support.

Requires Android 8.0 or later. No root required.
You must approve the Android VPN permission to route traffic.

Privacy: configurations stay on your device. No ads or analytics SDKs in
the open-source build. See the in-repo Privacy Policy.
```

### What to avoid in the listing

- Do not promise “unblock everything”, “military-grade anonymity”, or “no logs on our servers” if you do not operate servers.
- Do not include or advertise free shared nodes inside the app or listing if you cannot legally and operationally support them.
- Do not use other brands’ trademarks as your app name.

## Data safety form (approximate answers)

Align the Play Console **Data safety** form with [PRIVACY.md](../PRIVACY.md):

| Topic | Suggested answer |
| --- | --- |
| Does the app collect/share user data? | **No** collection by the developer for analytics; configs stay on device |
| Location | Not collected |
| Personal info (name, email) | Not collected by the app |
| Financial info | Not collected |
| Photos / files | Not collected |
| App activity / web browsing | Traffic is handled for VPN; not uploaded to developer |
| Encryption in transit | Depends on protocol/server you configure |
| Users can request deletion | Uninstall / clear app data removes local configs |

Declare the **VPN service** use case accurately when prompted.

## VPN policy checklist

- [ ] Privacy Policy URL publicly reachable (GitHub or your site) — use `PRIVACY.md`
- [ ] Listing states clearly that **users bring their own servers**
- [ ] No misleading “unlimited free VPN” claims
- [ ] Target API level meets current Play requirements
- [ ] Foreground service type and notifications comply with current rules
- [ ] Production signing (Play App Signing) configured
- [ ] Content rating questionnaire completed
- [ ] Export compliance / cryptography declarations if asked

## Permissions text (for Play justification)

- **VPN:** Required to create a system-wide tunnel to the user’s proxy/SSH server after consent.  
- **Internet / network state:** Connect to user-defined endpoints; detect connectivity.  
- **Foreground service + notification:** Android requirement while a VPN session is active.  
- **Notifications:** Status only; optional on Android 13+.

## Release build notes

1. Keep `applicationId` as `app.karoon.tunnel` (or your final id) for all Play uploads of this listing.  
2. Bump `versionCode` on every Play upload; keep `versionName` human-readable.  
3. Attach a privacy policy URL in Play Console.  
4. Test on arm64-v8a hardware: install, VPN consent, connect, disconnect, uninstall.  
5. Prefer AAB (`bundleRelease`) for Play.

## Support

Prefer GitHub Issues for the open-source project. For a commercial listing, add a support email in Play Console and in the privacy policy contact section.

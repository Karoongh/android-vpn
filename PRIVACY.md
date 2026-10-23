# Privacy Policy — Karoon Tunnel

**Last updated:** 2026-10-23  
**App version covered:** 2.0.0 and later  
**applicationId:** `app.karoon.tunnel`  
**Developer / publisher:** Karoon (repository: [Karoongh/android-vpn](https://github.com/Karoongh/android-vpn))

This privacy policy applies to the Android application **Karoon Tunnel** (“the App”).

## 1. Summary

Karoon Tunnel is a **local VPN client**. You supply your own server addresses and credentials. The App does **not** operate a VPN network, does **not** require an account with us, and does **not** include advertising or analytics SDKs in the open-source builds described in this repository.

## 2. Data the App processes

### 2.1 Data you enter

- Server hostnames / IP addresses and ports  
- Usernames, passwords, UUIDs, private keys, and related protocol fields  
- Profile names and import links (`vless://`, `vmess://`, `trojan://`, `ss://`, `ssh://`, etc.)

This data is stored **on your device** (Android SharedPreferences / app-private storage) so profiles can be reused.

### 2.2 Network traffic

When you connect, device traffic selected by the system VPN interface is forwarded according to your configuration to **the servers you chose**. Operators of those servers may see connection metadata and, depending on the protocol and their setup, traffic content. That processing is controlled by **you and your server provider**, not by a Karoon backend.

### 2.3 Logs

The App may keep a short **on-device** log ring buffer (connection status, errors) to help you debug. Logs stay on the device unless you copy them (e.g. clipboard).

### 2.4 Data we do not collect

In the standard open-source build:

- No account registration with the developer  
- No crash or analytics telemetry sent to the developer  
- No advertising identifiers used by the App for ads  
- No sale of personal data  

If you obtain a modified build from a third party, that party’s policy may differ.

## 3. Permissions

| Permission | Purpose |
| --- | --- |
| Internet / network state | Reach your configured servers; react to network changes |
| VPN service | Create a system VPN tunnel after your explicit approval |
| Foreground service | Run the tunnel with a persistent notification while connected |
| Notifications (Android 13+) | Show connection status (optional to deny; VPN may still work) |

## 4. Children

The App is not directed at children under 13. Do not use the App to submit children’s personal data.

## 5. Security

Storage uses standard Android app sandboxing. Credentials are not encrypted with a separate app-level vault in current versions. Protect your device lock screen and do not install untrusted APKs.

## 6. Your choices

- Do not connect if you do not trust the configured server.  
- Delete profiles in the App or uninstall the App to remove local config data.  
- Revoke VPN permission in system settings at any time.

## 7. Changes

We may update this policy when features change. The “Last updated” date will be revised. Continued use after an update means you acknowledge the revised policy.

## 8. Contact

For privacy questions about this open-source project, open an issue on:

https://github.com/Karoongh/android-vpn/issues

Replace this contact method with your official support email before a commercial Play Store listing if required by local law.

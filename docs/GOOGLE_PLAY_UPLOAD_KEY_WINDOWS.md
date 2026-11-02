# Google Play upload key setup (Windows)

The upload keystore is a private signing credential. Do not commit it, email it, or paste it into chat.

## 1. Generate the key locally

Open PowerShell in a private folder outside the repository (for example, `$HOME\KaroonSigning`):

```powershell
New-Item -ItemType Directory -Force "$HOME\KaroonSigning" | Out-Null
Set-Location "$HOME\KaroonSigning"
keytool -genkeypair -v -keystore karoon-play-upload.jks -alias karoon-upload -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Karoon Tunnel, OU=Android, O=Karoon, C=DE"
```

Use strong, unique passwords when prompted. Keep a secure offline backup of the JKS file and passwords. Losing the upload key can complicate future updates; Google Play App Signing is intended to protect the app signing key separately.

## 2. Create the GitHub Actions secrets

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret**.

Create these four secrets:

- `PLAY_UPLOAD_KEYSTORE_BASE64`: Base64 content of the JKS file. In PowerShell:
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\KaroonSigning\karoon-play-upload.jks")) | Set-Clipboard
  ```
  Paste the clipboard contents directly into the GitHub secret field.
- `PLAY_UPLOAD_STORE_PASSWORD`: the keystore password you chose.
- `PLAY_UPLOAD_KEY_ALIAS`: `karoon-upload`
- `PLAY_UPLOAD_KEY_PASSWORD`: the key password you chose.

Never add any of these values to source code, workflow files, issues, or pull requests.

## 3. Google Play Console

When creating the app's first release, opt in to **Play App Signing** and use the generated JKS as the **upload key**. Google manages the app signing key; CI uses this upload key only to sign the AAB sent to Play.

## 4. Build

After all four secrets are saved, run the **Android build** workflow from the Actions tab (or push a commit to main). The workflow will produce a signed `app-release.aab` artifact. Without all four secrets, it intentionally skips the Play AAB and continues to build the debug APK for testing.

## Important compatibility note

The existing GitHub sideload APK uses a different, publicly exposed debug certificate. A Play-installed app signed by Google Play will not update an installation signed by that debug certificate in place. Users may need to uninstall the GitHub APK before installing the Play version; local app data may be lost.

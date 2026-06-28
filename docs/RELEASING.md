# Releasing PDF-App

This guide takes you from zero to a published GitHub Release with a **signed**
APK + AAB attached. It ships through **GitHub Releases** (no Play Console
required for sideloading).

There are two parts:

- **Part 1 — one-time signing setup** (needs a laptop with Java). Do this once.
- **Part 2 — cut a release** (do this every time you want to ship). Works from a
  laptop *or* a phone browser.

If you skip Part 1, releases still build but are **debug-signed** — fine for
installing on your own device, **not** valid for the Google Play Store.

---

## Part 1 — One-time signing setup (laptop)

### 1. Check you have Java

The keystore is created with `keytool`, which ships with the JDK. If you don't
have one, install Temurin:

```powershell
# Windows (PowerShell)
winget install EclipseAdoptium.Temurin.21.JDK
```
```bash
# macOS
brew install temurin
# Ubuntu/Debian
sudo apt-get install -y openjdk-17-jdk
```

> Temurin 25 also works for the keystore. 21 is a safer pick if you ever build
> the app locally too. After installing, **open a new terminal** so PATH updates.

Verify (any OS):

```bash
keytool -help
```

If that prints help text, you're set.

### 2. Generate your signing keystore

Run this in a folder **outside** the repo (so it can never be committed). It's
one line, so it works the same in PowerShell, cmd, or a Unix shell:

```bash
keytool -genkeypair -v -keystore release.keystore -alias pdfapp -keyalg RSA -keysize 2048 -validity 10000
```

It will prompt you:

1. **Keystore password** — type one and remember it (you'll enter it twice).
2. **Name / organisation / city / country** — optional; press Enter to skip each,
   then type `yes` to confirm.
3. **Key password** — press **Enter** to reuse the keystore password (simplest;
   then your key password equals your keystore password).

You now have `release.keystore`.

> ⚠️ **Back this file up somewhere safe** (password manager, encrypted drive).
> If you lose it you can never publish updates under the same app identity, and
> if it leaks someone else can. Treat it like the master key to the app.

### 3. Base64-encode the keystore

GitHub secrets hold text, so encode the file to one line:

```powershell
# Windows (PowerShell) — run from the folder containing release.keystore
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -NoNewline keystore.b64
```
```bash
# Linux
base64 -w0 release.keystore > keystore.b64
# macOS
base64 -i release.keystore | tr -d '\n' > keystore.b64
```

Open `keystore.b64` and copy its entire contents (one long line). On Windows you
can copy it straight to the clipboard instead of opening the file:

```powershell
Get-Content keystore.b64 -Raw | Set-Clipboard
```

### 4. Add the four secrets on GitHub

In a browser: **repo → `Settings` → `Secrets and variables` → `Actions` →
`New repository secret`.** Add these four (name exactly as shown):

| Secret name | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the full contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | the keystore password from step 2 |
| `KEY_ALIAS` | `pdfapp` |
| `KEY_PASSWORD` | the key password (same as the keystore password if you pressed Enter) |

That's the one-time setup done. The build reads these via environment variables;
the keystore is decoded to a temporary file inside the CI runner and never stored.

You can now delete the local `keystore.b64` (keep `release.keystore` backed up).

---

## Part 2 — Cut a release

Pick **one** of these. Both produce the same result: a tag → the Release workflow
builds the APK + AAB → they're attached to a published GitHub Release.

### Option A — GitHub website (no git needed; works on a phone too)

1. Repo → **`Releases`** (right sidebar, or visit `/releases`).
2. **`Draft a new release`**.
3. Under **`Choose a tag`**, type `v0.1.0`, then click
   **`Create new tag: v0.1.0 on publish`**.
4. Title: `v0.1.0`. Leave the body empty (notes are auto-generated).
5. **`Publish release`**.

### Option B — terminal (laptop)

```bash
git clone https://github.com/shavei/Pdf-app.git    # or: git pull, if already cloned
cd Pdf-app
git tag v0.1.0
git push origin v0.1.0
```

### What happens next

Publishing the tag triggers **`.github/workflows/release.yml`**, which:

1. builds `:app:assembleRelease` and `:app:bundleRelease`,
2. names the build from the tag (`versionName = 0.1.0`, `versionCode = <run number>`),
3. signs with your keystore (Part 1) — or debug-signs if you skipped it,
4. uploads the **APK + AAB** to the GitHub Release.

Watch progress under the **`Actions`** tab (the **Release** workflow, ~3–5 min).

---

## Part 3 — Get and install the app

- **The release:** repo → **`Releases`** → `v0.1.0` → under **Assets**, download
  `app-release.apk` (and `app-release.aab` for the Play Store).
- **On an Android phone:** tap the downloaded `.apk`; the first time, allow
  "install unknown apps" for your browser/files app when prompted.
- **Latest test build (no release needed):** **`Actions`** → newest **CI** run →
  **Artifacts** → `app-debug-apk`.

---

## Versioning

`versionName` / `versionCode` come from `-PappVersionName` / `-PappVersionCode`,
which the Release workflow derives from the tag and the CI run number. To ship
`v1.2.0`, just tag `v1.2.0`. Local builds default to `0.1.0` / `1`.

---

## Troubleshooting

- **Release has no APK/AAB attached** — open the **Actions → Release** run; if it
  failed, the logs say why. A common cause is a typo in a secret name.
- **"keytool: command not found"** — the JDK isn't installed or not on your PATH
  (see Part 1, step 1).
- **APK installs but won't update later from the Play Store** — the build was
  debug-signed; add the Part 1 secrets and cut a new release.
- **Lost the keystore** — you cannot recover it. For sideloaded APKs you can start
  a fresh keystore (users must uninstall/reinstall); for the Play Store, you'd need
  Google's key-reset process. This is why the backup in Part 1 matters.

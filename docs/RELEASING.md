# Releasing PDF-App

This guide takes you from zero to a published GitHub Release with a **signed**
APK + AAB attached. It ships through **GitHub Releases** (no Play Console
required for sideloading).

> **Just want to install the app?** You don't need any of this. The README's
> **Download Signet** button is a one-tap permalink to the newest signed
> release, and every push to `main` that passes all checks also auto-publishes a
> debug-signed APK to the rolling [`Latest build`](../../releases/tag/latest).
> This guide is only for cutting **signed, versioned** releases via the
> `release` job in [`ci.yml`](../.github/workflows/ci.yml).

There are two parts:

- **Part 1 — one-time signing setup** (needs a laptop with Java). Do this once.
- **Part 2 — cut a release** (do this every time you want to ship). Works from a
  laptop *or* a phone browser.

If you skip Part 1, releases still build but are **debug-signed** — fine for
installing on your own device, **not** valid for the Google Play Store.

> **Where the release is built:** there is a single pipeline,
> [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). Its `release` job is
> what a `vX.Y.Z` tag triggers; the `publish` job maintains the rolling
> `Latest build`. There is no separate `release.yml`.

---

## Part 1 — One-time signing setup (laptop)

### 1. Check you have Java

The keystore is created with `keytool`, which ships with the JDK.

```bash
keytool -help >/dev/null 2>&1 && echo "keytool OK" || echo "install a JDK first"
```

If it says "install a JDK first", install Temurin/OpenJDK 17 (macOS: `brew install temurin`;
Ubuntu/Debian: `sudo apt-get install -y openjdk-17-jdk`; Windows: install from adoptium.net),
then re-run the check.

### 2. Generate your signing keystore

Run this in a folder **outside** the repo (so it can never be committed):

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias pdfapp \
  -keyalg RSA -keysize 2048 \
  -validity 10000
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

```bash
# Linux:
base64 -w0 release.keystore > keystore.b64

# macOS:
base64 -i release.keystore | tr -d '\n' > keystore.b64
```

Open `keystore.b64` and copy its entire contents (one long line).

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

**Cutting a release is one step: tag the version `main` is already on.** Check it
with `./gradlew -q :app:appVersion` (or read `appVersionName` in
`gradle.properties`) — right now that is `1.4.1`, so the next release is `v1.4.1`.

You never edit the version by hand. Once the release publishes, CI commits the
next patch (`1.4.1` → `1.4.2`) to `main` itself, so the version climbs by one
every time and the next release is simply `v1.4.2`.

Two things the automation does not do, both optional:

- **The CHANGELOG and the README's pinned download links** — move the
  `[Unreleased]` entries under a `[X.Y.Z]` heading and bump the links in a
  release-prep commit before tagging.
- **Jumping a minor or major** — set `appVersionName` to e.g. `1.5.0` on `main`
  and tag `v1.5.0`. The patch bumps continue from there (`1.5.1`, `1.5.2`, …).

The `release` job re-reads `gradle.properties` and **fails if the tag disagrees
with it**, so a tag can never ship a mislabelled build.

### Option A — GitHub website (no git needed; works on a phone too)

1. Repo → **`Releases`** (right sidebar, or visit `/releases`).
2. **`Draft a new release`**.
3. Under **`Choose a tag`**, type `v1.4.1`, then click
   **`Create new tag: v1.4.1 on publish`**.
4. Title: `v1.4.1`. Leave the body empty (notes are auto-generated).
5. **`Publish release`**.

### Option B — terminal (laptop)

```bash
git clone https://github.com/shavei/Pdf-app.git    # or: git pull, if already cloned
cd Pdf-app
git tag v1.4.1
git push origin v1.4.1
```

### What happens next

Publishing the tag triggers the **`release` job in
`.github/workflows/ci.yml`**, which:

1. checks the tag against `appVersionName` in `gradle.properties` and stops there
   if they disagree,
2. builds `:app:assembleRelease` and `:app:bundleRelease` with
   `versionName = 1.4.1`, `versionCode = <commit count>`,
3. signs with your keystore (Part 1) — or debug-signs if you skipped it,
4. uploads the **APK + AAB** to the GitHub Release as `Signet-1.4.1.apk` and
   `Signet-1.4.1.aab`,
5. commits `appVersionName=1.4.2` to `main`, so the next release is `v1.4.2`
   with nothing for you to edit.

Watch progress under the **`Actions`** tab (the **CI** run for your tag, whose
**Build + publish release** job does this, ~3–5 min).

---

## Part 3 — Get and install the app

- **The release:** repo → **`Releases`** → `v1.4.1` → under **Assets**, download
  `Signet-1.4.1.apk` (and `Signet-1.4.1.aab` for the Play Store).
- **On an Android phone:** tap the downloaded `.apk`; the first time, allow
  "install unknown apps" for your browser/files app when prompted.
- **Latest build (no release needed):** the rolling
  [`Latest build`](../../releases/tag/latest) release always carries the newest
  `Signet.apk` built from `main`. It is reached by its own tag, not by
  `/releases/latest`: that permalink — and the README's **Download Signet**
  button — belongs to the newest signed release, which ships its own
  unversioned `Signet.apk` for exactly that purpose. The version a rolling
  build installs is in the release title, on Signet's home screen and in
  Android's App info.
  Note the two are **differently signed**: a debug-signed rolling build and a
  signed release cannot replace one another in place, so moving between them
  means uninstalling first.
  (CI no longer archives a per-run copy of this APK. It was 26 MB on every run
  and the rolling release already carries the same build; to get an APK from a
  branch that has not merged, run the workflow manually or build it locally.)

---

## Versioning

One marketing version, one build number, no commit hashes in the version name:

| Build | Version shown in the app and in App info | `versionCode` |
| --- | --- | --- |
| Tagged release (`v1.4.1`) | `1.4.1` | commit count |
| Rolling "Latest build" from `main` | `1.4.1 (build 102)` | commit count |
| Local `./gradlew assembleDebug` | `1.4.1` | `1` |

**One source of truth, maintained by CI.** `appVersionName` in
**`gradle.properties`** is the version under development, and the `release` job
advances it by one patch after every published release. Everything else derives
from it:

- `app/build.gradle.kts` reads it for every build, so a local APK is labelled the
  same way CI labels one.
- `.github/actions/app-version` reads the same line for all three CI jobs — no
  job carries its own copy of the version — and fails the release when a tag
  disagrees with it.
- CI passes `-PappBuildNumber=<commit count>` for untagged builds; the build
  appends it as ` (build N)` and reuses it as the `versionCode`, so every rolling
  build outranks the last and installs in place over it.
- Signet shows the result on its home screen, so the running build identifies
  itself without a trip to Android's App info.

Check what a build would stamp, without unpacking an APK:

```bash
./gradlew -q :app:appVersion                     # 1.4.1 (versionCode 1)
./gradlew -q :app:appVersion -PappBuildNumber=102  # 1.4.1 (build 102) (versionCode 102)
```

The latest published release is `v1.3.0`; `appVersionName` is `1.4.1`, so the next
release is `v1.4.1`. After that one publishes, CI moves `main` to `1.4.2` and the
release after it is `v1.4.2` — the number keeps climbing by one on its own.

### Artifact names

| Where | Name |
| --- | --- |
| Tagged release assets | `Signet-<version>.apk`, `Signet-<version>.aab` |
| Rolling `Latest build` asset | `Signet.apk` (unversioned; reached via the `latest` *tag*) |
| Signed release, unversioned copy | `Signet.apk` (what `/releases/latest/download/` serves) |

CI uploads no APK artifact. Test reports are archived only when a run fails, and
expire after 7 days.

Releases up to `v1.3.0` used AGP's default `app-release.apk` / `app-release.aab`;
those old links still work.

---

## Troubleshooting

- **Release has no APK/AAB attached** — open the **Actions → CI** run for your
  tag and check the **Build + publish release** job; if it
  failed, the logs say why. A common cause is a typo in a secret name.
- **"Tag v1.4.2 does not match appVersionName=1.4.1"** — you tagged a version
  `main` is not on. Tag `v1.4.1` instead (what `./gradlew -q :app:appVersion`
  reports), or set `appVersionName=1.4.2` on `main`, delete the tag
  (`git push --delete origin v1.4.2`) and re-tag the new commit.
- **The version stopped climbing** — check the **Bump the patch version on
  main** step of the release run. If `main` is a protected branch, the push is
  rejected there; allow the `github-actions` bot to push to `main`, or bump
  `appVersionName` by hand after each release.
- **"keytool: command not found"** — the JDK isn't installed or not on your PATH
  (see Part 1, step 1).
- **APK installs but won't update later from the Play Store** — the build was
  debug-signed; add the Part 1 secrets and cut a new release.
- **Lost the keystore** — you cannot recover it. For sideloaded APKs you can start
  a fresh keystore (users must uninstall/reinstall); for the Play Store, you'd need
  Google's key-reset process. This is why the backup in Part 1 matters.

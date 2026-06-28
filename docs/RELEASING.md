# Releasing PDF-App

The project ships through **GitHub Releases**. Two workflows are involved:

- **CI** (`.github/workflows/ci.yml`) — on every push/PR, builds a debug APK and
  uploads it as the `app-debug-apk` artifact (Actions → run → Artifacts).
- **Release** (`.github/workflows/release.yml`) — on a `vX.Y.Z` tag, builds a
  release **APK + AAB** and attaches them to a GitHub Release.

## Cut a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

The tag triggers the Release workflow, which:
1. builds `:app:assembleRelease` and `:app:bundleRelease`,
2. names the build from the tag (`versionName = 1.0.0`, `versionCode = <run number>`),
3. signs with your release keystore if the secrets below are set (otherwise falls
   back to **debug signing** — installable for testing, but not Play-ready),
4. creates a GitHub Release with auto-generated notes and the APK + AAB attached.

You can also run the workflow manually (Actions → Release → *Run workflow*) to
build artifacts without publishing a Release.

## Signing secrets (one-time setup)

Generate a keystore (keep it out of the repo — it is git-ignored):

```bash
keytool -genkeypair -v -keystore release.keystore \
  -alias pdfapp -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore   # copy the output
```

Add these as **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of `release.keystore` (command above) |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | the key alias (e.g. `pdfapp`) |
| `KEY_PASSWORD` | the key password |

The build reads these via environment variables in `app/build.gradle.kts`; the
keystore is decoded to a temp file inside the runner and never persisted.

> ⚠️ Keep the keystore and its passwords safe and backed up. Losing the keystore
> means you can no longer ship updates under the same app signature.

## Versioning

`versionName`/`versionCode` come from `-PappVersionName` / `-PappVersionCode`,
which the Release workflow derives from the tag and the run number. Local builds
default to `0.1.0` / `1`.

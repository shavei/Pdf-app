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

## Publishing to Google Play

A third workflow, **`.github/workflows/play-publish.yml`**, builds a
release-signed AAB and uploads it to Google Play via the
[Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)
plugin (`:app:publishReleaseBundle`). It is **manual** (Actions → *Publish to
Google Play* → *Run workflow*) so you choose the track (`internal` → `alpha` →
`beta` → `production`) and version each time.

Store-listing text lives under `app/src/main/play/` and is uploaded alongside the
build. Listing graphics (icon, feature graphic, screenshots) are binary and not
committed — see `app/src/main/play/listings/en-US/graphics/README.md`.

### One-time setup (done outside this repo)

These steps require a human with the Play account and cannot be automated here:

1. **Create a Google Play Console developer account** (one-time $25 fee).
2. **Create the app** in the console with package name `com.pdfapp`.
3. **Upload the first AAB manually.** Google Play blocks API uploads until a
   build has been uploaded by hand once. Download the AAB from a Release (or the
   Release workflow's artifacts) and upload it via the console.
4. **Enable Play App Signing** when prompted (recommended).
5. **Create a service account** for API access:
   - In Google Cloud Console, create a service account and a JSON key.
   - In Play Console → *Users and permissions*, invite that service account and
     grant it release permissions.
6. **Complete the required store content** in the console: privacy policy URL
   (host `docs/PRIVACY.md` somewhere public, e.g. GitHub Pages or the raw file
   URL), data safety form (this app collects no data), content rating
   questionnaire, target audience, and listing graphics.

### Play secrets (one-time, in addition to the signing secrets above)

| Secret | Value |
| --- | --- |
| `PLAY_SERVICE_ACCOUNT_JSON` | full contents of the service-account JSON key |

The workflow exposes this as `ANDROID_PUBLISHER_CREDENTIALS`, which Gradle Play
Publisher reads automatically. The JSON is never written to the repo
(`play-service-account.json` is git-ignored).

### Publish

```
Actions → Publish to Google Play → Run workflow
  track:       internal        (start here; promote later)
  versionName: 1.0.0
```

The run builds the signed AAB and uploads it to the chosen track. From there,
promote to production in the Play Console (or via this workflow with
`track: production`) once you're happy with testing.

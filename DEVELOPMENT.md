# PDF-App — Development Guide

Native Android (Kotlin) app: a Google-Drive-style PDF viewer (continuous scroll, document zoom, immersive chrome) plus focused editing — text overlays and a flattened ink signature, saved via SAF. Offline, open-source libs only.

## Roadmap
The full product roadmap — where the app is and where it's going, phase by phase — lives in [`docs/plan.md`](docs/plan.md). Phases 1 (system integration) and 2 (Drive-style reading experience) are shipped; Phase 3 (annotation suite) was removed by product decision, so Phase 4 (forms) onward is next.

## Build & Verify
- Build:   `./gradlew assembleDebug`
- Unit:    `./gradlew testDebugUnitTest`
- Lint:    `./gradlew ktlintCheck detekt lintDebug`
- E2E:     `./gradlew connectedDebugAndroidTest`
- Harness: `./gradlew :file-persistence:testDebugUnitTest --tests "*PdfTestHarness*"`
- Version:  `./gradlew -q :app:appVersion` — what this build would stamp.

## Versioning
`appVersionName` in [`gradle.properties`](gradle.properties) is the single source of truth: the version under development. Builds read it directly, CI reads the same line via [`.github/actions/app-version`](.github/actions/app-version/action.yml), and a `vX.Y.Z` tag must agree with it or the release fails. Nobody edits it by hand — the `release` job commits the next patch (`1.4.1` → `1.4.2`) to `main` once a release publishes, so releasing is just "tag the version main is on". CI adds `-PappBuildNumber=<commit count>`, which becomes both the `versionCode` and the `(build N)` suffix, so a rolling build reads `1.4.1 (build 102)` — never a commit hash. Signet shows the result on its home screen. Release steps: [`docs/RELEASING.md`](docs/RELEASING.md#versioning).

## Stack
PdfRenderer (view) · Canvas (overlay) · PdfBox-Android / Tom Roush (write) · SAF (storage).

## Modules
- `:core-renderer` — load + render PDF pages; owns the shared `CoordinateMapper`.
- `:overlay-engine` — text + ink-signature overlay models and the interactive canvas view.
- `:file-persistence` — flatten overlays into the PDF, decrypt password-protected files, save via SAF.
- `:app` — UI shell wiring the three modules; calls `PDFBoxResourceLoader.init` at startup.

### `:app` source layout
One package per feature area, so a file's directory says which part of the app it
belongs to:

| Package | Holds |
| --- | --- |
| `com.pdfapp` | `MainActivity`, `PdfApplication`, incoming-intent parsing. |
| `…ui` | The shell composable `PdfEditorScreen` and the cross-cutting `ViewerMode` / `ZoomPreset`. |
| `…ui.common` | Shared UI rules: touch-target and dynamic-type sizing, screen-reader wording, the running build's version label. |
| `…ui.document` | State for the open document — `PdfEditorViewModel`, `DocumentSession`, the search and selection controllers. |
| `…ui.home` | Landing screen: the picker button and the recents list. |
| `…ui.reader` | READ mode — the continuous reader plus its chrome, adaptive-layout, zoom, prefetch and back rules. |
| `…ui.editor` | EDIT mode — the canvas scaffold, the tool bar and the text-entry dialog. |
| `…ui.theme` | Material 3 colour scheme and theme. |
| `…data` | DataStore-backed recents and viewer preferences. |

Dependencies inside `:app` flow one direction: the `ui` shell → feature packages
(`ui.reader`, `ui.editor`, `ui.home`) → `ui.common` / `ui.document` → `data`.
`ui.common` never imports a feature package.

`src/test` and `src/androidTest` mirror those packages, so a test sits beside
what it covers; whole-app device flows live in `com.pdfapp.e2e`.

## Style
- Kotlin official style; explicit types on public APIs.
- One responsibility per file; keep files small (< ~400 lines).
- Modules isolated: `:app`→features only; features share types via `:core-renderer/model`.
- All PDF/IO work off the main thread (coroutines + Dispatchers.IO).
- Coordinates stored in PDF points; convert only at the View boundary via CoordinateMapper.

## Never Do
- Never try to save/edit with PdfRenderer — it is read-only.
- Never block the main thread with render/flatten/save.
- Never hardcode file paths or request broad storage perms — use SAF only.
- Never add commercial SDKs (PSPDFKit/Syncfusion) or telemetry.
- Never commit secrets, keystores, or signed APKs.

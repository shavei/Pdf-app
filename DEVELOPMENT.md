# PDF-App — Development Guide

Native Android (Kotlin) app: a Google-Drive-style PDF viewer (continuous scroll, document zoom, immersive chrome) plus focused editing — text overlays and a flattened ink signature, saved via SAF. Offline, open-source libs only.

## Roadmap
The full product roadmap — where the app is and where it's going, phase by phase — lives in [`plan.md`](plan.md). Phases 1 (system integration) and 2 (Drive-style reading experience) are shipped; Phase 3 (annotation suite) was removed by product decision, so Phase 4 (forms) onward is next.

## Build & Verify
- Build:   `./gradlew assembleDebug`
- Unit:    `./gradlew testDebugUnitTest`
- Lint:    `./gradlew ktlintCheck detekt lintDebug`
- E2E:     `./gradlew connectedDebugAndroidTest`
- Harness: `./gradlew :file-persistence:testDebugUnitTest --tests "*PdfTestHarness*"`

## Stack
PdfRenderer (view) · Canvas (overlay) · PdfBox-Android / Tom Roush (write) · SAF (storage).

## Modules
- `:core-renderer` — load + render PDF pages; owns the shared `CoordinateMapper`.
- `:overlay-engine` — text + ink-signature overlay models and the interactive canvas view.
- `:file-persistence` — flatten overlays into the PDF, decrypt password-protected files, save via SAF.
- `:app` — UI shell wiring the three modules; calls `PDFBoxResourceLoader.init` at startup.

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

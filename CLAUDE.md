# PDF-App — Agent Guide

Native Android (Kotlin) app: a Google-Drive-style PDF viewer (continuous scroll, document zoom, immersive chrome) plus focused editing — AcroForm filling, text overlays and a flattened ink signature, saved via SAF. Offline, open-source libs only.

## Start here
- **Roadmap — read before planning or starting any feature:** [`docs/plan.md`](docs/plan.md).
  The phase-by-phase path to a full default PDF app. Phases 1 (system
  integration), 2 (Drive-style reading experience) and 4 (AcroForm fill & sign)
  are **shipped**; Phase 3 (annotation suite) was **removed by product
  decision** — Signet stays a viewer + form-filler + signer, not an annotator.
  Check a phase's status there before assuming work is (un)done.
- **Backlog — loose ends flagged in past PRs and never picked up:** [`docs/backlog.md`](docs/backlog.md).
  Sized S/M/L, each citing where it was flagged and where the code stands. Check
  it before "spotting" something new, and prune an entry when it ships.
- **Development guide — build commands, module map, style, guardrails:** [`DEVELOPMENT.md`](DEVELOPMENT.md).

## Build & Verify
- Build:   `./gradlew assembleDebug`
- Unit:    `./gradlew testDebugUnitTest`
- Lint:    `./gradlew ktlintCheck detekt lintDebug`
- E2E:     `./gradlew connectedDebugAndroidTest`
- Harness: `./gradlew :file-persistence:testDebugUnitTest --tests "*PdfTestHarness*"`

A feature is **Done** only when all three layers (Lint, Unit, E2E/Harness) pass.

## Never Do
- Never try to save/edit with PdfRenderer — it is read-only.
- Never block the main thread with render/flatten/save.
- Never hardcode file paths or request broad storage perms — use SAF only.
- Never add commercial SDKs (PSPDFKit/Syncfusion) or telemetry.
- Never commit secrets, keystores, or signed APKs.

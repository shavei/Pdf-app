# PDF-App
 https://github.com/shavei/Pdf-app/actions/runs/28332466265/artifacts/7937617387
A native Android app for viewing PDFs, adding **text overlays**, and applying a
hand-drawn **ink signature**, then flattening and saving the result back to
storage. Offline-first, open-source libraries only.

## Architecture

A Gradle multi-module project. Dependencies flow one direction: `:app` depends on
the feature modules; feature modules share only the small model/geometry types in
`:core-renderer`.

| Module | Responsibility | Key types |
|---|---|---|
| **`:core-renderer`** | Load a PDF from a SAF `Uri` and render pages to `Bitmap`. Owns the shared coordinate system. | `PdfDocumentSource`, `PageRenderer`, `CoordinateMapper` |
| **`:overlay-engine`** | Interactive text + ink layer captured in PDF-point coordinates. | `TextOverlay`, `InkSignature`, `OverlayLayer`, `OverlayCanvasView` |
| **`:file-persistence`** | Flatten overlays into the PDF with PdfBox-Android and write via SAF. | `PdfFlattener`, `PdfSaver` |
| **`:app`** | UI shell wiring the modules; initializes PdfBox at startup. | `MainActivity` |

### The key technical detail: coordinate mapping

Screen/canvas pixels (origin top-left, y-down, scaled to the rendered bitmap) are
not the same as PDF user-space points (origin bottom-left, y-up, 72 dpi). All
overlay positions are stored in **PDF points** so they are resolution-independent;
conversion happens only at the View boundary via `CoordinateMapper`. This is the
single source of truth and is the most heavily unit-tested class in the project.

## Tech stack

- **View:** `android.graphics.pdf.PdfRenderer` (API 21+)
- **Overlay/Ink:** `android.graphics.Canvas` in a custom `View`
- **Write-back:** [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0) — `PdfRenderer` is read-only and cannot save
- **Storage:** Storage Access Framework (SAF)

## Build & verify

```bash
./gradlew assembleDebug          # build the APK
./gradlew testDebugUnitTest      # unit tests (incl. PDF-Test-Harness)
./gradlew ktlintCheck detekt lintDebug   # static analysis (Lint layer)
./gradlew connectedDebugAndroidTest      # E2E (needs a device/emulator)
```

### Verification stack

A feature is **Done** only when all three layers pass (enforced in CI):

1. **Lint** — `ktlint` + `detekt` + Android `lintDebug`.
2. **Unit** — JUnit + Robolectric, focused on `CoordinateMapper`, overlay models, and `PdfFlattener`.
3. **E2E signature validation** — Espresso; its headless core is the **PDF-Test-Harness** (verifies a flattened text overlay + signature round-trips through save/reload).

### PDF-Test-Harness

`PdfTestHarnessTest` (in `:file-persistence`) is the project smoke test: it
generates a blank A4 PDF in memory, adds a mock `"PDF-TEST-OVERLAY"` text plus a
mock ink signature through the real `PdfFlattener` API, saves, re-opens, and
asserts the text is extractable and the signature strokes were written into the
page content (the ink is flattened as vector strokes). Run it with:

```bash
./gradlew :file-persistence:testDebugUnitTest --tests "*PdfTestHarness*"
```

## Download

- **Releases** — tagged builds (signed APK + AAB) are published on the
  [Releases page](../../releases). Download the `.apk` to sideload on a device.
- **Latest debug build** — every CI run uploads an `app-debug-apk` artifact
  (Actions → pick a run → *Artifacts*) for quick testing.

See [`docs/RELEASING.md`](docs/RELEASING.md) for how to cut a release and the
signing secrets to configure.

## Status

Feature-complete for the core flow: open a PDF, navigate pages, add styled text
and a hand-drawn signature, undo/clear, and save a flattened copy via SAF.
Verification runs as three CI layers (Lint, Unit + PDF-Test-Harness, and an
on-device emulator E2E). Cryptographic/PAdES signing remains a future extension.

## Requirements

- JDK 17+
- Android SDK (compileSdk 35); set `ANDROID_HOME` or add `local.properties` with `sdk.dir`.

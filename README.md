# PDF-App

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

## Status

Step-1 scaffold: project structure, the four modules with their core
implementations, the verification stack, and the PDF-Test-Harness. Interactive UI
polish and the full Espresso E2E flow are layered on next.

## Requirements

- JDK 17+
- Android SDK (compileSdk 35); set `ANDROID_HOME` or add `local.properties` with `sdk.dir`.

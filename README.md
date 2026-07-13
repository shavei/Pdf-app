# PDF-App

**View, sign, and annotate PDFs on Android — fully offline, no ads, no tracking.**

[![CI](https://github.com/shavei/Pdf-app/actions/workflows/ci.yml/badge.svg)](https://github.com/shavei/Pdf-app/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/shavei/Pdf-app)](https://github.com/shavei/Pdf-app/releases/latest)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

A native Android (Kotlin) app for opening a PDF, adding **text overlays**, drawing a
hand-drawn **ink signature**, and saving a flattened copy back to your storage.
Everything runs on-device with open-source libraries only.

## Features

- 📄 **View PDFs** — open any PDF via the system file picker and navigate its pages.
- ✍️ **Sign** — draw your signature with your finger; pick ink color and stroke width.
- 🔤 **Add text** — tap to place text anywhere on the page; choose size and color.
- ✏️ **Edit & move** — drag placed text to reposition it, tap to edit or delete it.
- ↩️ **Undo & clear** — step back a stroke or wipe the page's overlays.
- 💾 **Save a flattened copy** — overlays are baked into a new PDF; your original is untouched.
- 🔒 **Private by design** — works completely offline, no ads, no telemetry, no account.

## Download

Grab the latest `.apk` from the [Releases page](https://github.com/shavei/Pdf-app/releases/latest)
and sideload it on your device (Android 5.0 / API 21 or newer). Every CI run also
uploads an `app-debug-apk` artifact (Actions → pick a run → *Artifacts*) for quick testing.

## Build from source

Requirements: JDK 17+, Android SDK (compileSdk 35) — set `ANDROID_HOME` or add
`local.properties` with `sdk.dir`.

```bash
./gradlew assembleDebug          # build the APK
./gradlew testDebugUnitTest      # unit tests (incl. PDF-Test-Harness)
./gradlew ktlintCheck detekt lintDebug   # static analysis (Lint layer)
./gradlew connectedDebugAndroidTest      # E2E (needs a device/emulator)
```

See [`docs/RELEASING.md`](docs/RELEASING.md) for how to cut a release and the
signing secrets to configure.

## Tech stack

- **View:** `android.graphics.pdf.PdfRenderer` (API 21+)
- **Overlay/Ink:** `android.graphics.Canvas` in a custom `View`
- **Write-back:** [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache-2.0) — `PdfRenderer` is read-only and cannot save
- **Storage:** Storage Access Framework (SAF) — no broad storage permissions

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

## Testing

A feature is **Done** only when all three layers pass (enforced in CI):

1. **Lint** — `ktlint` + `detekt` + Android `lintDebug`.
2. **Unit** — JUnit + Robolectric, focused on `CoordinateMapper`, overlay models, touch handling, and `PdfFlattener`.
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

## Roadmap

The core flow is feature-complete: open a PDF, navigate pages, add styled text
and a hand-drawn signature, move/edit placed text, undo/clear, and save a
flattened copy via SAF. Cryptographic/PAdES signing remains a future extension.

## Contributing

Issues and pull requests are welcome. Before submitting, make sure all three
verification layers pass locally (see [Testing](#testing)); CI enforces them on
every PR.

## License

Licensed under the [Apache License 2.0](LICENSE).

Built with [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) by Tom Roush
(Apache-2.0).

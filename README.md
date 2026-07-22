# Signet

**View and sign PDFs on Android — fully offline, no ads, no tracking.**

### 📲 [**Download the latest APK**](https://github.com/shavei/Pdf-app/releases/latest/download/pdf-app.apk)

<!-- Static badges only: dynamic shields.io badges (release version, CI status)
     query the GitHub API anonymously and always show "repo not found" while
     this repo is private. The build date/commit live in the release itself. -->
[![Download APK](https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/shavei/Pdf-app/releases/latest/download/pdf-app.apk)
[![Latest build](https://img.shields.io/badge/Latest%20build-releases-blue?style=for-the-badge&logo=github)](https://github.com/shavei/Pdf-app/releases/latest)
[![CI](https://img.shields.io/badge/CI-workflow%20runs-555?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com/shavei/Pdf-app/actions/workflows/ci.yml)

> Tap **Download the latest APK** above, then open the file on your Android phone
> to install (you may need to allow "install from unknown sources"). Every push
> to `main` that passes all checks updates this download automatically. You can
> also rebuild on demand from your phone: **Actions → CI → Run workflow**.

A native Android app for reading PDFs — a Google-Drive-style viewer with
continuous vertical scroll, document-level pinch-zoom, immersive tap-to-hide
chrome, search, text selection, an outline drawer, and night mode — and for
signing them with **text overlays** and a hand-drawn **ink signature**, then
flattening and saving the result back to storage. Signet also registers as a
system "Open with" / share target for `application/pdf`. Offline-first,
open-source libraries only.

## Features

### Reading

- 📄 **Open anywhere** — the system file picker, the "Open with" sheet, or the
  share sheet; recently opened files are one tap away on the home screen and
  reopen at the page you left off.
- 📄 **Continuous scroll** — all pages stacked in one continuously scrolling
  column, rendered on demand with recycled bitmaps, plus a fast-scroll handle
  with a "page X / N" bubble and thumbnail-grid / go-to-page jump navigation.
- 🔍 **Pinch to zoom & pan** — document-level focal-anchored zoom: pinch
  anywhere to zoom the whole document about the gesture centroid (it survives
  scrolling across pages), double-tap to toggle fit-width ↔ 2.5×, and pan when
  zoomed in; high-zoom strips are re-rendered crisply as tiles.
- 🖼️ **Immersive reading** — a single tap hides the app bar and system bars for
  full-screen reading, like Drive.
- 🔎 **Search** — find text across the document with match highlighting and
  next/previous navigation.
- 🖱️ **Select & copy** — long-press to select text, drag to extend, copy to
  the clipboard.
- 🔖 **Outline & links** — jump via the document's table of contents; tap
  internal links to navigate and external links to open the browser.
- 🔐 **Password-protected PDFs** — unlock encrypted files with a password
  prompt (decrypted only to a private cache copy, wiped on close).
- 🌙 **Night mode & comfort** — invert the page for dark reading; keep the
  screen awake.

### Signing

- ✍️ **Sign** — draw your signature with your finger; pick ink color and stroke width.
- 🔤 **Add text** — tap to place text anywhere on the page; choose size and color.
- ✏️ **Edit & move** — drag placed text to reposition it, tap to edit or delete it.
- ↩️ **Undo & clear** — step back a stroke or wipe the page's overlays.
- 💾 **Save a flattened copy** — overlays are baked into a new PDF; your original is untouched.

- 🔒 **Private by design** — works completely offline, no ads, no telemetry, no account.

## Architecture

A Gradle multi-module project. Dependencies flow one direction: `:app` depends on
the feature modules; feature modules share only the small model/geometry types in
`:core-renderer`.

| Module | Responsibility | Key types |
|---|---|---|
| **`:core-renderer`** | Load a PDF from a SAF `Uri` and render pages (and tiles) to `Bitmap`; a memory-bounded page cache; a read-only PdfBox facade for text geometry, search, outline and links. Owns the shared coordinate system. | `PdfDocumentSource`, `PageRenderer`, `RenderedPageCache`, `PdfTextDocument`, `CoordinateMapper` |
| **`:overlay-engine`** | Interactive text + ink layer captured in PDF-point coordinates. | `TextOverlay`, `InkSignature`, `OverlayLayer`, `OverlayCanvasView` |
| **`:file-persistence`** | Flatten overlays into the PDF with PdfBox-Android and write via SAF; decrypt password-protected PDFs. | `PdfFlattener`, `PdfSaver`, `PdfDecryptor` |
| **`:app`** | UI shell wiring the modules: the Drive-style continuous-scroll reader (document zoom, immersive chrome, search, selection, outline, night mode, recents) and the overlay editor. Initializes PdfBox at startup. | `MainActivity`, `PdfEditorViewModel`, `ReaderView` |

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
- **Text overlays beyond Latin-1:** bundled [Arimo](https://github.com/googlefonts/Arimo) font (SIL OFL 1.1) embedded as a subset — the built-in Helvetica only covers WinAnsi, so Hebrew/Greek/Cyrillic text falls back to Arimo, with RTL runs reordered to visual order before flattening
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

## Download

- **Latest build** — the [`Latest build` release](../../releases/latest) is
  refreshed automatically on every push to `main` that passes all checks. The
  [Download APK](https://github.com/shavei/Pdf-app/releases/latest/download/pdf-app.apk)
  link above is a permalink that always points at the newest one — one tap, no
  login required.
- **Tagged releases** — versioned builds (signed APK + AAB) are published on the
  [Releases page](../../releases) when a `vX.Y.Z` tag is pushed. See
  [`CHANGELOG.md`](CHANGELOG.md) for what changed in each version.
- **Build on demand** — trigger a fresh build from anywhere (including the GitHub
  mobile app): **Actions → CI → *Run workflow***.

See [`docs/RELEASING.md`](docs/RELEASING.md) for how to cut a release and the
signing secrets to configure.

## Status

**Phase 1 (system integration) and Phase 2 (table-stakes reading) are done.**
Signet opens PDFs from the picker, the "Open with" sheet, and the share sheet;
reads them in a Google-Drive-style continuous scroll with document-level
pinch-zoom, immersive tap-to-hide chrome, in-document search, text selection,
an outline drawer, tappable links, password unlocking, night mode, and a
recents list that resumes the last-read page; and signs, adds and edits text
overlays, and saves a flattened copy via SAF. Verification runs as three CI
layers (Lint, Unit + PDF-Test-Harness, and an on-device emulator E2E).

Signet is a focused **viewer + signer** — the Phase 3 annotation suite was
removed by product decision (see [`plan.md`](plan.md)). Next up — the remaining
roadmap toward a full default PDF app, detailed phase by phase there:

4. **Forms** — AcroForm fill & save.
5. **Page tools** — reorder/rotate/delete pages, merge/split, print, share out.
6. **Create & secure** — images/scan to PDF, password protection, and
   cryptographic/PAdES signing as the long-term extension.

## Requirements

- JDK 17+
- Android SDK (compileSdk 35); set `ANDROID_HOME` or add `local.properties` with `sdk.dir`.

## License

Licensed under the [Apache License 2.0](LICENSE). The app bundles
[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), which is also
licensed under Apache-2.0, and the [Arimo](https://github.com/googlefonts/Arimo)
font, licensed under the [SIL Open Font License 1.1](file-persistence/src/main/assets/fonts/Arimo-OFL.txt).

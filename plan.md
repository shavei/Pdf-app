# Plan: Signet as the everything-PDF app

Signet today is a focused PDF **editor**: open a PDF via its own file picker, view
and zoom pages, add text overlays and an ink signature, and save a flattened copy.
This plan maps the road from that editor to a **full-fledged default PDF app** — the
one you pick "Always" for in Android's "Open with" sheet and never need to leave.

The gap analysis below is based on what the category leaders on Android ship
(Adobe Acrobat Reader, Foxit, Xodo, Drive PDF Viewer, MuPDF) and what users
expect from a device-default viewer.

## Where we are vs. where we're going

| Area | Have today | Missing for "default app" status |
|---|---|---|
| Getting PDFs in | Own SAF file picker, **"Open with" / share-target intents ✅, recent files ✅** | — |
| Viewing | **Drive-style continuous vertical scroll ✅, document-level pinch/double-tap zoom ✅, immersive tap-to-hide chrome ✅, fast-scroll handle with page bubble ✅, thumbnails ✅, go-to-page ✅, outline/TOC ✅, night mode ✅, text selection ✅, search ✅, password-protected files ✅** | — |
| Editing | Text overlay, ink signature, undo, save flattened copy | *(annotation suite intentionally out of scope — see Phase 3)* |
| Forms | — | AcroForm fill & save |
| Organizing | — | Reorder/rotate/delete pages, merge/split, extract |
| Output | Save flattened copy via SAF | Print, share out, compress |
| Security | — | Open encrypted PDFs; add/remove password; PAdES signing (long-term) |

Phases are ordered by user impact for a default viewer: integration first (be
reachable), then reading (most sessions are read-only), then the editing suite
that differentiates us.

---

## Phase 1 — System integration: "Open with" support ✅ *shipped*

**Status:** delivered. `MainActivity` now declares `VIEW` (`content`/`file`
`application/pdf`) and `SEND` intent filters with the `DEFAULT` category, so
Signet appears in the "Open with" and share sheets and can be set as the
**Always** default PDF app. Incoming intents are parsed by a unit-testable
`Intent.pdfUri()` helper (`IncomingIntent.kt`) and routed into
`PdfEditorScreen(initialUri = …)`. `PdfEditorViewModel.open()` takes the
persistable URI grant best-effort (`runCatching`), so an intent-delivered URI
that carries only a temporary grant no longer crashes the open coroutine, and
open failures fall back to the snackbar/pick-a-PDF screen. Verified across all
three layers: manifest lint, unit tests (`IncomingIntentTest`,
`PdfEditorViewModelOpenTest`), and an Espresso E2E launch
(`OpenWithIntentTest`). The design detail below is retained for reference.

### Goal

When the user taps a PDF in a file manager, browser download, email attachment, or
WhatsApp, Android shows an "Open with" sheet (Reader, Drive PDF Viewer, ChatGPT, …)
— and Signet is not in it. After this phase, Signet appears in that sheet (and can
be chosen as the **Always** default PDF app), and also shows up in the **share
sheet** when another app shares a PDF.

### Why it doesn't show up today

`app/src/main/AndroidManifest.xml` declares only the `MAIN`/`LAUNCHER` intent filter
on `MainActivity`. Android builds the "Open with" list from activities whose intent
filters match `ACTION_VIEW` + `application/pdf`; we declare no such filter, so the
resolver never considers us.

### 1.1 Manifest: declare the intent filters (`:app`)

Add to `MainActivity` in `app/src/main/AndroidManifest.xml`:

```xml
<!-- Tapped PDF: file managers, downloads, mail attachments -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="content" android:mimeType="application/pdf" />
    <data android:scheme="file" android:mimeType="application/pdf" />
</intent-filter>

<!-- Shared PDF: system share sheet ("Share → Signet") -->
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="application/pdf" />
</intent-filter>
```

Notes:
- `DEFAULT` category is what lets the user pick **Always** and make us the system
  default PDF app.
- `BROWSABLE` lets browsers hand off downloaded/linked PDFs.
- `file://` scheme still matters for older file managers (pre-FileProvider apps);
  the existing `PdfDocumentSource.fromUri(contentResolver, uri)` already goes
  through `ContentResolver`, which handles both schemes.
- Keep `launchMode` default for now; a second VIEW intent simply creates a new task
  entry. If that feels wrong in testing, consider `singleTask` + `onNewIntent`
  (1.3 already routes through one code path, so the switch is cheap).

### 1.2 Fix `takePersistableUriPermission` (**required, currently a crash**)

`PdfEditorViewModel.open()` unconditionally calls
`contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`.
That only succeeds for URIs obtained via SAF (`OpenDocument`). A URI delivered by a
`VIEW`/`SEND` intent carries a **temporary, non-persistable** grant — the call throws
`SecurityException` and the coroutine dies before the PDF loads.

Fix: attempt the persistable grant best-effort, never letting it abort opening:

```kotlin
runCatching {
    context.contentResolver.takePersistableUriPermission(
        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
} // best-effort: intent-delivered URIs only carry a temporary grant
```

Consequence to accept: for intent-delivered PDFs the read grant lives only as long
as the task, which is fine because we load pages on demand from an open
`ParcelFileDescriptor` and "Save" writes to a *new* SAF destination anyway.

### 1.3 Route the incoming intent to the ViewModel

`MainActivity` currently ignores its `Intent`. Add extraction:

```kotlin
private fun Intent.pdfUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND ->
        IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
    else -> null
}
```

Pass the result into `PdfEditorScreen(initialUri = intent.pdfUri())`; the screen
calls `viewModel.open(context, it)` once (guard with a ViewModel flag so rotation
doesn't re-open and blow away in-progress overlays). Keep the extraction logic in a
small helper (e.g. `IncomingIntent.kt`) so it is unit-testable without an emulator.

### 1.4 Graceful failure

If the URI can't be opened (revoked grant, corrupt file, mime-lied non-PDF), surface
the existing snackbar (`userMessage`) instead of crashing, and leave the user on the
normal "pick a PDF" screen.

### Testing (three-layer definition of Done)

1. **Lint** — manifest merger + `lintDebug` validate the intent filters.
2. **Unit** — intent-extraction helper (VIEW with data, SEND with `EXTRA_STREAM`,
   wrong action ⇒ null); Robolectric test that `open()` survives a non-persistable
   URI.
3. **E2E** — Espresso launch of `MainActivity` with an `ACTION_VIEW` intent via
   `ActivityScenario.launch(intent)`; manual smoke: tap a PDF in Files → chooser
   lists Signet → **Always** works → sign → save.

---

## Phase 2 — Table-stakes reading experience ✅ *shipped*

Most sessions with a default PDF app are *read-only*. These are the features every
competitor ships and users notice within the first minute.

**Status:** delivered, and reworked in July 2026 into a **Google-Drive-style
viewer** (the overlay editor stays behind an EDIT action). Building blocks:
`RenderedPageCache` (memory-bounded, serialized rendering + high-zoom tiles) and
`PdfTextDocument` (read-only PdfBox facade for text geometry, search, outline and
links) in `:core-renderer`; `PdfDecryptor` in `:file-persistence`; and a
DataStore-backed recents/preferences layer in `:app`. Each sub-item's status is
marked inline below.

### 2.1 Drive-style reading surface ✅ *(reworked from the original pager)*
- **Continuous vertical scroll**: all pages stacked in one `LazyColumn`
  (render on demand, recycle bitmaps) — no page-flip pager.
- **Document-level zoom**: pinch anywhere zooms the whole document about the
  gesture centroid and survives scrolling across pages; double-tap toggles
  fit-width ↔ 2.5×; a plain horizontal scroll pans when zoomed in. Crisp
  high-zoom strips follow a debounced settled scale, and only the strips
  visible in the viewport are rasterised, so quality (up to 8×) never trades
  off against memory.
- **Immersive chrome**: a single tap hides/shows the app bar and system bars,
  like Drive's full-screen reading mode.
- **Fast-scroll handle** on the right edge with a transient "page X / N"
  bubble; drag it to fling through long documents. Page thumbnails grid and a
  "go to page" dialog (with slider) remain for jump navigation.
- Remember last-read page per document (DataStore keyed by URI).

### 2.2 Text search ✅
- In-document search with match highlighting and next/previous navigation.
- Engine: PdfBox-Android's `PDFTextStripper` subclassed to capture glyph positions
  (`TextPosition`) per page → rectangles in PDF points → reuse `CoordinateMapper`
  to draw highlight quads over the rendered bitmap. Index lazily per page on
  `Dispatchers.IO`; cache per document.

### 2.3 Text selection & copy ✅
- Long-press to select rendered text, drag handles, copy to clipboard.
- Same `TextPosition` data as search; selection rectangles snap to word/line boxes.

### 2.4 Outline (table of contents) & link taps ✅
- Read `PDDocumentOutline` from PdfBox → bookmark drawer; tapping jumps to the page.
- Handle internal link annotations (`PDAnnotationLink` with go-to actions) as taps;
  external `http(s)` links open the browser via `Intent.ACTION_VIEW`.

### 2.5 Password-protected PDFs ✅
- `android.graphics.pdf.PdfRenderer` cannot open encrypted files. Flow: catch the
  renderer's `SecurityException` → password dialog → open with
  `PDDocument.load(stream, password)` → save a decrypted copy to app-private cache
  → render that. Wipe the cache copy when the document closes.

### 2.6 Night mode & reading comfort ✅
- Dark *page* rendering (the UI already has dark theme): invert rendered bitmaps via
  `ColorMatrix` (`-1` scale + offset), toggle in the top bar; persists per app.
- Keep-screen-on toggle; fit-width vs fit-page zoom presets.

### 2.7 Recent files ✅
- Home screen shows recently opened documents (name, page count, last-read page,
  thumbnail). Store SAF URIs — we already take persistable permissions for
  picker-opened files; intent-delivered ones appear only while their grant lives.

### 2.8 Performance guardrails ✅ *(tile rendering + LRU cache)*
- Tile-based rendering at high zoom (render only the visible rect at scale instead
  of one huge bitmap) — `PdfRenderer.Page.render` accepts a transform matrix, so
  this fits the existing `PageRenderer`.
- Bitmap pool + LRU cache sized off `ActivityManager.getMemoryClass()`; target:
  smooth on 1000-page documents.

---

## Phase 3 — Annotation suite ❌ *removed (July 2026 product decision)*

The annotation suite is **out of scope**: Signet stays a *viewer + signer*, not
an annotator. The previously shipped SHAPE tool (rectangle/ellipse/line/arrow)
was removed along with its models, flattening and tests. What remains — by
design — is the focused **editing** feature set: text overlays, the ink
signature, undo, and "save flattened copy". Text markup, sticky notes,
highlighter, eraser/redo, saved signatures and image stamps are explicitly not
planned; if that decision is ever revisited, the git history of the shape tool
is the reference implementation.

## Phase 4 — Forms (AcroForm fill & sign)

The single biggest functional gap vs. Acrobat/Foxit for a default app.

- Detect `PDAcroForm` on open; render field widgets (text fields, checkboxes,
  radio groups, dropdowns) as native Compose inputs positioned via
  `CoordinateMapper`.
- Write values back with PdfBox (`PDField.setValue`), offer "Save" (fields stay
  editable) and "Save flattened" (`PDAcroForm.flatten()`).
- XFA forms are explicitly out of scope (Acrobat-proprietary, dying format).

## Phase 5 — Page organization & document tools

All buildable on PdfBox; each is a small headless operation + a picker UI.

- **Organize pages**: thumbnail grid with drag-to-reorder, rotate, delete;
  `PDDocument` page-tree manipulation, save as copy.
- **Merge** multiple PDFs (`PDFMergerUtility`) and **split** / **extract pages**
  to a new file.
- **Compress**: re-encode images at lower DPI/quality.
- **Print**: `PrintManager` + a `PrintDocumentAdapter` that streams the current
  (flattened) PDF bytes in `onWrite` — Android's print framework accepts PDF
  natively, so this is cheap and expected of a default viewer.
- **Share out**: `ACTION_SEND` the current/flattened copy via `FileProvider`.

## Phase 6 — Create & secure

- **Images → PDF** (gallery multi-select → one page per image).
- **Scan to PDF** with the device camera (edge detection via ML Kit document
  scanner API; keeps the no-cloud promise — on-device only).
- **Add/remove password**: PdfBox `StandardProtectionPolicy` (AES-256) on save.
- **Cryptographic/PAdES signing** — the long-standing roadmap item; PdfBox
  supports signature containers, key storage via Android Keystore.

## Cross-cutting platform polish

- **App shortcuts** (static: "Open last document", "Pick a PDF").
- **Predictive back, themed icon, per-app language** — modern-Android hygiene.
- **Tablets/foldables**: two-page spread layout, drag-and-drop a PDF onto the app.
- **Accessibility**: expose extracted page text (from 2.2/2.3) to TalkBack;
  content descriptions on all tools; large-touch-target mode.
- **Privacy stance stays**: everything on-device, no telemetry — this is Signet's
  differentiator vs. Acrobat/Xodo, whose headline features increasingly require
  cloud accounts and AI upsells.

## Suggested module mapping

| Work | Module |
|---|---|
| Intents, recent files, reader UI, print, share | `:app` |
| Text extraction/search/selection geometry, tiles | `:core-renderer` |
| Text overlay + ink signature models and canvas | `:overlay-engine` |
| Forms write-back, page ops, merge/split, encrypt | `:file-persistence` |

## Sequencing & definition of done

1. **Phase 1** shipped first and alone (small, unblocked "default app" status).
2. **Phase 2** shipped next and was reworked into the Drive-style viewer.
3. Phase 3 is removed; Phases 4–6 can proceed feature-by-feature; each feature
   is independently shippable and must pass the project's three verification
   layers (Lint, Unit, E2E/PDF-Test-Harness) before merge, per `README.md`.

## Competitive research sources

- [TechRadar: Best PDF reader for Android](https://www.techradar.com/best/best-pdf-reader-android)
- [Adobe Acrobat Reader for Android — features](https://www.adobe.com/acrobat/mobile/acrobat-reader.html) and [Fill & Sign docs](https://www.adobe.com/devnet-docs/acrobat/android/en/forms.html)
- [Android Developers: building a PDF viewer](https://developer.android.com/media/grow/pdf-viewer)
- [Adamsdesk: open-source PDF readers (MuPDF et al.)](https://www.adamsdesk.com/posts/free-open-source-pdf-reader/)
- [RankRed: Best Android PDF reader apps](https://www.rankred.com/best-android-pdf-reader-apps/)

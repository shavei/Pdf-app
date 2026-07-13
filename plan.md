# Plan: Become the system's PDF handler ("Open with" support)

## Goal

Today the app can only open a PDF through its **own** file picker
(`ActivityResultContracts.OpenDocument` in `PdfEditorScreen`). When the user taps a
PDF in a file manager, browser download, email attachment, or WhatsApp, Android shows
an "Open with" sheet (Reader, Drive PDF Viewer, ChatGPT, …) — and Signet is not in it.

After this work, Signet appears in that sheet (and can be chosen as the **Always**
default PDF app), and also shows up in the **share sheet** when another app shares a
PDF. That makes it the one-stop PDF app: view, sign, annotate, save — regardless of
where the PDF came from.

## Why it doesn't show up today

`app/src/main/AndroidManifest.xml` declares only the `MAIN`/`LAUNCHER` intent filter
on `MainActivity`. Android builds the "Open with" list from activities whose intent
filters match `ACTION_VIEW` + `application/pdf`; we declare no such filter, so the
resolver never considers us.

## Changes

### 1. Manifest: declare the intent filters (`:app`)

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
  reading it needs no permission on API 21–28 targets via SAF-style streams, and the
  existing `PdfDocumentSource.fromUri(contentResolver, uri)` already goes through
  `ContentResolver`, which handles both schemes.
- Keep `launchMode` default for now; a second VIEW intent simply creates a new task
  entry. If that feels wrong in testing, consider `singleTask` + `onNewIntent`
  (step 3 already routes through one code path, so the switch is cheap).

### 2. Fix `takePersistableUriPermission` (`:app` — **required, currently a crash**)

`PdfEditorViewModel.open()` unconditionally calls
`contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`.
That only succeeds for URIs obtained via SAF (`OpenDocument`). A URI delivered by a
`VIEW`/`SEND` intent carries a **temporary, non-persistable** grant — the call throws
`SecurityException` and the coroutine dies before the PDF loads.

Fix: attempt the persistable grant only when the intent grants it, and never let it
abort opening:

```kotlin
runCatching {
    context.contentResolver.takePersistableUriPermission(
        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
} // best-effort: intent-delivered URIs only carry a temporary grant
```

Consequence to accept: for intent-delivered PDFs the read grant lives only as long as
the task, which is fine because we load pages on demand from an open
`ParcelFileDescriptor` and "Save" writes to a *new* SAF destination anyway.

### 3. Route the incoming intent to the ViewModel (`:app`)

`MainActivity` currently ignores its `Intent`. Add extraction:

```kotlin
private fun Intent.pdfUri(): Uri? = when (action) {
    Intent.ACTION_VIEW -> data
    Intent.ACTION_SEND ->
        IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
    else -> null
}
```

Pass the result into `PdfEditorScreen(initialUri = intent.pdfUri())`; the screen calls
`viewModel.open(context, it)` once (guard with `rememberSaveable`/ViewModel flag so
rotation doesn't re-open and blow away in-progress overlays). Keep the extraction
logic in a small pure-ish helper (e.g. `IncomingIntent.kt`) so it is unit-testable
without an emulator.

Style guardrails (per CLAUDE.md): PDF loading stays on `Dispatchers.IO` (already the
case in `open()`); no new file paths or storage permissions — the granted `content://`
URI **is** the SAF-compatible handle.

### 4. Graceful failure

If the URI can't be opened (revoked grant, corrupt file, mime-lied non-PDF), surface
the existing snackbar (`userMessage`) instead of crashing, and leave the user on the
normal "pick a PDF" screen. `PdfDocumentSource.fromUri` failures should map to a
user-readable message.

## Testing

Per the project's three-layer definition of Done:

1. **Lint** — `./gradlew ktlintCheck detekt lintDebug` (manifest merger + lint will
   also validate the intent filters).
2. **Unit** — new tests for the intent-extraction helper (VIEW with data, SEND with
   `EXTRA_STREAM`, wrong action, missing URI ⇒ null) and a Robolectric test that
   `open()` survives a non-persistable URI (mock resolver throwing
   `SecurityException`).
3. **E2E** — Espresso test launching `MainActivity` with an `ACTION_VIEW` intent
   pointing at a `content://` URI served from test assets (androidx-test's
   `ActivityScenario.launch(intent)`), asserting the page renders. Manual smoke on a
   device: tap a PDF in Files/a browser download → chooser lists Signet → **Always**
   works → sign → save.

## Out of scope (deliberately)

- Being a PDF *share target for editing in place* (would need write-back to the
  source URI; our model is "save a flattened copy").
- `ACTION_SEND_MULTIPLE`, print services, or opening password-protected PDFs.
- App-links/deep links to `http(s)` PDF URLs — the browser downloads first, then
  fires `VIEW` with a `content://` URI, which the plan already handles.

## Order of work

1. Manifest intent filters (step 1) — smallest diff, makes us appear in the chooser.
2. Permission fix (step 2) — must land in the same PR or the chooser path crashes.
3. Intent routing (step 3) + failure handling (step 4).
4. Tests, README already updated alongside this plan.

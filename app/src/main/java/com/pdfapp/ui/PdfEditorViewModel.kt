package com.pdfapp.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdfapp.core.renderer.PdfDocumentSource
import com.pdfapp.core.renderer.PdfPasswordRequiredException
import com.pdfapp.core.renderer.RenderedPage
import com.pdfapp.core.renderer.model.PageSize
import com.pdfapp.core.renderer.text.OutlineEntry
import com.pdfapp.core.renderer.text.PdfTextDocument
import com.pdfapp.data.RecentFile
import com.pdfapp.data.RecentFilesStore
import com.pdfapp.data.ViewerPrefsStore
import com.pdfapp.data.readerDataStore
import com.pdfapp.overlay.model.InkSignature
import com.pdfapp.overlay.model.OverlayDocument
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay
import com.pdfapp.persistence.PdfDecryptor
import com.pdfapp.persistence.PdfFlattener
import com.pdfapp.persistence.PdfSaver
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

/** UI state for viewing (one-page-at-a-time reader) and editing a multi-page PDF. */
class PdfEditorViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val recentsStore = RecentFilesStore(application.readerDataStore)
    private val prefsStore = ViewerPrefsStore(application.readerDataStore)

    /** Recently opened documents for the home screen, newest first. */
    val recents: StateFlow<List<RecentFile>> =
        recentsStore.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var nightMode: Boolean by mutableStateOf(false)
        private set
    var keepScreenOn: Boolean by mutableStateOf(false)
        private set

    var session: DocumentSession? by mutableStateOf(null)
        private set
    var mode: ViewerMode by mutableStateOf(ViewerMode.READ)
        private set

    /** One-shot page the reader should scroll to (search/outline/go-to). */
    var pendingReadTarget: Int? by mutableStateOf(null)
        private set

    /** One-shot zoom preset the reader should apply. */
    var pendingZoomPreset: ZoomPreset? by mutableStateOf(null)
        private set

    var currentPageIndex: Int by mutableStateOf(0)
        private set

    /** Placeholder page size until a page's real size is known. */
    var defaultPageSize: PageSize by mutableStateOf(PageSize(LETTER_WIDTH_PT, LETTER_HEIGHT_PT))
        private set

    /** Document outline; null until [loadOutline] finishes. */
    var outline: List<OutlineEntry>? by mutableStateOf(null)
        private set

    val searchController = SearchController(viewModelScope, { session }, ::goToPage)
    val selectionController = SelectionController(viewModelScope, { session })

    /** Set when an encrypted PDF waits for its password. */
    var passwordRequestUri: Uri? by mutableStateOf(null)
        private set
    var wrongPassword: Boolean by mutableStateOf(false)
        private set

    // ---- edit-mode state (single-page overlay editor) ----
    var renderedPage: RenderedPage? by mutableStateOf(null)
        private set
    var overlayDocument: OverlayDocument? by mutableStateOf(null)
        private set
    var busy: Boolean by mutableStateOf(false)
        private set

    /** One-shot message for the snackbar; cleared via [messageShown]. */
    var userMessage: String? by mutableStateOf(null)
        private set

    var inkColorArgb: Int by mutableStateOf(InkSignature.DEFAULT_COLOR)
        private set
    var inkStrokeWidthPt: Float by mutableStateOf(InkSignature.DEFAULT_STROKE_WIDTH_PT)
        private set
    var textColorArgb: Int by mutableStateOf(TextOverlay.DEFAULT_COLOR)
        private set
    var textSizePt: Float by mutableStateOf(TextOverlay.DEFAULT_FONT_SIZE_PT)
        private set

    val pageCount: Int get() = session?.pageCount ?: 0
    val canGoPrevious: Boolean get() = currentPageIndex > 0
    val canGoNext: Boolean get() = currentPageIndex < pageCount - 1

    private var initialUriConsumed = false
    private var persistPageJob: Job? = null

    // Whether the last open() secured a persistable grant: only such
    // documents can be reopened later, so only they join the recents list.
    private var grantPersisted = false

    init {
        viewModelScope.launch { prefsStore.nightMode.collect { nightMode = it } }
        viewModelScope.launch { prefsStore.keepScreenOn.collect { keepScreenOn = it } }
    }

    /**
     * Open the PDF [uri] delivered by the launching intent ("Open with" /
     * share sheet), at most once per ViewModel — recompositions after
     * rotation must not re-open and blow away in-progress overlays.
     */
    fun openInitial(
        context: Context,
        uri: Uri,
    ) {
        if (initialUriConsumed) return
        initialUriConsumed = true
        open(context, uri)
    }

    /** Open a PDF from a SAF or intent-delivered [uri]. */
    fun open(
        context: Context,
        uri: Uri,
    ) {
        launchBusy {
            grantPersisted =
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.isSuccess // best-effort: intent-delivered URIs carry only a temporary grant
            closeSession()
            val opened =
                try {
                    withContext(Dispatchers.IO) {
                        PdfDocumentSource.fromUri(context.contentResolver, uri)
                    }
                } catch (_: PdfPasswordRequiredException) {
                    passwordRequestUri = uri
                    wrongPassword = false
                    return@launchBusy
                }
            finishOpen(context, uri, opened, decryptedFile = null)
        }
    }

    /** Try [password] against the encrypted PDF pending in [passwordRequestUri]. */
    fun submitPassword(
        context: Context,
        password: String,
    ) {
        val uri = passwordRequestUri ?: return
        launchBusy {
            val result =
                withContext(Dispatchers.IO) {
                    val destination = File.createTempFile("decrypted", ".pdf", context.cacheDir)
                    val input =
                        context.contentResolver.openInputStream(uri)
                            ?: error("Unable to open source PDF")
                    input.use { PdfDecryptor().decryptToFile(it, password, destination) }
                }
            when (result) {
                is PdfDecryptor.Result.WrongPassword -> wrongPassword = true
                is PdfDecryptor.Result.Unlocked -> {
                    val opened =
                        withContext(Dispatchers.IO) {
                            PdfDocumentSource.fromUri(context.contentResolver, Uri.fromFile(result.file))
                        }
                    passwordRequestUri = null
                    wrongPassword = false
                    finishOpen(context, uri, opened, result.file)
                }
            }
        }
    }

    fun cancelPasswordRequest() {
        passwordRequestUri = null
        wrongPassword = false
    }

    /** Navigate the reader to [index] (from search, outline, thumbnails, links). */
    fun goToPage(index: Int) {
        if (pageCount == 0) return
        val target = index.coerceIn(0, pageCount - 1)
        currentPageIndex = target
        pendingReadTarget = target
        mode = ViewerMode.READ
    }

    fun readTargetConsumed() {
        pendingReadTarget = null
    }

    fun requestZoomPreset(preset: ZoomPreset) {
        pendingZoomPreset = preset
    }

    fun zoomPresetConsumed() {
        pendingZoomPreset = null
    }

    /** Reader scrolled: track and (debounced) persist the last-read page. */
    fun onVisiblePageChanged(index: Int) {
        if (mode != ViewerMode.READ) return
        currentPageIndex = index
        val uri = session?.uri?.toString() ?: return
        persistPageJob?.cancel()
        persistPageJob =
            viewModelScope.launch {
                delay(PERSIST_PAGE_DEBOUNCE_MS)
                recentsStore.updateLastPage(uri, index)
            }
    }

    fun enterEditMode() {
        if (session == null) return
        selectionController.clear()
        mode = ViewerMode.EDIT
        renderEditPage(currentPageIndex)
    }

    fun exitEditMode() {
        goToPage(currentPageIndex)
    }

    fun nextPage() = renderEditPage(currentPageIndex + 1)

    fun previousPage() = renderEditPage(currentPageIndex - 1)

    /** Load the outline once per document (idempotent). */
    fun loadOutline() {
        if (outline != null) return
        val active = session ?: return
        viewModelScope.launch {
            outline = withContext(Dispatchers.IO) { active.textDocument().outline() }
        }
    }

    fun toggleNightMode() {
        viewModelScope.launch { prefsStore.setNightMode(!nightMode) }
    }

    fun toggleKeepScreenOn() {
        viewModelScope.launch { prefsStore.setKeepScreenOn(!keepScreenOn) }
    }

    /** Persist the [layer] edited on the current page. */
    fun updateCurrentLayer(layer: OverlayLayer) {
        val document = overlayDocument ?: return
        overlayDocument = document.withLayer(layer)
    }

    fun setInkColor(argb: Int) {
        inkColorArgb = argb
    }

    fun setInkStrokeWidth(widthPt: Float) {
        inkStrokeWidthPt = widthPt.coerceIn(MIN_STROKE_PT, MAX_STROKE_PT)
    }

    fun setTextColor(argb: Int) {
        textColorArgb = argb
    }

    fun setTextSize(sizePt: Float) {
        textSizePt = sizePt.coerceIn(MIN_TEXT_PT, MAX_TEXT_PT)
    }

    /** Flatten every page's overlays into a fresh copy of the source PDF and save to [destUri]. */
    fun save(
        context: Context,
        destUri: Uri,
    ) {
        val active = session
        val document = overlayDocument
        if (active == null || document == null) {
            userMessage = "Nothing to save yet"
            return
        }
        if (!document.hasOverlays) {
            userMessage = "Add text or a signature first"
            return
        }
        launchBusy {
            withContext(Dispatchers.IO) {
                active.openInputStream(context.contentResolver).use { stream ->
                    PDDocument.load(stream).use { pdf ->
                        val flattener = PdfFlattener(context)
                        document.nonEmptyLayers.forEach { flattener.flattenInto(pdf, it) }
                        PdfSaver().saveToUri(context.contentResolver, destUri, pdf)
                    }
                }
            }
            userMessage = "Saved signed PDF"
        }
    }

    fun messageShown() {
        userMessage = null
    }

    private suspend fun finishOpen(
        context: Context,
        uri: Uri,
        opened: PdfDocumentSource,
        decryptedFile: File?,
    ) {
        val appContext = context.applicationContext
        val name = withContext(Dispatchers.IO) { displayNameOf(appContext, uri) }
        val newSession =
            DocumentSession(
                uri = uri,
                displayName = name,
                source = opened,
                openTextDocument = { loadTextDocument(appContext, uri, decryptedFile) },
                decryptedFile = decryptedFile,
            )
        session = newSession
        overlayDocument = OverlayDocument(pageCount = opened.pageCount)
        val lastPage =
            recentsStore.recents
                .first()
                .firstOrNull { it.uri == uri.toString() }
                ?.lastPageIndex
                ?.coerceIn(0, opened.pageCount - 1) ?: 0
        currentPageIndex = lastPage
        mode = ViewerMode.READ
        defaultPageSize = newSession.cache.pageSize(0)
        if (grantPersisted) {
            // Without a persistable grant the URI dies with this task, so a
            // recents entry could never be reopened.
            recentsStore.recordOpen(
                RecentFile(
                    uri = uri.toString(),
                    displayName = name,
                    pageCount = opened.pageCount,
                    lastPageIndex = lastPage,
                    lastOpenedEpochMillis = System.currentTimeMillis(),
                    thumbnailPath = saveThumbnail(appContext, uri, newSession),
                ),
            )
        }
        userMessage = "Loaded ${opened.pageCount}-page PDF"
    }

    /** Best-effort page-1 PNG for the recents list; null when anything fails. */
    private suspend fun saveThumbnail(
        context: Context,
        uri: Uri,
        newSession: DocumentSession,
    ): String? =
        runCatching {
            val size = newSession.cache.pageSize(0)
            val rendered = newSession.cache.page(0, THUMBNAIL_WIDTH_PX / size.widthPt)
            withContext(Dispatchers.IO) {
                val directory = File(context.filesDir, "thumbnails").apply { mkdirs() }
                val file = File(directory, "${uri.toString().hashCode().toUInt()}.png")
                file.outputStream().use { out ->
                    rendered.bitmap.compress(Bitmap.CompressFormat.PNG, THUMBNAIL_PNG_QUALITY, out)
                }
                file.absolutePath
            }
        }.getOrNull()

    private fun renderEditPage(index: Int) {
        val active = session ?: return
        if (index !in 0 until active.pageCount) return
        launchBusy {
            currentPageIndex = index
            val size = active.cache.pageSize(index)
            renderedPage = active.cache.page(index, editRenderScale(size))
        }
    }

    /**
     * Edit-mode render scale: twice the fit-width device resolution, so the
     * page stays crisp while pinch-zoomed in the editor. Floored at the old
     * 216 dpi baseline and capped so one page bitmap never busts the budget.
     */
    private fun editRenderScale(size: PageSize): Float {
        val metrics = getApplication<Application>().resources.displayMetrics
        val fitScale = metrics.widthPixels / size.widthPt
        val memoryCap =
            sqrt(MAX_EDIT_BITMAP_BYTES / (size.widthPt * size.heightPt * BYTES_PER_PIXEL))
                .coerceAtLeast(MIN_EDIT_RENDER_SCALE)
        return (fitScale * EDIT_ZOOM_HEADROOM).coerceIn(MIN_EDIT_RENDER_SCALE, memoryCap)
    }

    private fun closeSession() {
        searchController.close()
        selectionController.clear()
        outline = null
        renderedPage = null
        overlayDocument = null
        session?.close()
        session = null
    }

    /** Run [block] with the busy flag set, surfacing any failure to the snackbar. */
    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            runCatching { block() }
                .onFailure { userMessage = "Error: ${it.message}" }
            busy = false
        }
    }

    override fun onCleared() {
        closeSession()
        super.onCleared()
    }

    private companion object {
        // 3 px/pt ≈ 216 dpi is the quality floor for the edit-mode page.
        const val MIN_EDIT_RENDER_SCALE = 3f

        // Render at 2x the screen's fit-width resolution so pinch zoom stays sharp.
        const val EDIT_ZOOM_HEADROOM = 2f
        const val MAX_EDIT_BITMAP_BYTES = 96f * 1024 * 1024
        const val BYTES_PER_PIXEL = 4f
        const val MIN_STROKE_PT = 1f
        const val MAX_STROKE_PT = 8f
        const val MIN_TEXT_PT = 8f
        const val MAX_TEXT_PT = 48f
        const val LETTER_WIDTH_PT = 612f
        const val LETTER_HEIGHT_PT = 792f
        const val PERSIST_PAGE_DEBOUNCE_MS = 400L
        const val THUMBNAIL_WIDTH_PX = 220f
        const val THUMBNAIL_PNG_QUALITY = 90

        fun displayNameOf(
            context: Context,
            uri: Uri,
        ): String =
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"

        fun loadTextDocument(
            context: Context,
            uri: Uri,
            decryptedFile: File?,
        ): PdfTextDocument {
            val stream =
                decryptedFile?.inputStream()
                    ?: context.contentResolver.openInputStream(uri)
                    ?: error("Unable to open source PDF")
            return stream.use { PdfTextDocument.load(it) }
        }
    }
}

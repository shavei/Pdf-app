package com.pdfapp.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfapp.R
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.OverlayCanvasView
import com.pdfapp.overlay.model.OverlayLayer
import com.pdfapp.overlay.model.TextOverlay
import com.pdfapp.ui.reader.GoToPageDialog
import com.pdfapp.ui.reader.HomeScreen
import com.pdfapp.ui.reader.OutlineSheet
import com.pdfapp.ui.reader.PasswordDialog
import com.pdfapp.ui.reader.ReaderBottomBar
import com.pdfapp.ui.reader.ReaderContent
import com.pdfapp.ui.reader.ReaderTopBar
import com.pdfapp.ui.reader.ThumbnailSheet

/**
 * Single-activity screen wiring the three feature modules: open + render
 * (`:core-renderer`), draw overlays (`:overlay-engine`), and flatten + save
 * (`:file-persistence`). An open document starts in the continuous-scroll
 * READ mode (search, outline, selection, immersive chrome); EDIT mode hosts
 * the interactive [OverlayCanvasView] for signing and text placement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    initialUri: Uri? = null,
    viewModel: PdfEditorViewModel = viewModel(),
) {
    val context = LocalContext.current
    var canvasView by remember { mutableStateOf<OverlayCanvasView?>(null) }
    var editTool by remember { mutableStateOf(OverlayCanvasView.Mode.INK) }
    var pendingTextPoint by remember { mutableStateOf<PdfPoint?>(null) }
    var editingText by remember { mutableStateOf<TextOverlay?>(null) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showGoToPage by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val openLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { viewModel.open(context, it) } }

    val saveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(MIME_PDF),
        ) { uri ->
            uri?.let { dest ->
                canvasView?.commitSignature()
                viewModel.save(context, dest)
            }
        }

    LaunchedEffect(initialUri) {
        initialUri?.let { viewModel.openInitial(context, it) }
    }

    val view = LocalView.current
    DisposableEffect(viewModel.keepScreenOn) {
        view.keepScreenOn = viewModel.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Drive-style immersive reading: chrome returns whenever the context
    // changes, and the system bars follow the app chrome in READ mode.
    LaunchedEffect(viewModel.mode, viewModel.session) { chromeVisible = true }
    val searchActive = viewModel.searchController.active
    val hasSession = viewModel.session != null
    val readChromeShown =
        ReaderChrome.topBarShown(chromeVisible, searchActive, viewModel.mode, hasSession)
    val bottomBarShown =
        ReaderChrome.bottomBarShown(chromeVisible, searchActive, viewModel.mode, hasSession)
    DisposableEffect(readChromeShown) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        if (controller != null) {
            if (readChromeShown) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    EditModeBindings(viewModel, canvasView, editTool)
    LaunchedEffect(viewModel.userMessage) {
        viewModel.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val session = viewModel.session
    Scaffold(
        topBar = {
            when {
                session == null -> AppTitleBar()
                viewModel.mode == ViewerMode.READ ->
                    AnimatedVisibility(
                        visible = readChromeShown,
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it },
                    ) {
                        ReaderTopBar(
                            viewModel = viewModel,
                            onShowOutline = { showOutline = true },
                        )
                    }
                else ->
                    EditTopBar(
                        onBack = {
                            canvasView?.commitSignature()
                            viewModel.exitEditMode()
                        },
                    )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = bottomBarShown,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                ReaderBottomBar(
                    viewModel = viewModel,
                    onShowThumbnails = { showThumbnails = true },
                    onShowGoToPage = { showGoToPage = true },
                    onOpenAnother = { openLauncher.launch(arrayOf(MIME_PDF)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                session == null -> {
                    val recents by viewModel.recents.collectAsState()
                    HomeScreen(
                        recents = recents,
                        onOpenClick = { openLauncher.launch(arrayOf(MIME_PDF)) },
                        onRecentClick = { viewModel.open(context, Uri.parse(it.uri)) },
                    )
                }
                viewModel.mode == ViewerMode.READ ->
                    ReaderContent(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onToggleChrome = { chromeVisible = !chromeVisible },
                    )
                else ->
                    EditModeContent(
                        viewModel = viewModel,
                        editTool = editTool,
                        onToolChange = { editTool = it },
                        onSaveClick = { saveLauncher.launch(DEFAULT_SAVE_NAME) },
                        onCanvasReady = { view ->
                            view.onTextPlacementRequested = { point -> pendingTextPoint = point }
                            view.onTextEditRequested = { overlay -> editingText = overlay }
                            view.onLayerChanged = { layer -> viewModel.updateCurrentLayer(layer) }
                            canvasView = view
                        },
                        canvasView = canvasView,
                    )
            }
            if (viewModel.busy) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showThumbnails) ThumbnailSheet(viewModel) { showThumbnails = false }
    if (showOutline) OutlineSheet(viewModel) { showOutline = false }
    if (showGoToPage && viewModel.pageCount > 0) {
        GoToPageDialog(
            currentPage = viewModel.currentPageIndex,
            pageCount = viewModel.pageCount,
            onDismiss = { showGoToPage = false },
            onGo = { page ->
                showGoToPage = false
                viewModel.goToPage(page)
            },
        )
    }
    if (viewModel.passwordRequestUri != null) {
        PasswordDialog(
            wrongPassword = viewModel.wrongPassword,
            onDismiss = { viewModel.cancelPasswordRequest() },
            onSubmit = { viewModel.submitPassword(context, it) },
        )
    }
    TextOverlayDialogs(
        viewModel = viewModel,
        canvasView = canvasView,
        pendingTextPoint = pendingTextPoint,
        onPendingConsumed = { pendingTextPoint = null },
        editingText = editingText,
        onEditingConsumed = { editingText = null },
    )
}

/** Feed edit-mode state (page bitmap, tool settings) into the canvas view. */
@Composable
private fun EditModeBindings(
    viewModel: PdfEditorViewModel,
    canvasView: OverlayCanvasView?,
    editTool: OverlayCanvasView.Mode,
) {
    val rendered = viewModel.renderedPage
    LaunchedEffect(rendered, canvasView) {
        val view = canvasView ?: return@LaunchedEffect
        val page = rendered ?: return@LaunchedEffect
        val layer = viewModel.overlayDocument?.layerFor(page.index) ?: OverlayLayer(page.index)
        view.setPage(page, layer)
    }
    LaunchedEffect(viewModel.inkColorArgb, viewModel.inkStrokeWidthPt, canvasView) {
        canvasView?.inkColorArgb = viewModel.inkColorArgb
        canvasView?.inkStrokeWidthPt = viewModel.inkStrokeWidthPt
    }
    LaunchedEffect(editTool, canvasView) { canvasView?.mode = editTool }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTitleBar() {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
        colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTopBar(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text("Edit", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to reading")
            }
        },
    )
}

@Composable
private fun EditModeContent(
    viewModel: PdfEditorViewModel,
    editTool: OverlayCanvasView.Mode,
    onToolChange: (OverlayCanvasView.Mode) -> Unit,
    onSaveClick: () -> Unit,
    onCanvasReady: (OverlayCanvasView) -> Unit,
    canvasView: OverlayCanvasView?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val controlsEnabled = viewModel.renderedPage != null && !viewModel.busy
        EditorToolbar(
            mode = editTool,
            enabled = controlsEnabled,
            onModeChange = onToolChange,
            onCommitInk = { canvasView?.commitSignature() },
            onUndo = { canvasView?.undo() },
            onClear = { canvasView?.clearOverlays() },
            onSave = onSaveClick,
        )
        ToolSettingsRow(viewModel)
        PageNavBar(
            currentIndex = viewModel.currentPageIndex,
            pageCount = viewModel.pageCount,
            canPrevious = viewModel.canGoPrevious && !viewModel.busy,
            canNext = viewModel.canGoNext && !viewModel.busy,
            onPrevious = {
                canvasView?.commitSignature()
                viewModel.previousPage()
            },
            onNext = {
                canvasView?.commitSignature()
                viewModel.nextPage()
            },
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // The canvas view fills the area and handles pinch-zoom and
            // panning itself, so no scroll containers wrap it.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> OverlayCanvasView(ctx).also(onCanvasReady) },
            )
        }
    }
}

@Composable
private fun TextOverlayDialogs(
    viewModel: PdfEditorViewModel,
    canvasView: OverlayCanvasView?,
    pendingTextPoint: PdfPoint?,
    onPendingConsumed: () -> Unit,
    editingText: TextOverlay?,
    onEditingConsumed: () -> Unit,
) {
    pendingTextPoint?.let { point ->
        TextEntryDialog(
            onDismiss = onPendingConsumed,
            onConfirm = { text ->
                canvasView?.let { view ->
                    view.layer =
                        view.layer.withText(
                            TextOverlay(
                                text = text,
                                position = point,
                                fontSizePt = viewModel.textSizePt,
                                colorArgb = viewModel.textColorArgb,
                            ),
                        )
                }
                onPendingConsumed()
            },
        )
    }

    editingText?.let { overlay ->
        TextEntryDialog(
            title = "Edit text",
            confirmLabel = "Save",
            initialText = overlay.text,
            onDismiss = onEditingConsumed,
            onConfirm = { text ->
                canvasView?.let { view ->
                    view.layer = view.layer.updateText(overlay.copy(text = text))
                }
                onEditingConsumed()
            },
            onDelete = {
                canvasView?.let { view ->
                    view.layer = view.layer.removeText(overlay.id)
                }
                onEditingConsumed()
            },
        )
    }
}

private const val MIME_PDF = "application/pdf"
private const val DEFAULT_SAVE_NAME = "signed.pdf"

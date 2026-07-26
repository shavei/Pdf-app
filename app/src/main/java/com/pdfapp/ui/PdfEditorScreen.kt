package com.pdfapp.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfapp.R
import com.pdfapp.core.renderer.model.PdfPoint
import com.pdfapp.overlay.OverlayCanvasView
import com.pdfapp.overlay.model.TextOverlay
import com.pdfapp.ui.document.PdfEditorViewModel
import com.pdfapp.ui.editor.EditBottomBar
import com.pdfapp.ui.editor.EditModeBindings
import com.pdfapp.ui.editor.EditModeContent
import com.pdfapp.ui.editor.EditTopBar
import com.pdfapp.ui.editor.TextOverlayDialogs
import com.pdfapp.ui.editor.ToolSettingsSheet
import com.pdfapp.ui.home.HomeScreen
import com.pdfapp.ui.reader.GoToPageDialog
import com.pdfapp.ui.reader.OutlineSheet
import com.pdfapp.ui.reader.PasswordDialog
import com.pdfapp.ui.reader.ReaderBack
import com.pdfapp.ui.reader.ReaderBackAction
import com.pdfapp.ui.reader.ReaderBody
import com.pdfapp.ui.reader.ReaderBottomBar
import com.pdfapp.ui.reader.ReaderChrome
import com.pdfapp.ui.reader.ReaderContent
import com.pdfapp.ui.reader.ReaderLayout
import com.pdfapp.ui.reader.ReaderNavRail
import com.pdfapp.ui.reader.ReaderNavStyle
import com.pdfapp.ui.reader.ReaderTopBar
import com.pdfapp.ui.reader.ThumbnailPane
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
    var showToolSettings by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    // Expanded windows dock the navigation pane rather than overlaying it, so it
    // starts open there and the thumbnails action toggles it (Phase E.3).
    var pageDockOpen by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val openLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { viewModel.open(context, it) } }

    // Which of the two form-save shapes the pending SAF request stands for; the
    // picker cannot carry it, so it is latched when the save is asked for.
    var saveFlattensForm by remember { mutableStateOf(false) }
    val saveLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(MIME_PDF),
        ) { uri ->
            uri?.let { dest ->
                canvasView?.commitSignature()
                viewModel.save(context, dest, flattenForm = saveFlattensForm)
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
    val readerActionsShown =
        ReaderChrome.actionsShown(chromeVisible, searchActive, viewModel.mode, hasSession)
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

    // Predictive back (mobile-ui-plan Phase D.3): peel transient viewer states —
    // search, then edit mode, then immersive chrome — one press at a time before
    // the system pops the activity. Disabled (system handles back) when there's
    // nothing app-specific to unwind, so a plain reading screen still exits.
    val backAction =
        ReaderBack.actionFor(
            hasSession = hasSession,
            searchActive = searchActive,
            formActive = viewModel.formController.active,
            mode = viewModel.mode,
            chromeVisible = chromeVisible,
        )
    BackHandler(enabled = backAction != ReaderBackAction.SYSTEM) {
        when (backAction) {
            ReaderBackAction.CLOSE_SEARCH -> viewModel.searchController.close()
            ReaderBackAction.CLOSE_FORM -> viewModel.formController.hide()
            ReaderBackAction.EXIT_EDIT -> {
                canvasView?.commitSignature()
                viewModel.exitEditMode()
            }
            ReaderBackAction.SHOW_CHROME -> chromeVisible = true
            ReaderBackAction.SYSTEM -> Unit
        }
    }

    EditModeBindings(viewModel, canvasView, editTool)
    LaunchedEffect(viewModel.userMessage) {
        viewModel.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val session = viewModel.session
    // Adaptive layout (mobile-ui-plan Phase E): the window's own measured size —
    // not the physical screen — picks the chrome arrangement, so split-screen and
    // foldable resizes are tracked as they happen.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layout = ReaderLayout.spec(maxWidth.value.toInt(), maxHeight.value.toInt())
        val onShowThumbnails: () -> Unit = {
            if (layout.docksThumbnails) {
                pageDockOpen = !pageDockOpen
            } else {
                showThumbnails = true
            }
        }
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
                when {
                    session == null -> Unit
                    viewModel.mode == ViewerMode.READ ->
                        // Wider or shorter windows host these actions in the side
                        // rail inside the content instead (Phase E.2).
                        AnimatedVisibility(
                            visible = readerActionsShown && layout.navStyle == ReaderNavStyle.BOTTOM_BAR,
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ) {
                            ReaderBottomBar(
                                viewModel = viewModel,
                                onShowThumbnails = onShowThumbnails,
                                onShowGoToPage = { showGoToPage = true },
                                onOpenAnother = { openLauncher.launch(arrayOf(MIME_PDF)) },
                            )
                        }
                    else ->
                        EditBottomBar(
                            mode = editTool,
                            enabled = viewModel.renderedPage != null && !viewModel.busy,
                            currentIndex = viewModel.currentPageIndex,
                            pageCount = viewModel.pageCount,
                            canPrevious = viewModel.canGoPrevious && !viewModel.busy,
                            canNext = viewModel.canGoNext && !viewModel.busy,
                            onModeChange = { editTool = it },
                            onPrevious = {
                                canvasView?.commitSignature()
                                viewModel.previousPage()
                            },
                            onNext = {
                                canvasView?.commitSignature()
                                viewModel.nextPage()
                            },
                            onShowGoToPage = { showGoToPage = true },
                            onShowSettings = { showToolSettings = true },
                            onUndo = { canvasView?.undo() },
                            onCommitInk = { canvasView?.commitSignature() },
                            onClear = { canvasView?.clearOverlays() },
                        )
                }
            },
            floatingActionButton = {
                if (session != null && viewModel.mode == ViewerMode.EDIT) {
                    val saveEnabled = viewModel.renderedPage != null && !viewModel.busy
                    FloatingActionButton(
                        onClick = {
                            if (saveEnabled) {
                                // The editor's Save keeps the form interactive;
                                // flattening it is the fill bar's own choice.
                                saveFlattensForm = false
                                saveLauncher.launch(DEFAULT_SAVE_NAME)
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save PDF")
                    }
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
                        ReaderBody(
                            layout = layout,
                            chromeShown = readerActionsShown,
                            paneOpen = pageDockOpen,
                            rail = {
                                ReaderNavRail(
                                    viewModel = viewModel,
                                    onShowThumbnails = onShowThumbnails,
                                    onShowGoToPage = { showGoToPage = true },
                                    onOpenAnother = { openLauncher.launch(arrayOf(MIME_PDF)) },
                                )
                            },
                            pane = { ThumbnailPane(viewModel) },
                        ) {
                            ReaderContent(
                                viewModel = viewModel,
                                snackbarHostState = snackbarHostState,
                                chromeVisible = chromeVisible,
                                onToggleChrome = { chromeVisible = !chromeVisible },
                                onSaveForm = { flatten ->
                                    saveFlattensForm = flatten
                                    saveLauncher.launch(FILLED_SAVE_NAME)
                                },
                            )
                        }
                    else ->
                        EditModeContent(
                            onCanvasReady = { canvas ->
                                canvas.onTextPlacementRequested = { point -> pendingTextPoint = point }
                                canvas.onTextEditRequested = { overlay -> editingText = overlay }
                                canvas.onLayerChanged = { layer -> viewModel.updateCurrentLayer(layer) }
                                canvasView = canvas
                            },
                        )
                }
                if (viewModel.busy) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        // The thumbnail sheet only overlays when the pane isn't already docked.
        if (showThumbnails && !layout.docksThumbnails) {
            ThumbnailSheet(viewModel) { showThumbnails = false }
        }
    }

    if (showOutline) OutlineSheet(viewModel) { showOutline = false }
    if (showToolSettings) {
        ToolSettingsSheet(
            viewModel = viewModel,
            mode = editTool,
            onDismiss = { showToolSettings = false },
        )
    }
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

private const val MIME_PDF = "application/pdf"
private const val DEFAULT_SAVE_NAME = "signed.pdf"
private const val FILLED_SAVE_NAME = "filled.pdf"

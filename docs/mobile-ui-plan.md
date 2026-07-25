# Plan: making Signet's UI as phone-friendly as possible

Signet already works on a phone, but its chrome was built feature-first, not
thumb-first. This plan closes the gap between "runs on a phone" and "feels
native on a phone held in one hand." It is grounded in the current UI code
(`:app` `com.pdfapp.ui.*`), ordered by user impact, and every phase carries the
project's three-layer definition of Done (Lint · Unit · E2E/Harness) from
[`DEVELOPMENT.md`](../DEVELOPMENT.md).

Scope: **layout, reach, touch, gesture, and adaptivity** of the existing
screens. No new PDF features — those live in [`plan.md`](../plan.md). Where this
overlaps that roadmap's "Cross-cutting platform polish" (predictive back,
tablets/foldables, accessibility), this document is the concrete, file-level
version of those bullets.

---

## What's wrong today (audit)

Findings from the current Compose tree. Each is cited to the file that owns it.

| # | Issue | Where | Why it hurts on a phone |
|---|---|---|---|
| 1 | Edge-to-edge is on, but floating/bottom chrome doesn't consume insets | `MainActivity.enableEdgeToEdge()`; `ReaderContent` page chip / copy bar use `.align(BottomCenter).padding(12.dp)` with no `navigationBarsPadding()` | The "Page X / N" chip and copy bar can sit **under the gesture pill / nav bar**, so taps land on the system, not the app. |
| 2 | Touch targets below the 48 dp minimum | `EditorControls.ColorSwatch` = **28 dp**; `Stepper` `−`/`+` `TextButton`s; home `RecentThumbnail` 44×56 | Fat-finger misses; fails Material and accessibility guidance. |
| 3 | Edit toolbar is a horizontally-scrolling text-button row | `EditorControls.EditorToolbar` + `ToolSettingsRow` (`.horizontalScroll(...)`) | **Save / Undo / Clear scroll off-screen**, no icons, no grouping — the most destructive and most important actions are the easiest to miss. |
| 4 | Reader chrome never hides | `PdfEditorScreen` top bar always present; no immersive mode | On a ~6" screen the app bar permanently eats reading height; no way to get a full-bleed page. |
| 5 | Primary actions live in a top-bar overflow menu | `ReaderTopBar` `MoreVert` → `ReaderMenu` | Night mode, thumbnails, go-to-page, fit — all require reaching the **top** of a tall phone one-handed. |
| 6 | Three stacked chrome rows in edit mode | `PdfEditorScreen.EditModeContent`: `EditorToolbar` + `ToolSettingsRow` + `PageNavBar` | Consumes a large slice of vertical space before any page is shown. |
| 7 | Fixed heights clip under large font/display scale | `ReaderSheets` thumbnail `height(130.dp)`; `HomeScreen` fixed thumb; single-line ellipsis titles | Users on large system font / display size get clipped or cramped UI. |
| 8 | No double-tap-to-zoom | `ReaderView` handles pinch + one-finger pan only | Double-tap zoom is a near-universal reader gesture; its absence reads as "unfinished." |
| 9 | No haptic feedback | none in edit/tool/selection paths | Tool changes and long-press selection give no tactile confirmation. |
| 10 | Predictive back not enabled | `AndroidManifest` app tag lacks `enableOnBackInvokedCallback` | Misses the modern-Android back affordance (also a `plan.md` cross-cutting item). |
| 11 | No adaptive layout | no `WindowSizeClass` anywhere | Landscape and foldables/tablets get the tall phone chrome verbatim. |

---

## Phase A — Safe areas & touch targets ✅ *shipped*

Correctness before polish: nothing else matters if controls hide behind the nav
bar or are too small to hit. Small, self-contained, high-confidence.

**Status:** delivered. Floating and bottom chrome now consume window insets
(`navigationBarsPadding()` / `WindowInsets.safeDrawing`) so the page chip and copy
bar clear the gesture pill (A.1), and the sub-48 dp controls were fixed — the
`ColorSwatch` keeps its 28 dp dot inside a 48 dp touch box and the `Stepper`
`−`/`+` are 48 dp `IconButton`s (A.2). Covered by `TouchTargetTest`.

**A.1 Consume window insets on all floating & bottom chrome.**
- `ReaderContent`: wrap the bottom `Column` (page chip + copy bar) so it clears
  the nav bar — `Modifier.navigationBarsPadding()` (or align to
  `WindowInsets.safeDrawing`). The Scaffold already insets the top bar; the
  floating overlay is the gap.
- Anticipating Phase B/C bottom bars: standardize on
  `WindowInsets.safeDrawing` and add `.imePadding()` to the search field
  (`ReaderTopBar.SearchTopBar`) and the text-entry / password dialogs so the
  keyboard never covers the input.

**A.2 Enforce 48 dp minimum targets.**
- `ColorSwatch`: keep the 28 dp visual dot but give it a 48 dp touch box
  (`Modifier.minimumInteractiveComponentSize()` or `size(48.dp)` with the swatch
  centered).
- `Stepper`: replace bare `TextButton("−"/"+")` with `IconButton` (48 dp) or
  apply the same min-size modifier; bump hit area without growing the glyphs.
- `HomeScreen` recent rows already use `ListItem` (≥ 56 dp) — leave, but ensure
  the whole row is the click target (it is).

**Done when:** Lint (`ktlintCheck detekt lintDebug`) clean; a Compose UI test
asserts the swatch/stepper semantics report ≥ 48 dp; manual smoke on a
gesture-nav device shows the page chip fully above the pill.

---

## Phase B — One-handed reach: immersive reading + bottom bar ✅ *shipped*

The single biggest phone win: bring actions to the thumb and give the page the
whole screen.

**Status:** delivered. Tap-to-toggle immersive chrome (B.1) drives both the top
bar and system bars off a single `chromeVisible` state, and a `ReaderBottomBar`
(B.2) hosts Search · Thumbnails · Night mode · Edit plus a tap-to-jump
"page X / N" chip and a `MoreVert` overflow for the long tail (fit width/page,
go to page, keep screen on, open another). Both bars' visibility is decided by
the JVM-testable `ReaderChrome` predicates. The high-frequency actions moved out
of the top-bar overflow, so the reader title bar keeps only the document name
and the outline drawer.

**B.1 Tap-to-toggle immersive chrome (READ mode).**
- Single tap on the page (that isn't a link/selection) toggles a
  `chromeVisible` state. Hidden ⇒ top bar + bottom bar animate out and the app
  goes true full-screen via `WindowInsetsControllerCompat.hide(systemBars())`;
  shown ⇒ they return. Wire the existing `handleTap` in `ReaderView.ReaderPage`
  (it already distinguishes link taps) to also emit a "background tap."
- Persist nothing; default to chrome **visible** on open.

**B.2 Reader bottom bar for the top-used actions.**
- Add a `BottomAppBar` (READ mode) hosting the highest-frequency actions pulled
  out of the overflow menu: **Search · Thumbnails · Night mode · Edit**. Keep
  the overflow `MoreVert` for the long tail (fit-width/page, keep-screen-on, go
  to page, open another).
- Fold the floating "Page X / N" chip into this bar (still tap-to-jump), so
  there's one bottom surface, correctly inset (Phase A).
- Bottom bar visibility follows `chromeVisible` from B.1.

**Done when:** Lint clean; Robolectric/Compose test toggles `chromeVisible` and
asserts bar presence/absence; E2E (`connectedDebugAndroidTest`) taps the page
and verifies system bars hide, taps Edit from the bottom bar and lands in EDIT
mode.

---

## Phase C — Edit-mode toolbar redesign ✅ *shipped*

Replace the scrolling text-button rows (issue #3, #6) with a fixed, icon-first
bottom toolbar and an on-demand settings sheet.

**Status:** delivered. The three horizontally-scrolling text-button rows
(`EditorToolbar` + `ToolSettingsRow` + `PageNavBar`) are gone. Edit mode now
gives the whole content area to the page and hosts one `EditBottomBar` in the
Scaffold's bottom-bar slot (C.1): the tools (Sign / Text / Select) are tinted
icon toggles that always fit, **Save** is a trailing `FloatingActionButton`,
**Undo** is always visible, and the destructive **Clear** / **Apply ink** plus
single-step page moves live in a `MoreVert` overflow. A tap-to-jump "page X / N"
chip folds the old page-nav row in (C.3). Tool colour/size controls open on
demand in a `ToolSettingsSheet` `ModalBottomSheet` scoped to the active tool
(C.2) — the 48 dp swatches and steppers from Phase A move there. Verified by
`EditBottomBarTest` (bar contents + tool/settings/save wiring) with the
retained `TouchTargetTest` still covering the swatch/stepper touch floor.

**C.1 Bottom tool bar, no horizontal scroll.**
- Convert `EditorToolbar` into a `BottomAppBar`: the four modes (Sign / Text /
  Shape / Edit) become icon toggles that always fit; **Save** becomes a
  prominent trailing `FloatingActionButton`; **Undo** an always-visible icon.
  Move **Clear** and **Apply ink** into an overflow or the settings sheet so the
  destructive `Clear` can't be hit by accident.

**C.2 Contextual tool settings as a bottom sheet.**
- Replace the always-present `ToolSettingsRow` with a settings affordance that
  opens a `ModalBottomSheet` scoped to the active tool (colour swatches — now
  48 dp from Phase A — stroke/size steppers, shape kind). Frees the two-to-three
  rows of permanent chrome (issue #6) and gives each control room to breathe.

**C.3 Page nav in edit mode.**
- Fold `PageNavBar` Prev/Next into the bottom bar (or reuse the reader's
  page-chip jump), reclaiming the third chrome row.

**Done when:** Lint clean; existing edit/touch unit tests still green; PDF-Test
Harness (`:file-persistence …PdfTestHarness*`) unaffected (pure UI change);
E2E draws ink, opens the settings sheet, changes colour, and saves.

---

## Phase D — Gestures & feedback ✅ *shipped*

Make the reader feel like a first-class mobile reader.

**Status:** delivered. Double-tap zoom (D.1) landed with the Drive-style viewer
rework — `ReaderView`'s `detectTapGestures.onDoubleTap` animates the cheap live
layer between fit-width and 2.5× about the tap point, and single-tap
(chrome toggle / links) is untouched. Its toggle math is a pure `ReaderZoom`
predicate (`ReaderZoomTest`), and `DoubleTapZoomE2ETest` proves the gesture
zooms the page end-to-end on a device. Haptics (D.2) now fire a light
`TextHandleMove` tick on a real tool switch in `EditBottomBar` and a `LongPress`
tick when text selection latches in `ReaderView`'s
`detectDragGesturesAfterLongPress.onDragStart`. Predictive back (D.3) is on:
`android:enableOnBackInvokedCallback="true"` is set on the `<application>`, and a
`BackHandler` in `PdfEditorScreen` peels the transient states in order — search,
then edit mode, then immersive chrome — before deferring to the system, driven
by the JVM-testable `ReaderBack` predicate (covered by `ReaderBackTest`).

- **D.1 Double-tap to zoom** in `ReaderView`: double-tap toggles between fit and
  a comfortable zoom (2.5×) centered on the tap point. Coordinates with the
  existing `detectTapGestures` so single-tap (chrome toggle / links) still works.
- **D.2 Haptics** via `LocalHapticFeedback`: a light tick on tool/mode change
  (`EditBottomBar`) and on long-press selection start
  (`ReaderView` `detectDragGesturesAfterLongPress.onDragStart`).
- **D.3 Predictive back**: `android:enableOnBackInvokedCallback="true"` on the
  `<application>` in `AndroidManifest.xml`; back exits EDIT→READ and
  search/immersive states predictably (drives off existing `exitEditMode`,
  `searchController.close`, and B.1's `chromeVisible`, ordered by `ReaderBack`).

**Done when:** Lint clean; JVM unit tests for the double-tap zoom target math
(`ReaderZoomTest`) and the back-unwind order (`ReaderBackTest`); an on-device
`DoubleTapZoomE2ETest` for the zoom gesture; manual smoke for haptics and the
predictive-back animation.

---

## Phase E — Adaptive layout (phone-first, ready for large screens) ✅ *shipped*

Stop shipping tall-phone chrome to every form factor.

**Status:** delivered. The window's size now picks the reader chrome (E.1): a
`BoxWithConstraints` in `PdfEditorScreen` feeds the measured window into the pure
`ReaderLayout.spec(widthDp, heightDp)` predicate, which resolves a
`ReaderLayoutSpec` — where the primary actions live, and whether the navigation
pane docks. Compact phone portrait is unchanged: the Phase A–D bottom bar. Wider
(≥ 600 dp) **or shorter** (< 480 dp) windows move those actions into a new
`ReaderNavRail` (E.2) so the bottom bar stops eating the short axis, and the page
keeps the rest of the row. Expanded widths (≥ 840 dp) additionally dock a
`ThumbnailPane` — page thumbnails and the document outline behind a two-tab
header — beside the page instead of overlaying a modal sheet (E.3); there the
thumbnails action toggles the dock rather than opening the sheet, and jumping to a
page leaves it open. `ReaderBody` renders the resolved row (rail · pane · page).
Covered by `ReaderLayoutTest` (breakpoints, including both boundaries) and
`AdaptiveLayoutTest` (the compact → medium → expanded swap, plus the
immersive/closed-dock cases and the rail's action set).

- **E.1 Introduce `WindowSizeClass`** (material3 `androidx.compose.material3.
  windowsizeclass`) in `MainActivity`/`PdfEditorScreen`. Compact width = the
  phone layout from Phases A–D.
- **E.2 Landscape / medium width**: move the bottom bar to a slim start-side
  rail so it doesn't eat the short axis; keep the page centered.
- **E.3 Expanded width (tablet/foldable)**: two-pane — thumbnails/outline
  permanently docked beside the page instead of a modal sheet. This is the
  concrete form of `plan.md`'s "Tablets/foldables: two-page spread" bullet;
  spread rendering itself stays in that roadmap.

**Implementation notes** — two deliberate deviations from the sketch above:

- **No `material3-window-size-class` dependency.** E.1 named that artifact, but
  its `calculateWindowSizeClass` is experimental and measures the *activity*
  window through an `Activity` handle, which a Compose test can't vary. Applying
  the same Material 3 breakpoints to `BoxWithConstraints`' measured size gives
  identical results on a phone, tracks split-screen and foldable resizes as they
  happen, keeps the decision a pure JVM-testable function, and adds no library.
  The breakpoints live as named constants on `ReaderLayout`.
- **Height matters, not just width.** The sketch keyed E.2 on "landscape / medium
  width", but a narrow split-screen window can be short without being wide. The
  rail is chosen on `wide || short`, so any window that can't spare vertical
  space gets it.
- **EDIT mode keeps its bottom bar** at every size. E.2/E.3 are reader concerns
  (the file map lists no `EditorControls.kt`), and `EditBottomBar` carries a FAB,
  an overflow and steppers that a 80 dp rail can't host without a redesign of
  Phase C. Left as-is rather than half-converted.

**Done when:** Lint clean; a UI test drives both a compact and an expanded
`WindowSizeClass` and asserts the rail/two-pane swap; manual smoke on a
resizable emulator across rotation and unfold.

---

## Phase F — Accessibility & dynamic type

Phone-friendly includes users with large fonts, TalkBack, and motor needs.

- **F.1 Font-scale-safe layouts**: remove fixed heights that clip (thumbnail
  cells `height(130.dp)`, home thumb) in favour of aspect-ratio/`wrapContent`;
  allow titles to wrap to two lines where space permits.
- **F.2 TalkBack**: audit content descriptions (most exist); expose page
  position and selection state; ensure the new bottom-bar icons and immersive
  toggle are announced. Ties into `plan.md`'s "expose extracted page text to
  TalkBack."
- **F.3 Large-touch-target mode**: honour the 48 dp floor everywhere (verified
  by a lint/`accessibility` check) and respect the system's bold-text / display
  size without layout breakage.

**Done when:** Lint clean incl. accessibility checks; UI tests at 1.0× and 2.0×
`fontScale` show no clipped/overlapping controls; TalkBack smoke pass on the
reader and edit toolbars.

---

## Sequencing

1. **Phase A** first and alone — pure correctness (insets + targets), low risk,
   unblocks every bottom-bar phase.
2. **Phase B** next — the highest perceived-quality jump (immersive + reach).
3. **Phase C** builds on B's bottom-bar pattern.
4. **Phases D–F** are independent and can land in any order, each behind the
   three-layer gate. D and E have shipped; **Phase F is what's left.**

## File map

| Phase | Primary files |
|---|---|
| A | `ui/reader/ReaderContent.kt`, `ui/EditorControls.kt`, `ui/reader/ReaderTopBar.kt`, `ui/TextEntryDialog.kt`, `ui/reader/ReaderDialogs.kt` |
| B | `ui/reader/ReaderView.kt`, `ui/reader/ReaderContent.kt`, `ui/PdfEditorScreen.kt`, `MainActivity.kt` |
| C | `ui/EditorControls.kt`, `ui/PdfEditorScreen.kt` |
| D | `ui/reader/ReaderView.kt`, `ui/EditorControls.kt`, `AndroidManifest.xml` |
| E | `ui/ReaderLayout.kt`, `ui/PdfEditorScreen.kt`, `ui/reader/ReaderAdaptive.kt`, `ui/reader/ReaderNavRail.kt`, `ui/reader/ReaderActions.kt`, `ui/reader/ReaderPanes.kt`, `ui/reader/ReaderSheets.kt` |
| F | `ui/reader/ReaderSheets.kt`, `ui/reader/HomeScreen.kt`, plus semantics across the tree |

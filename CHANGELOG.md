# Changelog

All notable changes to Signet are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to
follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases are cut by pushing a `vX.Y.Z` tag; see
[`docs/RELEASING.md`](docs/RELEASING.md). Each tagged release attaches a signed
APK + AAB to its [GitHub Release](../../releases). The rolling
[`Latest build`](../../releases/latest) is refreshed on every green push to
`main` and is not versioned here.

## [Unreleased]

### Added

- **Fill PDF forms** (plan Phase 4) — an AcroForm document now opens with a
  "Fill form" action. Turning it on lays a native input over every field
  — text boxes, checkboxes, radio groups and dropdowns — positioned on the page
  itself, so they stay in place as you zoom and scroll. Read-only fields are
  shown but not editable, and a field's character limit is respected as you type.
  Nothing is written to the original file: edits are held until you save, and
  "Reset" puts them all back.
- **Save a filled form two ways** — "Save filled form" keeps the fields
  editable for whoever gets it next; "Save flattened (not editable)" burns the
  values into the page, and combines with the ink signature in the same save, so
  a form can be filled and signed in one pass. Fields that could not be written
  are named in the confirmation rather than dropped silently.
- **Version on the home screen** — the running build names itself
  ("Signet 1.4.1", or "Signet 1.4.1 (build 102)" for a rolling build), so a
  sideloaded APK can be identified without opening Android's App info.
- **Accessibility & dynamic type** (mobile-ui-plan Phase F) — TalkBack
  announces a rendered page by its position and, while a screen reader is
  running, reads the page's extracted text; the immersive chrome toggle is
  offered as that page's click action. The glyph shorthand on the chrome is
  spoken properly ("Page 5 of 120, go to page", "Match 3 of 12"), text
  selection announces itself, thumbnails mark the current page, and the edit
  canvas describes the page, active tool and existing overlays.
- **Font-scale-safe layouts** — thumbnail cells, the home thumbnail and grid
  columns grow with the system font scale instead of clipping; outline entries
  and recent-file names wrap to two lines.

### Changed

- **Source tree organised by feature** (no behaviour change) — `:app`'s flat
  `com.pdfapp.ui` package is split into `ui.common`, `ui.document`, `ui.home`,
  `ui.reader` and `ui.editor`, with the shell (`PdfEditorScreen`, `ViewerMode`)
  left at `ui`. Unit and instrumented tests mirror those packages, whole-app
  device flows moved to `com.pdfapp.e2e`, and the roadmap moved from `plan.md`
  to [`docs/plan.md`](docs/plan.md) beside the other long-form docs. The package
  map is in [`DEVELOPMENT.md`](DEVELOPMENT.md#app-source-layout).
- Every control now meets the 48 dp touch floor. Material 3 leaves text buttons
  *and every icon button* at 40 dp — so the reader bottom bar, nav rail, top bar
  and edit bar were all below the accessibility minimum.
- Android lint's accessibility checks (`ContentDescription`,
  `ClickableViewAccessibility`, `KeyboardInaccessibleWidget`, `LabelFor`) are
  build errors in `:app` and `:overlay-engine`.
- **Build naming** — builds install as a version plus a build number,
  `1.4.1 (build 102)`, instead of `0.1.0+9caa14e`; the commit SHA stays in the
  release notes. Published files are named after the app: `Signet-<version>.apk`
  / `.aab` on tagged releases, `Signet.apk` on the rolling `Latest build`
  (unversioned, so the download permalink is stable).
- **One source of truth for the version** — `appVersionName` in
  `gradle.properties`. Builds and all three CI jobs read that one line (via the
  new `.github/actions/app-version`), and a `vX.Y.Z` tag that disagrees with it
  fails the release instead of shipping a mislabelled build. The version is never
  edited by hand: publishing a release commits the next patch (`1.4.1` → `1.4.2`)
  to `main`, so it climbs by one every time and cutting a release is just
  "tag the version `main` is on". `./gradlew -q :app:appVersion` prints what a
  build would stamp. See [`docs/RELEASING.md`](docs/RELEASING.md#versioning).

### Fixed

- **Double-tapping twice in a row no longer leaves the page somewhere you did
  not ask for.** The reader worked out the right distance to travel and then
  lost it: a second zoom arriving before the first had finished scrolling
  cancelled that scroll outright, so the document settled short and stayed
  there — and because every double-tap started an animation without stopping
  the one already running, two taps read the same zoom, fought over the same
  transform and committed two conflicting positions. Tapping again to correct a
  bad zoom therefore made it worse, which is what made it look unfixable.
  A double-tap now takes over from the one in flight instead of racing it,
  toggles against the zoom the reader is heading for rather than the one
  mid-animation on screen, and no anchor is discarded while it is still owed.
  The moment between a committed zoom and its anchor landing is covered too, so
  a zoom finishes where it was aimed rather than arriving and then jumping.
- **Zooming lands where you asked for it.** Two ways it didn't: a pinch turned
  the document about whichever finger touched down first rather than the point
  between the fingers, and any zoom whose anchor sat above the top of the page
  the reader was on — most double-taps back to fit-width, and any pinch that
  drew the fingers down the screen — was pinned to that page's top instead of
  travelling back to the line it was asked to hold. A zoom's vertical anchor is
  now applied once the pages exist at the new zoom, so a distance measured in
  post-zoom pixels is spent against post-zoom pages instead of landing short,
  and a zoom-out travels back up through the pages above rather than stopping at
  the current page's top.
- **Pan a zoomed page in both axes** — dragging moves the document vertically as
  well as horizontally, and the pan clamps against the zoomed width so no
  gesture exposes content past the page edge.
- **Double-tap zoom animates on frame callbacks** — the toggle drives the live
  GPU transform and bakes the result in once, instead of relayouts per step, and
  a zoomed scroll no longer runs through recomposition.
- **The gap between pages scales with the zoom**, and pages the lazy list is
  holding for reuse are skipped when that gap is measured.

## [1.3.0] — 2026-07-22

The reading experience is reworked into a **Google-Drive-style viewer**, and the
scope is refocused to **viewer + signer** (the annotation suite is removed).

### Added

- **Drive-style continuous scroll** — all pages stack in one continuously
  scrolling column, rendered on demand with recycled bitmaps.
- **Document-level zoom** — pinch anywhere zooms the whole document about the
  gesture centroid and survives scrolling across pages; double-tap toggles
  fit-width ↔ 2.5×; pan when zoomed in. High-zoom strips are re-rendered crisply
  as tiles, so quality (up to 8×) never trades off against memory.
- **Immersive chrome** — a single tap hides/shows the app bar and system bars
  for full-screen reading; a fast-scroll handle with a transient "page X / N"
  bubble flings through long documents.
- **Smooth zoom** — a live GPU transform drives the pinch gesture, with a
  debounced settled scale before re-rasterising.
- **Phone-first UI** — safe-area insets and 48 dp touch targets (Phase A), a
  one-handed reader bottom bar (Phase B), and an icon-first edit toolbar with a
  settings sheet (Phase C).

### Changed

- Replaced the one-page-at-a-time horizontal pager with the continuous-scroll
  reader described above.
- Documentation (README, plan) updated to describe the Drive-style viewer and
  the viewer + signer scope.

### Removed

- **Annotation suite (Phase 3)** — removed by product decision. Signet stays a
  focused viewer + signer; the shape tool and its models, flattening and tests
  were dropped. See [`docs/plan.md`](docs/plan.md).

### Fixed

- Edit bottom bar no longer overflows on small phones.
- Stepper touch targets sized to 48 dp.
- `OpenWithIntentTest` updated for the chip-less continuous reader.
- CI upload steps tolerate a full Actions artifact-storage quota without failing
  an otherwise-green build.

## [1.2.0] — 2026-07-14

- View-page zoom system reworked for consistent focal-anchored zoom across the
  reader and editor.

## [1.1.1] — 2026-07-13

- Fixes to touch-drag behaviour while signing.

## [1.1.0] — 2026-07-11

- Move and edit placed text overlays: drag to reposition, tap to edit or delete.

## [1.0.0] — 2026-06-19

- Initial release: PDF viewer with text overlays and a flattened ink signature,
  saved via SAF; "Open with" / share-target integration.

[1.3.0]: ../../releases/tag/v1.3.0
[1.2.0]: ../../releases/tag/v1.2.0
[1.1.1]: ../../releases/tag/v1.1.1
[1.1.0]: ../../releases/tag/v1.1.0

<!-- 1.0.0 is deliberately unlinked. Its tag pointed at a commit from an
     unrelated project that shared this repository before it was cleaned up, so
     the tag and its release were deleted rather than re-cut; there is nothing
     left to link to. Its entry above stays as the record of what shipped, and
     renders unlinked — which Keep a Changelog allows. -->


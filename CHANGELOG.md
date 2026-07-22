# Changelog

All notable changes to Signet are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to
follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases are cut by pushing a `vX.Y.Z` tag; see
[`docs/RELEASING.md`](docs/RELEASING.md). Each tagged release attaches a signed
APK + AAB to its [GitHub Release](../../releases). The rolling
[`Latest build`](../../releases/latest) is refreshed on every green push to
`main` and is not versioned here.

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
  were dropped. See [`plan.md`](plan.md).

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
[1.0.0]: ../../releases/tag/v1.0.0

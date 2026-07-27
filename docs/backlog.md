# Backlog: flagged, not yet done

Every item below was raised somewhere — a PR description, a plan document, a
review note — and then never picked up. This file collects them in one place so
they stop living only in merged PR bodies where nobody reads them.

Each entry cites where it was flagged and where the code stands **today**
(verified against `main` at the time of writing, July 2026). Items are grouped by
size, not priority; the ordering inside each group is roughly by how much a user
would notice.

Two conventions:

- **Feature work with a home in [`plan.md`](plan.md) stays there.** This file
  points at those phases rather than restating them, and carries the loose ends
  that no phase owns.
- An item leaves this file when it ships (with the changelog entry as the
  record) or when a product decision voids it — see
  [Closed without doing](#closed-without-doing) at the bottom.

---

## Large

### L1 · Phase 5 — page organization & document tools

**Flagged:** [`plan.md`](plan.md#phase-5--page-organization--document-tools) ·
**Status:** not started.

Reorder / rotate / delete pages, merge, split, extract, compress, **print** and
**share out**. Print (`PrintManager` + a `PrintDocumentAdapter` streaming the
current bytes) and share-out (`ACTION_SEND` through `FileProvider`) are the two
remaining table-stakes gaps for a default viewer, and both are cheap relative to
what they buy — Android's print framework takes PDF natively.

### L2 · Phase 6 — create & secure

**Flagged:** [`plan.md`](plan.md#phase-6--create--secure) · **Status:** not
started.

Images → PDF, on-device camera scan, add/remove password, and
**cryptographic/PAdES signing**. The last one is the project's oldest open
thread: PR #1 chose a flattened visual signature and said "a seam is left for
that later," PR #2 repeated it, and the seam has been unused ever since.

### L3 · Google Play deployment

**Flagged:** PR #7 (**closed unmerged**) · **Status:** nothing in the repo.

PR #7 carried the Gradle Play Publisher plugin, a `play-publish.yml` workflow,
`app/src/main/play/` store-listing metadata and `docs/PRIVACY.md`. None of those
files exist on `main`. Its human-only prerequisites were never done either: Play
Console account, the mandatory first manual AAB upload, and the
`PLAY_SERVICE_ACCOUNT_JSON` / signing-keystore secrets. Sideloading remains the
only distribution path.

### L4 · Nothing since v1.3.0 has been released

**Status:** `gradle.properties` says `appVersionName=1.4.1`; the remote has no
`v1.4.x` tag.

The whole of `CHANGELOG.md`'s `[Unreleased]` — AcroForm fill & sign, Phase F
accessibility, the version system, the source reorganisation — is unreleased.
The newest thing a user can install is four weeks old. Cutting the tag also
clears S2 (the README still advertises v1.3.0).

### L5 · Cross-cutting platform polish

**Flagged:** [`plan.md`](plan.md#cross-cutting-platform-polish) · **Status:**
accessibility shipped (mobile-ui-plan Phase F); the rest untouched.

Static app shortcuts ("Open last document", "Pick a PDF"), themed icon, per-app
language, tablets/foldables two-page spread, and drag-and-drop of a PDF onto the
app window.

---

## Medium

### M1 · The public `v1.0.0` tag points at an unrelated commit

**Flagged:** PR #6 · **Status:** still true.

`v1.0.0` resolves to `f4bdbb8 "Extract NEON SURVIVOR into its own repo"` — a
commit from a different project that shared this repository before it was
cleaned up. `CHANGELOG.md`'s `[1.0.0]` link points straight at that release.
Needs the tag and its release deleted (and either re-cut at the real 1.0.0
commit or the changelog link dropped).

### M2 · Stray branches on the public remote

**Flagged:** PR #6 · **Status:** still true.

`showcase-site` and `garmin-hebrew-widget` are unrelated projects visible to
anyone browsing the repository. Beneath them sit roughly forty merged `claude/*`
feature branches that were never pruned — cosmetic, but it makes the branch list
useless.

*(M3 — `PAGE_SPACING` not scaled with zoom — shipped in PR #48: the gap is
`(PAGE_SPACING * zoom).dp`, guarded by `zoomScalesTheGapsBetweenPages`. Its
number is not reused, so PRs citing M4–M10 still resolve.)*

### M4 · Two-finger pan during a pinch is deferred to the settle

**Flagged:** PR #36 · **Where:** the live `graphicsLayer` transform in
`ui/reader/ReaderView.kt`.

Zoom tracks the fingers on the GPU, but the pan component of a two-finger
gesture is only baked in on release, so a large in-gesture pan can briefly reveal
clipped edges that fill in afterwards. One-finger scrolling is unaffected. PR #36
asked for an on-device look during E2E; that look never happened.

### M5 · Recent behavioural changes were never exercised on a device

**Flagged:** PR #44, #46, #47 · **Status:** left to CI's emulator job.

`FormPipelineE2ETest`, `FormFillE2ETest` and the pan/zoom tests were only ever
compiled locally — the authoring environment has no KVM, so no emulator. They are
green in CI, but nobody has watched the forms flow or the pan gesture happen on
real hardware.

### M6 · `launchMode` left at default

**Flagged:** PR #21, [`plan.md`](plan.md#11-manifest-declare-the-intent-filters-app)
· **Where:** `app/src/main/AndroidManifest.xml` (no `launchMode` attribute).

A second `VIEW` intent creates a new task entry instead of reusing the running
one. `singleTask` + `onNewIntent` was called out as a cheap follow-up precisely
because every entry point already routes through `Intent.pdfUri()`.

### M7 · `AcroFormFixture` bypasses `testFixtures`

**Flagged:** PR #44 · **Where:** the shared source directory added by
`:core-renderer` and `:file-persistence`.

AGP's `testFixtures` variant is the idiomatic home for a fixture two modules'
tests share, but the Kotlin Android plugin does not compile Kotlin for it. The
hand-rolled source directory works; it is a wart in the test build that should be
revisited whenever the toolchain grows the support.

### M8 · Manual smokes that were promised and skipped

**Flagged:** PR #21, #40, #41.

- Phase 1: tap a PDF in Files → chooser lists Signet → **Always** → sign → save.
- Phase D: haptics on tool change, and the predictive-back animation.
- Phase E: a resizable emulator across rotation and unfold.

Each is the "manual smoke" half of a three-layer Done that shipped on the
automated half alone.

### M9 · Arabic text overlays are not shaped

**Flagged:** PR #16 · **Status:** out of scope then, never revisited.

The Hebrew fix brought bidi reordering and a Unicode fallback font, so RTL runs
land in visual order. Arabic additionally needs glyph shaping (contextual
forms/ligatures), and the bundled Arimo subset does not cover the script at all.

### M10 · Intent-delivered PDFs disappear from Recents

**Flagged:** [`plan.md`](plan.md#27-recent-files) · **Status:** accepted
consequence of the best-effort persistable grant.

A PDF opened from another app carries a temporary grant, so its Recents entry
stops resolving once the task dies. Fixing it means copying into app-private
storage on open — a real design decision, not a bug, but users see a dead entry.

### M11 · A committed zoom shows one un-anchored frame

**Flagged:** the zoom-anchor fix · **Where:** the `pendingAnchor` effect in
`ui/reader/ReaderView.kt`.

The vertical half of a zoom anchor can only be applied once the pages have
re-composed at the new zoom, so the frame in between presents the new zoom at the
old scroll position, before the anchor lands. It reads as a flicker on the way to
the right place rather than a wrong resting position. Compose 1.9's
`LazyListState.requestScrollToItem` sets a position for the *next* measure
instead of forcing one, which would close the gap; the project is on Compose 1.7
(BOM 2024.10.01), so it moves with the next BOM upgrade — alongside S8.

---

## Small

### S1 · The home screen still says "annotate"

**Flagged:** PR #43 · **Where:** `ui/home/HomeScreen.kt:58` — "View, sign and
annotate PDFs — entirely on this device."

The annotation suite was removed by product decision in v1.3.0. One string.

### S2 · README still advertises v1.3.0

**Where:** `README.md:5`, `:14`, `:130`–`:134`.

The download button, the badge and the install section all point at
`v1.3.0/app-release.apk`. Moves with L4; the pinned-link bump rule is already
documented in the README's own comment.

### S3 · `mobile-ui-plan.md` still names the Shape tool

**Flagged:** PR #39 (left intact deliberately) · **Where:**
[`mobile-ui-plan.md:132`](mobile-ui-plan.md), `:140`.

Phase C's prose lists "Sign / Text / Shape / Edit" and a "shape kind" control.
The phase is shipped and its status note is accurate, so this is stale
description, not a stale plan.

### S4 · The rolling build outranks tagged releases

**Flagged:** PR #9 · **Where:** `make_latest: true` on the rolling publish step in
`.github/workflows/ci.yml`.

GitHub's "latest release" is the unversioned rolling build, not the newest signed
`vX.Y.Z`. PR #9 offered to flip the rolling one to a prerelease; the decision was
never made.

### S5 · Release builds fall back to debug signing

**Flagged:** PR #3 · **Where:** the release `signingConfig` in
`app/build.gradle.kts`.

With `KEYSTORE_FILE` and friends unset, a release build signs with the debug key
and succeeds. That was deliberate — it keeps the artifact installable without
secrets — but it means a misconfigured release ships quietly instead of failing.

### S6 · AI references remain in git history

**Flagged:** PR #8 · **Status:** working-tree files were cleaned; history was not.

Commit trailers, author metadata and `claude/*` branch names still carry them.
Removing them needs a history rewrite and a force-push — deliberately out of
scope, recorded here so the decision isn't rediscovered.

### S7 · `CLAUDE.md` vs `DEVELOPMENT.md`

**Flagged:** PR #27 · **Status:** open question.

`CLAUDE.md` is a thin pointer at `DEVELOPMENT.md` and `plan.md`. Whether that is
the end state or the two should be consolidated was raised and never answered.

### S8 · `LocalClipboardManager` deprecation is deferred, not gone

**Flagged:** PR #47.

It disappeared with the Compose BOM revert and will return with any future BOM
upgrade. Worth handling in the same change that next moves the BOM.

---

## Closed without doing

- **Shape fill pickers** (PR #30) — void: the shape tool was removed with the
  annotation suite in v1.3.0.
- **Edit-mode fixed render scale** (PR #24) — superseded: `PdfEditorViewModel`
  now derives the scale from fit-width with zoom headroom and a memory cap
  (`MIN_EDIT_RENDER_SCALE`), rather than the flat `3f` PR #24 questioned.

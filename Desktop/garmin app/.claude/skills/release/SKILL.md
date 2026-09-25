---
name: release
description: Prepare a Connect IQ store release of the Hebrew Calendar widget - bump the manifest version, build all devices as a check, package the signed .iq, and walk through the upload checklist. Use when the user wants to ship, publish, or upload a new version.
---

# Store Release

Project root: `C:\Users\yosef\Desktop\garmin app`. Store history & rules: MEMORY.md.
**Dashboard latest app version: v1.6.0. manifest is already 1.7.0 (95 devices, packaged 2026-09-24, ready to upload) — the store requires every new version to be higher than the dashboard's latest. Check the upload page's "(Latest app version: …)" line before writing What's New / reviewer notes.**

## Steps

1. **Pick the version.** Read `version=` from `manifest.xml`. Default: bump the minor
   (e.g. 1.7.0 → 1.8.0) unless the user named a version, or ship the current 1.7.0 if it was never uploaded. Confirm it's strictly greater than
   the live store version.

2. **Bump** `manifest.xml` version (Edit the `version="..."` attribute only).

3. **Package** the signed store binary — this compiles EVERY device in `manifest.xml`
   (95 as of v1.7.0) in one pass (~4 min, run in background) and doubles as the
   pre-flight check; any error stops the release:
   ```powershell
   monkeyc -e -r -w -o "bin\HebrewCalendar.iq" -f monkey.jungle -y "developer_key.der"
   ```
   Expect `N OUT OF N DEVICES BUILT` + `BUILD SUCCESSFUL`. Never run several monkeyc
   processes in parallel here (shared default.jungle). Report the .iq size.

4. **After ANY layout/font/glance change: full text-fit check** (the user requires 10/10 on
   every watch): `tools\sim\preview\run.ps1 -All` (~90 min, worst-case strings) →
   `python tools\sim\preview\sheet.py --all 6` → review every sheet + `stale.py`; Instincts
   also `real_glance.ps1` (real carousel). Fix and re-shoot anything that clips or touches
   before packaging. See CLAUDE.md "Bulk visual check".

5. **Commit** the version bump (message: `Release vX.Y.Z`).

6. **Upload checklist** for the user (the dashboard upload is manual):
   - Connect IQ developer dashboard → app → "Upload New Version" → `bin\HebrewCalendar.iq`
   - What's-new text: summarize the commits since the last `Release`/`Published` commit
     and draft it for them (English + Hebrew, see STORE_LISTING.md for tone)
   - Listing assets if changed: `bin\store_images\` (cover_500.png, hero_1440x720.png, 1–5 jpg — Garmin allows max 5)
   - Garmin review verdict arrives by email (Gmail is connected — offer to watch for it)

7. After the user confirms it's live: update MEMORY.md store-status section (live version,
   date) and commit (`Published: vX.Y.Z live; sync notes`).

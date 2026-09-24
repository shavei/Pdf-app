---
name: release
description: Prepare a Connect IQ store release of the Hebrew Calendar widget - bump the manifest version, build all devices as a check, package the signed .iq, and walk through the upload checklist. Use when the user wants to ship, publish, or upload a new version.
---

# Store Release

Project root: `C:\Users\yosef\Desktop\garmin app`. Store history & rules: MEMORY.md.
**v1.5.0 is live — the store requires every new version to be higher (next ≥ 1.6.0).**

## Steps

1. **Pick the version.** Read `version=` from `manifest.xml`. Default: bump the minor
   (1.5.0 → 1.6.0) unless the user named a version. Confirm it's strictly greater than
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

4. (Optional, after layout changes) Spot-check devices per font bucket with
   `tools\sim\preview\run.ps1 -Devices ...` + `sheet.py` (see CLAUDE.md).

5. **Commit** the version bump (message: `Release vX.Y.Z`).

6. **Upload checklist** for the user (the dashboard upload is manual):
   - Connect IQ developer dashboard → app → "Upload New Version" → `bin\HebrewCalendar.iq`
   - What's-new text: summarize the commits since the last `Release`/`Published` commit
     and draft it for them (English + Hebrew, see STORE_LISTING.md for tone)
   - Listing assets if changed: `bin\store_images\` (cover_500.png, hero_1440x720.png, 1–6 jpg)
   - Garmin review verdict arrives by email (Gmail is connected — offer to watch for it)

7. After the user confirms it's live: update MEMORY.md store-status section (live version,
   date) and commit (`Published: vX.Y.Z live; sync notes`).

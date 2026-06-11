---
name: release
description: Prepare a Connect IQ store release of the Hebrew Calendar widget - bump the manifest version, build all 16 devices as a check, package the signed .iq, and walk through the upload checklist. Use when the user wants to ship, publish, or upload a new version.
---

# Store Release

Project root: `C:\Users\yosef\Desktop\garmin app`. Store history & rules: MEMORY.md.
**v1.4.0 is live — the store requires every new version to be higher (next ≥ 1.5.0).**

## Steps

1. **Pick the version.** Read `version=` from `manifest.xml`. Default: bump the minor
   (1.4.0 → 1.5.0) unless the user named a version. Confirm it's strictly greater than
   the live store version.

2. **Pre-flight build check** — compile all 16 devices; any failure stops the release:
   instinct3solar45mm instinct3amoled45mm instinct3amoled50mm instinct2 fr165m fenix7
   fr255 fr955 fr55 fenix847mm fr965 venu3 fr265 epix2 venu2 vivoactive5
   ```powershell
   monkeyc -o "bin\check-$d.prg" -f monkey.jungle -y "developer_key.der" -d $d
   ```

3. **Bump** `manifest.xml` version (Edit the `version="..."` attribute only).

4. **Package** the signed store binary:
   ```powershell
   monkeyc -e -r -o "bin\HebrewCalendar.iq" -f monkey.jungle -y "developer_key.der"
   ```
   Verify `bin\HebrewCalendar.iq` exists and report its size.

5. **Commit** the version bump (message: `Release vX.Y.Z`).

6. **Upload checklist** for the user (the dashboard upload is manual):
   - Connect IQ developer dashboard → app → "Upload New Version" → `bin\HebrewCalendar.iq`
   - What's-new text: summarize the commits since the last `Release`/`Published` commit
     and draft it for them (English + Hebrew, see STORE_LISTING.md for tone)
   - Listing assets if changed: `bin\store_images\` (cover_500.png, hero_1440x720.png, 1–6 jpg)
   - Garmin review verdict arrives by email (Gmail is connected — offer to watch for it)

7. After the user confirms it's live: update MEMORY.md store-status section (live version,
   date) and commit (`Published: vX.Y.Z live; sync notes`).

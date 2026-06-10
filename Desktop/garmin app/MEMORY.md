# Hebrew Calendar Garmin Widget — Session Memory

## Project Location
`C:\Users\yosef\Desktop\garmin app`

## SDK & Toolchain
- SDK: `C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.1.0-2026-03-09-6a872a80b` (`monkeyc`/`monkeydo` are on PATH)
- Developer key: **`developer_key.der` in the project root** (NOT in the Garmin AppData dir — that path does not exist)
- Build output: `bin\` folder inside project
- Target devices: `instinct3solar45mm`, `instinct3amoled45mm`, `instinct3amoled50mm`, `fr165m`

## Quick Build & Run Commands
```powershell
# cwd is already the project. Build + run one device:
monkeyc -o "bin\instinct3amoled45mm.prg" -f monkey.jungle -y "developer_key.der" -d instinct3amoled45mm
monkeydo "bin\instinct3amoled45mm.prg" instinct3amoled45mm
# If "Unable to connect to simulator": Start-Process "<sdk>\bin\simulator.exe"; Start-Sleep 5; then monkeydo
```
- **CRITICAL: run all monkeydo/simulator commands with `dangerouslyDisableSandbox: true`** — otherwise monkeydo writes to a sandbox %TEMP% the real simulator can't read, and the new build never loads.

---

## Current Design (simplified — all confirmed working ✅)
The app was stripped down to **ONE screen**: the Hebrew date. All extra pages (Parasha/Holiday/Zmanim/Candles/Omer) were removed.

- **Widget** (`HebrewCalendarView`):
  - Solar: white circle (mirrors the GPS button) holding the **day-of-week letter** on the RIGHT side, with day+month and year centered below the GPS circle.
  - AMOLED/fr165m: day-of-week letter, day+month, year — all centered.
- **Glance** (`HebrewCalendarGlanceView`): day-of-week Hebrew letter (א=Sun … ז=Sat) on the LEFT, date (day+month over year) right-aligned RTL.
- Day-of-week letters in `HebrewDate.DOW_LETTERS`, via `getDayOfWeekLetter()`.

## Hebrew date math (`HebrewDate.mc`) — VERIFIED CORRECT
- `hebrewNewYear` uses the **Reingold–Dershowitz** algorithm with ALL FOUR dechiyot: `hebrewElapsedDays(y)` folds molad-zaken + lo-ADU via `(3*(day+1))%7 < 3`; `hebrewNewYear` adds GaTaRaD (356) / BeTUTaKPaT (382) year-length corrections; JD base **347998**.
- **Bug fixed:** old code applied only 2 of 4 dechiyot → wrong Cheshvan/Kislev lengths (e.g. year 5787), dates off by a day. Verified **0 mismatches over 50 years** vs the `pyluach` library.
- **Day-of-week off-by-one fixed:** Garmin `Gregorian.Info.day_of_week` is 1-based (Sunday=1), `DOW_LETTERS` 0-based → `initialize()` uses `info.day_of_week - 1`.

## Fonts — the key architecture (HARD-WON, don't regress)
Hebrew renders via **bitmap `.fnt`/`.png` fonts** generated from `NotoSansHebrew-Regular.ttf`. **Vector fonts (`getVectorFont`) are NOT used anywhere** — on AMOLED they return Latin-only `?` diamonds; on fr165m they render at a fixed tiny size and ignore per-device sizing.
- `source/HebrewFonts.mc`: `HebrewFonts` (`:glance`, glance) + `HebrewFontsEx` (widget). **Both always `loadResource` bitmap fonts** (the glance's old vector-first path was removed; `_sizes()` is now vestigial).
- **Glance + Rez gotcha:** the glance process can NOT access a normal `Rez` font (`Could not access symbol 'Rez'`). Glance fonts MUST be `scope="glance"` in `fonts.xml` → `HebrewGlanceSmall`/`HebrewGlanceMedium`.
- **Descender fix (CRITICAL):** `.fnt` `lineHeight`/`base` MUST come from `font.getmetrics()` (ascent+descent), NOT `size+2`, or final letters (ן ך ף ץ ק) clip (e.g. סיוון→סיווו). All 3 generators do this.
- **Per-device generators & sizes (widget / glance px):**
  - `generate_fonts.py` → `resources-instinct3solar/fonts` — Solar widget **18/24/28**, glance **23/29** (Large 28 so כ"ט מרחשוון clears the round 176px edge).
  - `generate_fonts_amoled.py` → `resources-instinct3amoled/fonts` — AMOLED widget **34/48/62**, glance **36/46**.
  - `generate_fonts_fr165m.py` → `resources-fr165m/fonts` — fr165m widget **34/46/58**, glance **40/50** (Large capped 58 for the 360px edge).

## Resource layout
- Each device has its OWN `resources-<device>/fonts/fonts.xml` (same font IDs, different bitmap sizes): `resources-instinct3solar`, `resources-instinct3amoled`, `resources-fr165m`.
- `monkey.jungle` sets per-device `resourcePath` (e.g. `fr165m.resourcePath += resources-fr165m`).

## Platform limitation (can't fix — told the user)
The **calendar icon in the glance** (Solar corner sub-screen + fr165m glance row) is Garmin's **system-drawn launcher icon**; a custom GlanceView has no API to hide/replace it. (Drawing into the Solar sub-screen from the glance drew a stray circle over content — reverted.) The WIDGET *can* use `WatchUi.getSubscreen()`, which is why the Solar widget shows the day letter in that circle.

---

## File Map
| File | Role |
|---|---|
| `source/HebrewCalendarApp.mc` | App entry: `getInitialView` (widget) + `getGlanceView` (`:glance`) |
| `source/HebrewCalendarView.mc` | Widget view (single Hebrew-date screen) |
| `source/HebrewCalendarGlanceView.mc` | Glance view (day letter + Hebrew date) |
| `source/HebrewCalendarDelegate.mc` | Back/exit handling only |
| `source/HebrewFonts.mc` | `HebrewFonts` (glance) + `HebrewFontsEx` (widget) bitmap font loading |
| `source/HebrewDate.mc` | Hebrew date calc + day-of-week letters |
| `source/DeviceInfo.mc` | Device/layout/color helpers (`isSolar`, `colorDim`, etc.) |
| `generate_fonts.py` / `generate_fonts_amoled.py` / `generate_fonts_fr165m.py` | Per-device bitmap font generators (use getmetrics for lineHeight) |
| `manifest.xml` | App ID, target devices, version |
| `monkey.jungle` | Build config, per-device resource paths |

## Status — DONE (all 4 devices visually signed off by user)
fr165m ✅ · AMOLED 45mm ✅ · AMOLED 50mm ✅ · Solar ✅ — glance + widget, correct Hebrew date + day-of-week, per-device fonts tuned to user sign-off.

## Store submission (current: v1.1.0)
- **v1.0.0** uploaded earlier, **Pending** review on apps.garmin.com/developer.
- **v1.1.0** (`manifest.xml`) = date/day-of-week correctness fixes + larger per-device fonts + unified glance bitmap path. Re-upload `bin\HebrewCalendar.iq` (~98KB, 5 targets) as the update.
- Earlier prep (commit `df45d6c`): removed unused `Positioning` permission; deleted dead `Zmanim.mc`/`JewishCalendar.mc`; new Hebrew-calendar launcher icon (calendar page + א, PNGs 54/60/62px).
- **Backups:** `developer_key.der` + old icons/files in `C:\Users\yosef\Desktop\garmin app-backups\`. Dev key is irreplaceable.
- **Listing copy:** `STORE_LISTING.md` (English + Hebrew). Still user-supplied at upload: screenshots per device family, category (Widget/Tools).

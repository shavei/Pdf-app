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

## Fonts — the key architecture (HARD-WON, don't regress)
Hebrew renders via **bitmap `.fnt`/`.png` fonts** generated from `NotoSansHebrew-Regular.ttf`. Vector fonts (`getVectorFont`) are unreliable: in the AMOLED simulator they return a Latin-only font → `?` diamonds. So we always fall back to bitmap fonts.
- `source/HebrewFonts.mc`: class `HebrewFonts` (`:glance`) for the glance; class `HebrewFontsEx` for the widget. Both load bitmap Rez fonts.
- **Glance + Rez gotcha:** the glance process can NOT access a normal `Rez` font — it throws `Could not access symbol 'Rez'`. Fonts used in the glance MUST be declared with `scope="glance"` in `fonts.xml`. We added `HebrewGlanceSmall`/`HebrewGlanceMedium` (same .fnt files, `scope="glance"`) — the glance loads those.
- **Per-device font sizes:** Solar + fr165m use base/solar `fonts` at 16/20/24px; AMOLED uses `resources-instinct3amoled/fonts` at 30/42/56px (bigger for the 390/416 screen).
- **Generators:** `generate_fonts.py` (Solar 16/20/24 + base resources), `generate_fonts_amoled.py` (AMOLED 30/42/56). Both write `HebrewSmall/Medium/Large`.
- **Descender fix (CRITICAL):** `.fnt` `lineHeight`/`base` MUST come from `font.getmetrics()` (ascent+descent), NOT `size+2`. Otherwise final letters (ן ך ף ץ ק) get their descender clipped — e.g. סיוון rendered as סיווו. Both generators now do this.

## Resource layout
- `resources/fonts/fonts.xml` — base (fr165m + AMOLED inherit), defines Hebrew* + HebrewGlance* (scope=glance)
- `resources-instinct3solar/fonts/fonts.xml` — Solar override, same IDs
- `resources-instinct3amoled/fonts/fonts.xml` — AMOLED override, larger bitmaps, same IDs
- `monkey.jungle` sets per-device `resourcePath` (e.g. `instinct3amoled45mm.resourcePath += resources-instinct3amoled`)

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
| `generate_fonts.py` / `generate_fonts_amoled.py` | Bitmap font generators (use getmetrics for lineHeight) |
| `manifest.xml` | App ID, target devices |
| `monkey.jungle` | Build config, per-device resource paths |

## Status (all 4 devices build clean, Hebrew confirmed in simulator)
fr165m ✅ · AMOLED 45mm ✅ (glance + widget, big fonts, סיוון correct) · AMOLED 50mm ✅ (same fonts) · Solar ✅ (glance + widget with day-of-week circle on the right).

**Not yet committed** — all session changes are still in the working tree on `master`.

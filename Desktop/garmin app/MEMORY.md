# Hebrew Calendar Garmin Widget — Session Memory

## Project Location
`C:\Users\yosef\Desktop\garmin app`

## SDK & Toolchain
- SDK: `C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.1.0-2026-03-09-6a872a80b` (`monkeyc`/`monkeydo` are on PATH)
- Developer key: **`developer_key.der` in the project root** (NOT in the Garmin AppData dir — that path does not exist)
- Build output: `bin\` folder inside project
- Target devices: **95 as of v1.7.0 (2026-09-24)** — list in `manifest.xml`, mapping in `monkey.jungle`.
  The original 16: `instinct3solar45mm`, `instinct3amoled45mm`, `instinct3amoled50mm`, `fr165m`, `fenix7`, `fenix847mm`, `fr965`, `fr265`, `fr255`, `fr955`, `venu3`, `venu2`, `vivoactive5`, `epix2`, `instinct2`, `fr55`

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
  - `generate_fonts_mip260.py` → `resources-mip260/fonts` — MIP 260px (fenix7/fr255/fr955) widget **24/32/42**, glance **26/33**. ALSO writes `resources-glance63/fonts` — glance **21/23** for fenix7 only, whose glance content area is just **63px tall** (fr255/fr955 get 93px); appended after the bucket in monkey.jungle so its glance font IDs override. 21/23 is the MAX that fits the glance layout: lineHeight(Small) ≤ 30 (top line vs y=0) and LH(Small)+LH(Medium) ≤ 62 (year-line bottom vs y=63); NotoSansHebrew LH ≈ size+9 in this range.
  - `generate_fonts_amoled454.py` → `resources-amoled454/fonts` — AMOLED 454px (fenix847mm/fr965/venu3) widget **38/52/68**, glance **40/50**.
  - `generate_fonts_mip208.py` → `resources-mip208/fonts` — fr55 (MIP 208px) widget **20/26/34**, glance **20/26**. fr55's glance contentArea is **144x75** (narrower than fr255's 176): glance Small 20 is the max where the longest date line (כ״ט אדר א׳ = 104px) fits right of the day letter; widget = mip260 sizes × 208/260.

## Resource layout
- Font buckets (same font IDs, different bitmap sizes): `resources-instinct3solar` (176 MIP: Instinct 3 Solar + Instinct 2 — same semioctagon class, same 62px icon), `resources-mip260` (fenix7/fr255/fr955), `resources-fr165m` (390), `resources-instinct3amoled` (390/416: both Instinct 3 AMOLED + fr265/epix2/venu2/vivoactive5), `resources-amoled454` (fenix847mm/fr965/venu3), `resources-mip208` (fr55).
- `monkey.jungle` sets per-device `resourcePath`; later paths override earlier (used for icon-size overlays `resources-icon56` → vivoactive5, `resources-icon70` → venu2/venu3).
- Launcher icons: `generate_icons.py` renders the calendar+א icon (PIL redraw, NotoSansHebrew א glyph) at 35/40/56/65/70px. Existing 54/60/62px PNGs untouched.

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

## Retail editions ALREADY COVERED — never add these as "new" devices
The store auto-expands each CIQ device id into all retail editions on the same platform (same screen + same binary). These are **already live** via our 15 ids — do NOT download profiles or add products for them:
- `instinct2` covers: Instinct 2 Camo / dēzl / Surf / Tactical / Solar / Dual Power / ONE PIECE editions.
- `fenix7` covers: fenix 7 Sapphire / Solar / Dual Power, **quatix 7** (+ Sapphire).
- `fenix847mm` covers: fenix 8 AMOLED **51mm**, **tactix 8** 47/51mm (+ Cerakote), **quatix 8** 47/51mm.
- `epix2` covers: Porsche Epix 2. · `venu2` covers: Mercedes-Benz Venu 2. · `vivoactive5` covers: GarminActive 5.
- `fr955` covers: FR955 Solar / Dual Power. · Instinct 3 ids cover the Tactical editions.

Genuinely SEPARATE ids (different screens — these WOULD be new work if requested): instinct2s (163px), instinct2x, fenix7s (240px), fenix7x (280px), fenix7pro*, fr265s (360px), venu2s (360px), venu2plus, venu3s (390px), epix2pro42/47/51, fenix843mm, fr165 (non-Music).

## fr55 (Forerunner 55, MIP 208x208) — added + SIM-VERIFIED ✅, shipped in v1.4.0
`generate_fonts_mip208.py` (widget 20/26/34, glance 20/26 — sized for fr55's 144x75 glance
area), `resources-mip208/` (generated fonts + 35px launcher icon), fr55 in `manifest.xml`
+ `monkey.jungle`. All 16 devices compile. Sim-verified glance + widget (screenshots
`bin\shots\fr55_glance.png` / `fr55_widget.png`), user signed off on the screenshots;
glance 17.5/27.9KB, widget 24.0/59.9KB — fr55 has the smallest limits of the fleet
(32KB glance / 64KB widget), both fine. fr55 is non-touch: open glance carousel = click
display once (focus) + send {DOWN}, open widget = {ENTER}.

## Status — 15 devices, ALL USER-VERIFIED ✅ (2026-06-11)
- User manually checked glance+widget on all 11 new devices: all good. Only fix needed: fenix7 glance was cut off (63px-tall glance area) → `resources-glance63` smaller glance fonts, re-verified.
- Glance geometry lives in each device's `simulator.json` → `glance.contentArea` (e.g. fenix7 171x63 vs fr255 176x93) — check this when adding MIP devices.
- instinct2 ✅ added after the batch of 10 (same bucket/layout as Instinct 3 Solar incl. sub-screen circle; glance 18/27.9KB OK). Sim: open widget from glance = click display once (focus) then send ENTER.
- Original 4 (signed off in v1.1.0): fr165m ✅ · AMOLED 45mm ✅ · AMOLED 50mm ✅ · Solar ✅.
- 10 added in v1.2.0, all sim-verified (glance + widget screenshots in `bin\shots\`): fenix7 ✅, fr255 ✅, fr955 ✅ (MIP 260 bucket) · fenix847mm ✅, fr965 ✅, venu3 ✅ (AMOLED 454 bucket) · fr265 ✅, epix2 ✅, venu2 ✅ (416), vivoactive5 ✅ (390) on the instinct3amoled bucket.
- Sub-screen (Solar circle) layout safe: only fires via `DeviceInfo.isSolar()` = screenW ≤ 176; smallest new device is 260.
- All new devices have 64KB glance memory (glance uses ~12.5KB) — no memory issues.
- Sim screenshot workflow: `tools\sim\capture.ps1` (PrintWindow), `tools\sim\click.ps1`, `tools\sim\runshot.ps1` (monkeydo → tap glance → tap again → capture). fr255 has no touch — click the chrome START button instead.
- **Reviewing all devices at a glance: open `bin\shots\v15\index.html`** — it's the sorted grid of every device's glance/date/parasha shots, the canonical place to eyeball the whole fleet (e.g. before a release or after a layout change). Regenerate the shots with `tools\sim\retake_v15.ps1`. (Build output + screenshots live in the gitignored `bin\`; dev scripts live in tracked `tools\`.)

## v1.5.0 in progress (2026-06-12) — parasha page + settings, NOT yet released
Built on branch `garmin-hebrew-widget` after a store review asked for customizability:
- **Page 2 = parasha** (`ParashaView.mc` + `Parasha.mc`): פרשת השבוע + name; festival
  Shabbatot (no portion) show the festival name with header שבת. Enter: select/tap/swipe-left;
  exit: back/select/swipe-right. Page dots on both pages (Solar inactive dot = white outline).
- **Algorithm** ported from pyluach (`_gentable`): virtual deque [51,52,0..51] walked
  Shabbat-by-Shabbat from RH; 6 doubling rules (NB Matot = index 41 — initial port had 40,
  caught by verification); Israel reads on diaspora 2nd days (Tishrei 23/Nisan 22/Sivan 7).
  Verified twice: `tools/verify/verify_parsha.py` → 0/25,568 days vs pyluach 2020-2090 (pip
  install pyluach; PYTHONIOENCODING=utf-8 for Hebrew prints) + `tools/verify/crosscheck_hebcal.py`
  → 0/418 Shabbatot vs hebcal.com (saved offline feeds in `tools/verify/hebcal_fixtures/`),
  2026-2029, BOTH schedules. Old deleted JewishCalendar.mc
  was junk (weekOfYear%54, Purim in Nisan) — never resurrect it.
- **Settings** (`resources/settings/`, `AppSettings.mc`): israelSchedule number 1/0 (lists
  can't bind booleans — compile error "For input string"), textColor number (palette-safe
  Graphics constants). Defaults: Israel, white. Instinct forced white. onSettingsChanged →
  requestUpdate.
- **Maqaf gotcha (CRITICAL):** ASCII `-` between Hebrew words gets bidi-substituted to
  maqaf U+05BE at render → missing-glyph box unless fonts include `־`. All 6 generators
  + all buckets regenerated with maqaf in CHARS (2026-06-12).
- **Sim gotcha — stored app settings:** the sim persists `GARMIN\APPS\SETTINGS\<DEV>.SET`
  across monkeydo installs AND applies stale values across device profiles; it re-saves
  from memory on exit. To test new property defaults: STOP the sim, delete the .SET files,
  restart. Cost an hour of debugging "properties not updating".
- **Sim gotcha — instinct3amoled45mm navigation:** tap/ENTER/GPS-chrome clicks would NOT
  open the widget from the glance in this session (stock v1.4.0 build also affected —
  not a code bug). epix2 runshot flow works; instinct3amoled was originally verified
  manually in v1.1.0. Unresolved; use epix2 for AMOLED sim checks.
- **Unit tests work well:** `monkeyc --unit-test` + `monkeydo bin\test.prg <dev> -t` —
  the test flag is `-t` (NOT `/t`; `/t` gives "ILLEGAL ARGUMENT"). The simulator must
  already be fully booted before monkeydo connects, or it hangs with no output.
  ParashaTest.mc covers weekly/doubled/festival/Haazinu/settings.
- **Verified in sim (epix2):** both pages, Israel קרח vs diaspora שלח (real divergence
  week!), yellow color end-to-end, doubled אחרי מות־קדושים auto-fit. All 16 devices compile.
- **ALL 16 DEVICES SIM-VERIFIED (2026-06-12):** parasha page rendered correctly on every
  device (epix2, instinct3amoled45/50, instinct3solar45, instinct2, fr55, fr255, fr955,
  fenix7, fr165m, venu2, venu3, vivoactive5, fr265, fenix847mm, fr965). Verified via the
  watch-app trick (below) + real widget flow on epix2/instinct3amoled45/instinct3solar45/
  instinct2. Remaining before release: version bump ≥1.5.0 + store listing update.

## v1.6.0 in progress (2026-06-14) — Omer / next-holiday / Rosh Chodesh, NOT released
Built on branch `showcase-site`. Three pure-math calendar features (no location/permission):
- **Omer counter** (`HebrewDate.omerDay`/`getOmerDay`): 16 Nisan(day1)..5 Sivan(day49),
  gematria + `בעומר` (e.g. `ל״ג בעומר`). Months Nisan=7/Iyar=8/Sivan=9.
- **Closest Jewish date** (`source/HebrewEvents.mc`, new): `nextEvent(hd, israel)` returns
  [name, daysUntil] = soonest of a BROAD event set vs next Rosh Chodesh (always ≤30 days:
  onset is 30-d days away for any month). **Event set expanded 2026-06-17 (user: "closest
  Jewish thing, not just ראש חודש")** from 10 majors to ~24: + public fasts (צום גדליה, עשרה
  בטבת, תענית אסתר, י״ז בתמוז) + minor/festive (הושענא רבה, ט״ו בשבט, שושן פורים, פסח שני,
  ל״ג בעומר, ט״ו באב, שמיני עצרת/שמחת תורה — split 22/23 diaspora vs combined 22 Israel) +
  modern Israeli (יום השואה/הזיכרון/העצמאות/ירושלים). NOMINAL dates — no Shabbat-nidche.
  Purim & co. → Adar II in leap years. `countdownText`: היום/מחר/בעוד יומיים/בעוד <gematria> ימים.
  jd via per-year rh+doy (cheap sums, watchdog-safe). Verified vs **pyluach** (`testNextEvent`).
- **`contextual(hd, israel)`** is the shared decision (ParashaView + OmerView both call it):
  Omer count wins in season EXCEPT on a day that is itself an event → the event wins (so the
  modern days / ל״ג בעומר / פסח שני that fall INSIDE the Omer surface on their day). Tested
  (`testContextual`): plain Omer day → count; 5 Iyar → יום העצמאות; out of season → closest event.
- **Countdowns use GEMATRIA not Arabic digits** to stay all-Hebrew (the bitmap fonts DO
  contain 0-9 + maqaf already — confirmed in the committed `.fnt` — but we don't render digits).
- **UX:** Non-Solar shows the line under the parasha name on page 2 (Omer in season, else
  next event; 2-line wrap via `_drawExtra`, clamped above the dots). **Solar/Instinct 2
  (176px 2-color) can't fit a 3rd line under the GPS circle → it gets a dedicated page 3
  (`OmerView.mc`, new)** — user's explicit choice ("3 separate pages, solar only").
  `drawDots(dc, active, count)` now takes a count (2 normally, 3 on Solar); ParashaDelegate
  pushes OmerView on Solar select/swipe-left.
- Unit tests added (`ParashaTest.mc`): `testOmerDay`, `testNextEvent`. All 7 pass; all 16 compile.
- **Sim-verified (2026-06-14):** epix2 omer+event, fr55 event (2-line fits 208px), Solar
  page2 (clean) + page3 omer + page3 event. Shots + `index.html` in `bin\shots\feat\`.
- **v1.6.0 PACKAGED, ready for dashboard upload** (2026-06-17): manifest 1.5.0→1.6.0,
  `bin\HebrewCalendar.iq` ~730KB/26 variants (rebuild after the expanded event set + the
  round-clip fix — repackage again if code changes). Gallery refreshed to
  `1_venu3_date` `2_epix2_omer` `3_fr55_event` `4_solar_omer` `5_fr265_glance` + new hero
  (Omer page + date, tagline "PARASHA · OMER · HOLIDAYS"); STORE_LISTING.md updated (what's-new
  EN+HE, full description, reviewer notes). Glance unchanged (date only). **User still needs to
  Upload New Version on the dashboard** (yosefnider@gmail.com account). Once live → mark LIVE.

## Round-screen clip bug (fixed 2026-06-14) — chord-aware text width
The contextual line at ~76%h on the parasha page CLIPPED both bezel edges on round
watches (user caught on fr165m: `ראש חודש תמוז מחר` ran off-screen). Cause: the fit/wrap
check compared against full width (`w-24`), but a round screen's usable width NARROWS toward
top/bottom — a medium single line "fit" the full-width test, drew on one line, and overflowed
the bezel. (Missed in screenshots because my test dates gave either a short Omer line or a
long countdown that wrapped — never the medium single-line case.) Fix: `DeviceInfo.usableWidthAtY(y)`
returns the chord width at that row (square screen → `2·√(r²−dy²)−pad`; non-square → full);
`_drawExtra` caps maxW to it so medium lines wrap instead of clipping. Line also raised 76%→74%.
RULE: any text drawn low/high on a round screen must budget width with `usableWidthAtY`, not `w-…`.

## Sim screenshot workflow — UPDATED (2026-06-14, learned the hard way)
- **The sim opens a widget to its GLANCE over the watchface, NOT the widget page.** After
  `monkeydo` you see the Hebrew date on a watchface ring — that's the glance, the app loaded
  fine. Don't mistake it for "stale build / getInitialView ignored" (burned ~30 min on this).
  To reach the widget: tap the glance band (~50% x, ~43% y of the window) on touch devices,
  or use the watch-app trick.
- **Watch-app trick = most reliable for screenshots** (esp. non-touch Solar/Instinct/fr55,
  which won't open the widget from the glance via keyboard). Set manifest `type="watch-app"`,
  **COMMENT OUT `getGlanceView` entirely** (returning `null` shows a `?` placeholder glance on
  CIQ 5.2), set `getInitialView` to the page you want + a fixed `Time.Gregorian.moment({...})`
  date, build, monkeydo → launches that page FULLSCREEN, capture directly. REVERT all of it after.
- New helpers: `tools\sim\capture2.ps1` (PrintWindow of the LARGEST visible sim window —
  robust vs the stale 249x43 grabs the old `capture.ps1` returned), `tools\sim\openshot.ps1`
  (focus sim + tap glance band + capture, for touch devices).
- **`monkeydo -t` vs `/t` is shell-dependent:** PowerShell needs `/t` (`-t` prints usage),
  Bash needs `-t` (`/t` = ILLEGAL ARGUMENT). (Supersedes the old "-t not /t" doc note.)
- The sim wedges/garbles (torn glyphs, watchface bleed-through) after heavy monkeydo cycling,
  esp. fr55 — kill `simulator.exe` + restart to clear. Verify suspicious captures with a real
  desktop screenshot (PrintWindow can return frozen frames).

## Layout fixes (2026-06-12, user-requested) — parasha lowered + glance date inset
- **Parasha page (non-Solar): header y=28%h, name y=53%h** (was 22/48 — header clipped on
  fr55's round top edge, user pasted the screenshot). Solar branch untouched. Don't raise.
- **Glance date inset (round screens):** the glance carousel's band can sit high on screen
  (top slot, and on fr265/fr955/fr165m-class firmware even when focused) where the round
  edge CLIPS the first 1-2 chars of the right-aligned date (כ״ז סיוון → ז סיוון; v1.4.0
  live has this). Fix: date+year right-aligned at `w - pad - inset`, inset = 20% of dc
  width, capped so the longest date (כ״ט אדר א׳) never collides with the day letter
  (cap binds on the 176px MIP glance areas). Year alone never clipped, but both lines
  share xR for alignment.
- **CRITICAL — no System calls in glance code:** `System.getDeviceSettings()` inside the
  glance view got the app **silently rejected at install ("Unsupported app was removed"
  in CIQ_LOG.YML)** on tiered-glance CIQ 3.4 devices (fr55, instinct2) — the glance
  showed the broken-app icon / garbled watchface. Shape selection is done at COMPILE
  time instead: `GlanceShape.insetPct()` has `(:glance,:roundGlance)` and
  `(:glance,:flatGlance)` variants; monkey.jungle sets `base.excludeAnnotations =
  flatGlance` and overrides `instinct2`/`instinct3solar45mm` to exclude `roundGlance`
  (flush-right kept there). Instinct 3 AMOLED is a true round display → roundGlance.
- **Instinct subscreen in the sim masks glance content** (date start vanishes under the
  top-right circle on instinct2/instinct3solar glance) — on REAL hardware the subscreen
  region is normal display pixels (sim-only rendering quirk). Don't "fix" it.
- CIQ_LOG.YML (crash/install log): `%TEMP%\com.garmin.connectiq\GARMIN\APPS\LOGS\`.

## WATCHDOG BUG (fixed 2026-06-12) — instinct2 "Code Executed Too Long"
First Parasha.mc port crashed instinct2 (CIQ 3.4.2, strict watchdog): every Shabbat-walk
step recomputed month lengths via hebrewDaysInMonth → hebrewNewYear (3 molad calcs each),
and ParashaView called the full walk TWICE (hasParasha + displayName). Fix: year month
lengths prefix-summed ONCE into `starts`, walk is day-of-year arithmetic only, and the
view calls forShabbat() once (joinNames/festivalName for display). Re-verified after the
rewrite: 0 mismatches vs pyluach (2020-2090) + hebcal (2026-2029). RULE: any Parasha.mc
change must keep the single-walk pattern and rerun verify_parsha.py.

## Sim navigation — SOLVED (the "widget won't open" mystery)
1. **Simulation > App Lock Enabled is CHECKED by default per device profile** and
   silently blocks opening apps from the glance (affects stock builds too!). Uncheck it
   (menu at window-relative ~(163,51), item at bottom of dropdown) before glance testing.
2. Instinct/non-touch: display taps are touch events (ignored); use keyboard. Recipe:
   click display once (focus) → {DOWN} (focus glance in carousel) → {ENTER} (open) →
   {ENTER} (page 2). Keys DO work — if nothing happens, the wrong carousel item is
   focused or App Lock is on.
3. **capture.ps1 (PrintWindow) can return STALE frames** for the watch viewport after sim
   restarts — verify screen state with a real screen grab (Windows-MCP Screenshot) when
   results look frozen.
4. **Fast all-device verification trick:** temporarily set manifest type="watch-app" AND
   comment out getGlanceView → monkeydo auto-launches the app full-screen, no glance
   navigation needed at all. ENTER pushes page 2. RESTORE type="widget" + glance after.
   (CIQ 5.x sims show glances even for watch-apps, hence also removing getGlanceView.)
5. **Touch devices: a "focus click" on the display IS A TAP** → fires onSelect → pushes
   page 2. This silently swapped date/parasha screenshots on 11 devices (ENTER then POPS
   from the parasha page, capturing date as "parasha"). For touch devices capture the
   date page with NO clicks (fresh launch), then ONE tap = parasha page. For non-touch,
   click display (focus) + {ENTER} — the first ENTER after a click is often eaten:
   ALWAYS verify the capture and resend click+ENTER (script `tools\sim\retake_v15.ps1`).
6. **Glance carousel band position varies** per device firmware: some sims show the
   glance in a high "top slot" (clipped by the round edge), one {DOWN} (sometimes two,
   fr55) moves it to the focused mid-screen band. fr265-class never moves — band is
   fixed high (that's why the glance inset exists). If the sim display wedges (black
   screen / frozen frame ignoring keys): kill + restart simulator.exe.

## v1.7.0 — 16 → 95 devices (2026-09-24, NOT yet uploaded)
- User asked to "add as many as possible". Device profiles were downloaded via the SDK Manager
  (`C:\Users\yosef\Downloads\connectiq-sdk-manager\sdkmanager.exe`, Devices tab → download
  whole API-level groups 6.0/5.2/5.1/5.0/3.4). Its window can open BEHIND Chrome — bring it to
  the front (SetForegroundWindow on the sdkmanager process) before clicking anything.
- Added every WATCH with a glance (79 new ids): fenix 6/7/8/9 all sizes incl. the new fenix 9
  family, MARQ Gen1+2, epix Pro, Enduro 1/3, FR165/170/70/255S/265S/570/945LTE/970, Venu 2S/2+/
  3S/4/Sq2/X1, vivoactive6, Instinct 2S/2X/E/Crossover(+AMOLED), Descent, Approach S50/S70, D2.
  **Edge bike computers excluded.**
- Only buckets/overlays + two small layout fixes were needed; see CLAUDE.md "Target devices".
  Glance font sizes chosen from each device's `simulator.json` glance.contentArea with the rule
  LH(GlanceSmall) ≤ h/2 and (LH(S)+LH(M))/2 ≲ h/2+8% (both glance lines draw in GlanceSmall).
- **Instinct 2S (163x156)**: year line fell off the bottom and the parasha name hit the page dots
  → `DeviceInfo.solarLift()` + date-page overflow nudge (no-op at 176px; Instinct 3 Solar
  re-shot identical).
- **Instinct Crossover (+AMOLED)** are hybrids: the sim draws physical hands across the middle
  of the screen. Nothing to do in-app.
- CIQ 3.4 devices (fenix 6, MARQ Gen1, FR945 LTE, Instinct 2S/2X/Crossover, Descent G1/Mk2)
  have 32KB glance / 64KB widget: fenix6 glance measured 19.6/27.9KB, widget ~43KB — OK.
- Sim-verified with the preview harness (`tools\sim\preview\`): instinct2s, instincte40mm/45mm,
  instinctcrossover(+amoled), fr255s, fenix7s, fr945lte, fenix6, fenix7x, enduro3, fr265s,
  venusq2, vivoactive6, fr170, fenix9pro51mm, venux1, epix2pro51mm, fenix8solar47mm — all OK.
  Store package: 147/147 part-number builds, ~4MB.
- Gotcha: after preview builds (same app id) the sim showed GARBLED glyphs for the real build —
  stale font cache; restarting simulator.exe fixes it. Not a real bug.

## Store status — PUBLISHED ✅ (v1.4.0 LIVE, 2026-06-11, 16 devices)
- **Garmin developer account email: `yosefnider@gmail.com`** (NOT the user's general
  shilomeir@gmail.com). Store review verdicts and Connect IQ dashboard mail go here.
- **v1.5.0 LIVE ✅ 2026-06-13** (internal build 6) — parasha page + settings shipped.
  Dashboard confirms: Latest Release 2026-06-13, v1.5.0 (Internal: 6), still 4.7★/3 reviews,
  10+ downloads. **Next release must be ≥ 1.6.0.** (Built from commit `4bdffd7`: manifest
  bumped, `bin\HebrewCalendar.iq` 613KB/26 variants, gallery `1_instinct2_date`
  `2_fenix7_date` `3_venu3_parasha` `4_fr965_parasha` `5_fr955_parasha` `6_fenix7_glance`
  + hero, what's-new from STORE_LISTING.md.)
- **Attribution (CREDITS.md):** the parasha algorithm in `Parasha.mc` is a port of **pyluach**
  (© 2014 Meir S. List, MIT) — MIT notice reproduced in CREDITS.md, credited in Parasha.mc
  header + the store full description. **Hebcal** (CC BY 4.0) was only a verification reference;
  no Hebcal data/API ships. Keep these credits if the parasha code stays.
- **v1.4.0 (internal build 5) live 2026-06-11**: added Forerunner 55. **Next release must be ≥ 1.5.0.**
- History: v1.3.0 (internal 4) = the 15-device update, same day. (Its first submit attempt errored client-side but actually consumed v1.2.0/internal 3 — that's why version numbers skip.)
- Store page: 4.7★, 3 reviews (latest: "עובד👍" on v1.3.0). Listing assets in `bin\store_images\`: cover `cover_500.png`, hero `hero_1440x720.png`, screenshots `1_*.jpg`–`5_*.jpg`. **Garmin allows max 5 gallery images.** Generators in `tools\store\` (`make_store_images.py`, `make_cover.py`, `make_hero.py`) share `storelib.py` (gradient bg + anti-aliased watch cut-out + soft shadow). Gallery emits a styled `.jpg` (upload) **and** a clean white-bg `.png` twin (NOT for upload — only the hero composites from the twins). Run order if regenerating: gallery → hero (depends on twins) → cover.
- To ship an update: bump `manifest.xml` version → `monkeyc -e -r -o bin\HebrewCalendar.iq ...` → dashboard "Upload New Version".
- Earlier prep (commit `df45d6c`): removed unused `Positioning` permission; deleted dead `Zmanim.mc`/`JewishCalendar.mc`; new Hebrew-calendar launcher icon (calendar page + א, PNGs 54/60/62px).
- **Backups:** `developer_key.der` + old icons/files in `C:\Users\yosef\Desktop\garmin app-backups\`. Dev key is irreplaceable.
- **Listing copy:** `STORE_LISTING.md` (English + Hebrew). Still user-supplied at upload: screenshots per device family, category (Widget/Tools).

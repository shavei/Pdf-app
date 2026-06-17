# Hebrew Calendar — Garmin Connect IQ Widget

Monkey C widget showing the Hebrew date (day-of-week letter, day+month, year). Hebrew RTL,
bitmap fonts, glance + widget views. Published on the Connect IQ store (v1.4.0 live,
16 devices; **next release must be ≥ 1.5.0**).

Deep architecture notes, hard-won gotchas, and store history live in [MEMORY.md](MEMORY.md) —
read it before touching fonts, date math, or layout.

For any Connect IQ / Monkey C API question, use the offline docs mirror in
`.claude/skills/connectiq-docs/references/` (Toybox API, language guides, core topics,
per-device specs — SDK 9.1.0) instead of fetching developer.garmin.com.

## Standing instruction: keep the notes current yourself

The user will NEVER ask you to update documentation/memory. Whenever something durable
changes in a session — bug fixed, version shipped, device added, design decision,
workflow learned — update the right file(s) on your own before the session ends:
this CLAUDE.md (build/architecture facts), [MEMORY.md](MEMORY.md) (history/status/gotchas),
or the skills in `.claude/skills/` (workflow changes). Then tell the user in one line
that the notes were updated.

## Build & run

SDK 9.1.0 is on PATH (`monkeyc`, `monkeydo`). Developer key is `developer_key.der` in the
project root (NOT in Garmin AppData).

```powershell
# Build one device
monkeyc -o "bin\<device>.prg" -f monkey.jungle -y "developer_key.der" -d <device>

# Run in simulator (start it first if not running)
Start-Process "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.1.0-2026-03-09-6a872a80b\bin\simulator.exe"
monkeydo "bin\<device>.prg" <device>

# Store package (release)
monkeyc -e -r -o "bin\HebrewCalendar.iq" -f monkey.jungle -y "developer_key.der"
```

**CRITICAL: all `monkeydo`/simulator commands need `dangerouslyDisableSandbox: true`** —
sandboxed monkeydo writes to a %TEMP% the real simulator can't read; the new build never loads.

Simulator screenshot/click helpers: `tools\sim\capture.ps1`, `tools\sim\click.ps1`,
`tools\sim\runshot.ps1` (monkeydo → tap glance → tap → capture). fr255 has no touch — click
the chrome START button.
In the sim, open the widget from the glance: click the display once (focus), then send ENTER.
On fr55 the sim boots to a black watchface — click the display, send {DOWN} to reach the
glance carousel, then {ENTER} for the widget.

**Sim gotchas (cost hours — see MEMORY.md "Sim navigation — SOLVED"):** the sim's
`Simulation > App Lock Enabled` is on by default per device profile and silently blocks
opening apps from the glance; `capture.ps1` can return stale frames after sim restarts
(verify with a real screen grab); for bulk device verification, temporarily build as
`type="watch-app"` with getGlanceView commented out — the app then auto-launches
full-screen on monkeydo with no glance navigation.

## Target devices (16)

`instinct3solar45mm` `instinct3amoled45mm` `instinct3amoled50mm` `instinct2` `fr165m`
`fenix7` `fr255` `fr955` (MIP 260) · `fr55` (MIP 208) · `fenix847mm` `fr965` `venu3`
(AMOLED 454) · `fr265` `epix2` `venu2` `vivoactive5` (AMOLED 390/416)

Retail editions (quatix, tactix, Solar/Tactical variants, etc.) are auto-covered by these
ids — never add them as new products. See MEMORY.md for the full mapping and for the list
of genuinely separate ids (different screens).

## Rules that must not regress

- **Bitmap fonts only.** Never use `getVectorFont` — Latin-only `?` diamonds on AMOLED,
  fixed tiny size on fr165m. Fonts are generated from NotoSansHebrew by the
  `tools/fonts/generate_fonts*.py` scripts; `.fnt` lineHeight/base MUST come from `font.getmetrics()`
  or final letters (ן ך ף ץ ק) clip.
- **Join doubled parshiyot with maqaf `־` (U+05BE), never ASCII `-`.** Garmin's RTL
  shaper substitutes a hyphen between Hebrew words with maqaf at draw time — if the
  fonts lack that glyph you get a missing-glyph box. All generators carry `־` in CHARS.
- **Parasha math in `source/Parasha.mc`** (port of pyluach's algorithm — attribution in
  [CREDITS.md](CREDITS.md)) is verified 0 mismatches vs pyluach (every day 2020–2090) AND
  hebcal.com (2026–2029), both Israel and diaspora — after any change rerun BOTH
  `tools/verify/verify_parsha.py` (vs pyluach) and `tools/verify/crosscheck_hebcal.py` (vs the
  saved hebcal feeds in `tools/verify/hebcal_fixtures/`, offline). Don't "simplify".
- **Glance fonts must be `scope="glance"`** in fonts.xml — the glance process cannot access
  normal `Rez` symbols.
- **No runtime `System.*` calls in glance code** — tiered-glance CIQ 3.4 devices (fr55,
  instinct2) reject the whole app at install ("Unsupported app was removed"). Shape
  decisions are compile-time: `GlanceShape` has `(:glance,:roundGlance)` /
  `(:glance,:flatGlance)` variants picked via `excludeAnnotations` in monkey.jungle
  (semi-octagon MIP Instincts = flat/flush-right; everything else incl. Instinct 3
  AMOLED = round, 20%-width date inset capped against day-letter collision).
- **Parasha page (non-Solar): header at 28%h, name at 53%h** — lower values clip the
  header on fr55's round top edge (user-reported). Solar branch has its own layout.
- Per-device font buckets are wired in [monkey.jungle](monkey.jungle) via `resourcePath`;
  later paths override earlier (icon overlays, fenix7's 63px-glance fonts).
- Hebrew date math in `source/HebrewDate.mc` is Reingold–Dershowitz with all four dechiyot,
  verified 0 mismatches over 50 years vs pyluach. Don't "simplify" it.
- The glance's calendar icon is system-drawn and cannot be hidden/replaced (platform limit).

## Repo layout

Build-critical files stay at the project root where the SDK expects them:
`manifest.xml`, `monkey.jungle`, `developer_key.der`, `source/`, `resources/` (base) and the
per-device `resources-*` variant dirs. Everything else is sorted into:

| Dir | Contents |
|---|---|
| `source/` `resources/` `resources-*/` | App code + per-device resource buckets (the build) |
| `tools/fonts/` | `generate_fonts*.py`, `generate_icons.py` (regenerate bitmap fonts/icons into `resources-*`) |
| `tools/verify/` | `verify_parsha.py`, `crosscheck_hebcal.py` + `hebcal_fixtures/` (offline parasha checks) |
| `tools/sim/` | Simulator helpers: `capture.ps1` `capture2.ps1` (robust largest-window grab) `openshot.ps1` (tap glance band + capture) `click.ps1` `runshot.ps1` `retake_v15.ps1` `make_v15_shots.ps1` `scap.ps1` `sendkey.ps1` |
| `tools/store/` | Listing-image generators: `make_cover.py` `make_hero.py` `make_store_images.py` |
| `bin/` (gitignored) | Build output (`*.prg`, `HebrewCalendar.iq`), `shots/v15/`, `store_images/` |

## Source map

| File | Role |
|---|---|
| `source/HebrewCalendarApp.mc` | Entry: widget view + `(:glance)` glance view + onSettingsChanged |
| `source/HebrewCalendarView.mc` | Widget page 1: date (Solar uses sub-screen circle for day letter) |
| `source/ParashaView.mc` | Widget page 2: weekly parasha / festival fallback + ParashaDelegate. Non-Solar also draws the contextual line (Omer / next event) under the name |
| `source/OmerView.mc` | Widget page 3 — **SOLAR ONLY**: Omer in season, else next holiday / Rosh Chodesh. + OmerDelegate |
| `source/HebrewEvents.mc` | Omer name + next-holiday / Rosh-Chodesh countdown math (pure date math; NOT glance) |
| `source/Parasha.mc` | Parasha-of-the-week algorithm (verified; see Rules) |
| `source/AppSettings.mc` | App-settings access (israelSchedule, textColor) |
| `source/ParashaTest.mc` | `(:test)` unit tests (parasha + Omer + nextEvent) — `monkeyc --unit-test`; flag is `/t` in PowerShell, `-t` in Bash |
| `source/HebrewCalendarGlanceView.mc` | Glance: day letter left, RTL date right |
| `source/HebrewFonts.mc` | Bitmap font loading (glance + widget) |
| `source/HebrewDate.mc` | Hebrew calendar math + DOW letters (exposes `jd`); `omerDay`/`monthNameOf` |
| `source/DeviceInfo.mc` | Device/layout helpers (`isSolar` = screenW ≤ 176) |
| `resources/settings/` | properties.xml + settings.xml (Israel/diaspora, text color) |

Navigation: page 1 → page 2 via select/tap or swipe-left; back/swipe-right goes back.
On **Solar/Instinct 2 only** there is a page 3 (`OmerView`): page 2 select/swipe-left pushes
it; elsewhere page 2 is the last page (select returns to page 1). `ParashaView.drawDots`
takes a page count — 2 normally, 3 on Solar. Color setting applies everywhere except
Instinct (2-color MIP); the day-letter accent uses the chosen color when it isn't white.

**Omer / next-event line (Rules):** the contextual line shows the closest Jewish date —
`HebrewEvents.contextual(hd, israel)` decides: the Omer count (16 Nisan–5 Sivan, gematria +
בעומר) in season, else `nextEvent` = the soonest of a BROAD event set vs the next Rosh Chodesh,
with a gematria countdown (היום / מחר / בעוד … ימים). The event set (`_holidays`) is holidays +
**public fasts** (צום גדליה, עשרה בטבת, תענית אסתר, י״ז בתמוז) + **minor/festive** (הושענא רבה,
ט״ו בשבט, שושן פורים, פסח שני, ל״ג בעומר, ט״ו באב, שמיני עצרת/שמחת תורה — split 22/23 in diaspora,
combined 22 in Israel) + **modern Israeli days** (יום השואה/הזיכרון/העצמאות/ירושלים). Nominal
dates — Shabbat-postponement (nidche) is NOT applied (teaser, not a luach). **An event ON its
day beats the Omer count** (`contextual`), so the modern days/ל״ג בעומר/פסח שני that fall inside
the Omer still surface that day; other Omer days show the count. Countdowns use **gematria, not
Arabic digits** (fonts carry 0-9 but we don't use them). Non-Solar draws this under the parasha
name (chord-aware width — see round-clip rule); Solar shows it on page 3. Unit-tested
(`testOmerDay`, `testNextEvent`, `testContextual`) vs pyluach fixtures — keep green.

- **Round-screen text width:** any text low/high on a round screen must budget width with
  `DeviceInfo.usableWidthAtY(y)` (chord at that row), NOT `w-…` — else medium lines clip the
  bezel (the contextual line did; fixed).

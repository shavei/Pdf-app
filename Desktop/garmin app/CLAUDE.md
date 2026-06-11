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

Simulator screenshot/click helpers: `bin\capture.ps1`, `bin\click.ps1`, `bin\runshot.ps1`
(monkeydo → tap glance → tap → capture). fr255 has no touch — click the chrome START button.
In the sim, open the widget from the glance: click the display once (focus), then send ENTER.
On fr55 the sim boots to a black watchface — click the display, send {DOWN} to reach the
glance carousel, then {ENTER} for the widget.

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
  `generate_fonts*.py` scripts; `.fnt` lineHeight/base MUST come from `font.getmetrics()`
  or final letters (ן ך ף ץ ק) clip.
- **Join doubled parshiyot with maqaf `־` (U+05BE), never ASCII `-`.** Garmin's RTL
  shaper substitutes a hyphen between Hebrew words with maqaf at draw time — if the
  fonts lack that glyph you get a missing-glyph box. All generators carry `־` in CHARS.
- **Parasha math in `source/Parasha.mc`** (port of pyluach's algorithm) is verified
  0 mismatches vs pyluach (every day 2020–2090) AND hebcal.com (2026–2029), both
  Israel and diaspora — rerun `verify_parsha.py` after any change. Don't "simplify".
- **Glance fonts must be `scope="glance"`** in fonts.xml — the glance process cannot access
  normal `Rez` symbols.
- Per-device font buckets are wired in [monkey.jungle](monkey.jungle) via `resourcePath`;
  later paths override earlier (icon overlays, fenix7's 63px-glance fonts).
- Hebrew date math in `source/HebrewDate.mc` is Reingold–Dershowitz with all four dechiyot,
  verified 0 mismatches over 50 years vs pyluach. Don't "simplify" it.
- The glance's calendar icon is system-drawn and cannot be hidden/replaced (platform limit).

## Source map

| File | Role |
|---|---|
| `source/HebrewCalendarApp.mc` | Entry: widget view + `(:glance)` glance view + onSettingsChanged |
| `source/HebrewCalendarView.mc` | Widget page 1: date (Solar uses sub-screen circle for day letter) |
| `source/ParashaView.mc` | Widget page 2: weekly parasha / festival fallback + ParashaDelegate |
| `source/Parasha.mc` | Parasha-of-the-week algorithm (verified; see Rules) |
| `source/AppSettings.mc` | App-settings access (israelSchedule, textColor) |
| `source/ParashaTest.mc` | `(:test)` unit tests — `monkeyc --unit-test` + `monkeydo ... /t` |
| `source/HebrewCalendarGlanceView.mc` | Glance: day letter left, RTL date right |
| `source/HebrewFonts.mc` | Bitmap font loading (glance + widget) |
| `source/HebrewDate.mc` | Hebrew calendar math + DOW letters (exposes `jd`) |
| `source/DeviceInfo.mc` | Device/layout helpers (`isSolar` = screenW ≤ 176) |
| `resources/settings/` | properties.xml + settings.xml (Israel/diaspora, text color) |

Navigation: page 1 → page 2 via select/tap or swipe-left; page 2 pops on
back/select/swipe-right. Color setting applies everywhere except Instinct (2-color MIP);
the day-letter accent uses the chosen color when it isn't white.

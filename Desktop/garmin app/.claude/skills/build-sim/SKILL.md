---
name: build-sim
description: Build the Hebrew Calendar widget for a Garmin device and run it in the Connect IQ simulator. Use when the user says "build", "run in sim", "test on <device>", or wants to see the widget/glance on a device. Args: device id (default instinct3amoled45mm); "all" to compile every device as a build check; optional "shot" to capture a screenshot.
---

# Build & Simulate

Project root: `C:\Users\yosef\Desktop\garmin app` (run everything from there).

## Inputs
- `$DEVICE` — any product id in `manifest.xml` (95 as of v1.7.0; default `instinct3amoled45mm`). Fuzzy-match user
  wording: "solar" → instinct3solar45mm, "amoled 50" → instinct3amoled50mm, "fenix 7" →
  fenix7, "fenix 8" → fenix847mm, "venu 3" → venu3, "forerunner 55" → fr55, etc.
- `all` — compile every device (no simulator) to verify nothing broke.
- `shot` — after launching, capture a screenshot via `tools\sim\runshot.ps1` / `tools\sim\capture.ps1`.

Devices: read the `<iq:product id=...>` list from `manifest.xml` (e.g. "vivoactive 6" →
vivoactive6, "fenix 9 pro 47" → fenix9pro47mm, "instinct 2s" → instinct2s).

## Steps

1. **Build** (sandbox OK):
   ```powershell
   monkeyc -o "bin\$DEVICE.prg" -f monkey.jungle -y "developer_key.der" -d $DEVICE
   ```
   On compile errors: report file:line and stop. Warnings: report but continue.
   For `all`: DON'T loop per device — run the store-package build (compiles every device in one
   pass, background, ~4 min): `monkeyc -e -r -w -o "bin\HebrewCalendar.iq" -f monkey.jungle -y "developer_key.der"`
   and report `N OUT OF N DEVICES BUILT`. Never run monkeyc processes in parallel (shared default.jungle).

2. **Ensure simulator is running** — check `Get-Process simulator -ErrorAction SilentlyContinue`;
   if not running:
   ```powershell
   Start-Process "C:\Users\yosef\AppData\Roaming\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.1.0-2026-03-09-6a872a80b\bin\simulator.exe"
   Start-Sleep 5
   ```

3. **Launch** — MUST use `dangerouslyDisableSandbox: true` (sandboxed monkeydo writes to a
   %TEMP% the real simulator can't read; the build silently never loads):
   ```powershell
   monkeydo "bin\$DEVICE.prg" $DEVICE
   ```
   `monkeydo` blocks while streaming app output — run it in the background, then check output
   for runtime errors/crashes after a few seconds.

4. **If `shot`**: use `tools\sim\runshot.ps1` (monkeydo → tap glance → tap again → capture) or
   `tools\sim\capture.ps1` for a plain capture. To open the widget from the glance manually:
   click the display once (focus), then send ENTER. fr255 has no touch — click the chrome
   START button instead. fr55 boots to a black watchface: click the display, send {DOWN}
   to reach the glance carousel, then {ENTER} to open the widget.
   Save shots to `bin\shots\` and show the result to the user.

5. Report: build result, simulator status, any runtime output, screenshot path if taken.

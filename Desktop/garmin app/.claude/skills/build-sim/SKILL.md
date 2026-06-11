---
name: build-sim
description: Build the Hebrew Calendar widget for a Garmin device and run it in the Connect IQ simulator. Use when the user says "build", "run in sim", "test on <device>", or wants to see the widget/glance on a device. Args: device id (default instinct3amoled45mm); "all" to compile every device as a build check; optional "shot" to capture a screenshot.
---

# Build & Simulate

Project root: `C:\Users\yosef\Desktop\garmin app` (run everything from there).

## Inputs
- `$DEVICE` — one of the 16 ids below (default `instinct3amoled45mm`). Fuzzy-match user
  wording: "solar" → instinct3solar45mm, "amoled 50" → instinct3amoled50mm, "fenix 7" →
  fenix7, "fenix 8" → fenix847mm, "venu 3" → venu3, "forerunner 55" → fr55, etc.
- `all` — compile every device (no simulator) to verify nothing broke.
- `shot` — after launching, capture a screenshot via `bin\runshot.ps1` / `bin\capture.ps1`.

Devices: instinct3solar45mm instinct3amoled45mm instinct3amoled50mm instinct2 fr165m
fenix7 fr255 fr955 fr55 fenix847mm fr965 venu3 fr265 epix2 venu2 vivoactive5

## Steps

1. **Build** (sandbox OK):
   ```powershell
   monkeyc -o "bin\$DEVICE.prg" -f monkey.jungle -y "developer_key.der" -d $DEVICE
   ```
   On compile errors: report file:line and stop. Warnings: report but continue.
   For `all`: loop over the 16 ids, build each, summarize pass/fail per device, skip the simulator.

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

4. **If `shot`**: use `bin\runshot.ps1` (monkeydo → tap glance → tap again → capture) or
   `bin\capture.ps1` for a plain capture. To open the widget from the glance manually:
   click the display once (focus), then send ENTER. fr255 has no touch — click the chrome
   START button instead. fr55 boots to a black watchface: click the display, send {DOWN}
   to reach the glance carousel, then {ENTER} to open the widget.
   Save shots to `bin\shots\` and show the result to the user.

5. Report: build result, simulator status, any runtime output, screenshot path if taken.

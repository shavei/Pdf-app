---
name: connectiq-docs
description: Offline mirror of Garmin Connect IQ developer documentation (Toybox API, Monkey C language, core topics, UX guidelines, per-device specs). Use when answering any Connect IQ / Monkey C API question - lifecycle methods, WatchUi, glances, fonts, resources, permissions, store publishing, device capabilities - instead of fetching developer.garmin.com.
---

# Connect IQ Docs (offline mirror)

Fetched 2026-05-23 from developer.garmin.com, SDK 9.1.0 (matches our SDK). Each file has
frontmatter with its `source:` URL — cite it if the user wants the live page. If something
seems outdated or missing, fall back to WebFetch on the source URL.

NOTE: some files end with an "Our project's ..." section written by the mirror's original
author for a DIFFERENT app — ignore those sections; our project's facts live in CLAUDE.md
and MEMORY.md.

## Where to look (all under `references/`)

| Question about | Path |
|---|---|
| Toybox API (WatchUi, Graphics.Dc, System, Time, Attention, Complications, Weather, ...) | `connect-iq-docs/reference/api/` (23 modules; `index.md` lists them) |
| Monkey C language (types, containers, annotations like `(:glance)`, memory, exceptions) | `connect-iq-docs/reference/monkey-c/` |
| monkeyc/jungle build files, CLI, VS Code extension | `connect-iq-docs/reference/reference-guides/` (esp. `jungle-reference.md`, `command-line-setup.md`) |
| Core topics: glances, graphics, resources, layouts, input, manifest/permissions, persisting data, backgrounding, unit-testing, debugging, publishing-to-the-store | `connect-iq-docs/portal/core-topics/<topic>.md` |
| A specific device's screen/specs (e.g. fenix7.md, venu3.md, instinct3solar45mm.md) | `connect-iq-docs/portal/device-reference/<deviceid>.md` |
| UX guidelines (widget/glance design rules) | `connect-iq-docs/portal/ux-guidelines/` |
| System fonts/colors per device family ("personalities") | `connect-iq-docs/portal/personality-library/` |
| Sensor availability / walled-garden metrics | `catalogs/sensors.md` |

## Usage

1. Grep/Read the relevant file(s) above — they are concise summaries, fast to read.
2. Answer from the mirror; mention the `source:` URL when precision matters.
3. Refreshing the mirror: `connect-iq-docs/_refresh/` has the original fetch scripts.

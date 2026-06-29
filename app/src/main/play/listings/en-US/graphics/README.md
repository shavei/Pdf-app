# Store graphics

Drop Play Store listing graphics here. Gradle Play Publisher uploads any images
it finds in these subdirectories (PNG or JPEG, no transparency for the icon):

- `icon/` — high-res icon, 512 x 512 px
- `feature-graphic/` — 1024 x 500 px
- `phone-screenshots/` — 2–8 screenshots, 16:9 or 9:16
- `tablet-screenshots/` — optional

Example: `phone-screenshots/1.png`, `phone-screenshots/2.png`, …

These are required by Google Play before a listing can go live, but they are
binary assets and intentionally not committed here. Add them locally (or to a
release branch) before running the publish workflow with listing changes.

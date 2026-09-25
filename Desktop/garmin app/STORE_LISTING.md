# Connect IQ Store Listing — לוח עברי / Hebrew Calendar

Upload at: https://apps.garmin.com/developer/dashboard → app → **Upload New Version**

═══════════════════════════════════════════════════════════════════════
# ▶ COPY-PASTE FOR THIS UPLOAD — v1.8.0
═══════════════════════════════════════════════════════════════════════

(Store's latest is v1.7.0. In the upload form: choose the file below and type **1.8.0** in
"App Version". After publishing, check the store page shows THIS What's New — the dashboard
keeps the previous one unless you replace it.)

### 1. Binary
`bin\HebrewCalendar.iq`  (built 2026-09-25, 1.8.0, 95 devices / 147 part-number variants)

### 2. "What's New" — paste into the version-notes field

**English:**
```
Move between pages with the UP and DOWN buttons: DOWN goes to the next page, UP goes back. Start, Back and touch work as before.
```

**Hebrew:**
```
מעבר בין הדפים בעזרת כפתורי UP ו-DOWN: כפתור DOWN עובר לדף הבא, ו-UP חוזר לדף הקודם. כפתורי START ו-BACK והמגע ממשיכים לעבוד כרגיל.
```

### 3. "Notes for the reviewer" field
```
v1.8.0 adds page navigation with the UP/DOWN buttons (BehaviorDelegate onNextPage/onPreviousPage): DOWN = next page, UP = previous page; at the first/last page the key does nothing. No other changes: same layouts, no new permissions, no GPS/internet/sensors.
```

### 4. Screenshots, hero, description
No change — the screen layouts are identical to v1.7.0 (gallery re-captured 2026-09-25).

═══════════════════════════════════════════════════════════════════════
# v1.7.0 — PUBLISHED 2026-09-25 (kept for reference)
═══════════════════════════════════════════════════════════════════════

(The dashboard shows v1.6.0 as the latest app version (confirmed 2026-09-25), so v1.7.0 is
the watch-support + text-fit update on top of it. In the upload form: choose the file below
and type **1.7.0** in "App Version".)

### 1. Binary
`bin\HebrewCalendar.iq`  (built 2026-09-24 after the text-fit pass, 1.7.0, 95 devices / 147 part-number variants, ~4 MB)

### 2. "What's New" — paste into the version-notes field

**English:**
```
Now on 95 Garmin watches! Added dozens of new models, including the vivoactive 6, Venu 4, every fenix 6, 7, 8 and 9, Forerunner 70, 165, 170, 570 and 970, Instinct E and Instinct Crossover, and many more. Text sizes and icons are tuned for every screen, so all text now fits perfectly on every watch.
```

**Hebrew:**
```
עכשיו על 95 שעוני Garmin! נוספה תמיכה בעשרות שעונים חדשים, ביניהם vivoactive 6, Venu 4, כל דגמי fenix 6, 7, 8 ו-9, Forerunner 70, 165, 170, 570 ו-970, Instinct E ו-Instinct Crossover, ועוד רבים. גדלי הטקסט והאייקונים הותאמו לכל מסך, כך שכל הטקסט מוצג במלואו בכל שעון.
```

### 3. "Notes for the reviewer" field
```
v1.7.0 adds support for 79 more watches (95 total). No new features beyond v1.6.0: per-screen bitmap font sizes and launcher icons for the new screen sizes, and layout refinements so every line of text fits on every screen — the glance date moves beside the year on the longest dates, Instinct glances are laid out clear of the sub-screen circle, and on the Instinct Crossover all text stays clear of the physical watch hands. Still no permissions, GPS, internet or sensors; all date math is on-device.
```

### 4. Screenshots — REPLACE the gallery (in `bin\store_images\`)
Re-captured 2026-09-25 from the final v1.7.0 code. **Garmin allows a maximum of 5 gallery
images.** Replace the current gallery (captured in June from older code) with these 5:
1. `1_venu3_date.jpg` — date page (Venu 3)
2. `2_epix2_omer.jpg` — parasha page with the Omer count (epix Gen 2)
3. `3_fr55_event.jpg` — parasha page with the Rosh Chodesh countdown (Forerunner 55)
4. `4_solar_omer.jpg` — the Instinct Omer page (Instinct 3 Solar)
5. `5_fr265_glance.jpg` — glance (Forerunner 265)

(Upload the `.jpg` files only; the matching `.png` files are just used to build the hero.)

### 5. Hero image — replace
`bin\store_images\hero_1440x720.png` (regenerated 2026-09-25 from the new screenshots).

### 6. Full description
Re-paste the EN and HE full description below — the **supported-watches line changed** (95 watches).

### 7. Fields that DON'T change
- App name, category (Widget → Lifestyle), short description, permissions (none),
  cover icon (`cover_500.png`, unchanged design).

═══════════════════════════════════════════════════════════════════════
# Canonical listing fields (reference)
═══════════════════════════════════════════════════════════════════════

## App name
- **English:** Hebrew Calendar
- **Hebrew (on-device):** לוח עברי

## Category
Widget → Lifestyle (matches the live listing — keep as is)

## Short description (one-liner)
**EN:** See today's Hebrew date at a glance — day, month, and year in Hebrew.
**HE:** התאריך העברי של היום במבט אחד — יום, חודש ושנה בעברית.

## Full description

### English
Hebrew Calendar shows today's Hebrew (Jewish) date right on your watch.

A clean glance gives you the day of the week and the full Hebrew date — day, month, and year — written in Hebrew letters with proper gematria. Open the widget for a larger, easy-to-read view, then swipe or tap to a second page for this week's Torah portion.

Features:
• Today's Hebrew date: day (gematria), month name, and year
• Hebrew day-of-the-week letter (א–ז)
• Weekly Torah portion (parasha) on the second page — shows the festival name on holidays
• Sefirat HaOmer count during the Omer (Pesach to Shavuot)
• Countdown to the closest upcoming Hebrew date — holiday, fast, Rosh Chodesh, or a special day (Tu BiShvat, Lag BaOmer, Yom HaAtzma'ut…); on Instinct/Solar it gets its own page
• Choose your reading schedule: Israel or Diaspora
• Pick your text color
• Fully Hebrew text, rendered with crisp custom Hebrew fonts
• Glance support — your Hebrew date without opening anything
• Lightweight and battery-friendly: no GPS, no internet, no permissions

Supported watches (95): fenix 6, 7, 8 and 9 (all sizes, incl. Pro / Solar / X / S), fenix E, epix (Gen 2) and epix Pro, Enduro and Enduro 3, MARQ (Gen 1 and 2), Forerunner 55, 70, 165, 170, 255 / 255S, 265 / 265S, 570, 945 LTE, 955, 965 and 970, Venu 2 / 2S / 2 Plus, Venu 3 / 3S, Venu 4, Venu Sq 2, Venu X1, vivoactive 5 and 6, Instinct 2 / 2S / 2X, Instinct 3, Instinct E, Instinct Crossover (incl. AMOLED), Descent, Approach S50 / S70, and D2 — including their Solar, Sapphire, quatix and tactix editions.

Parasha calculation is based on the open-source pyluach library (MIT License) and was verified against Hebcal.com (CC BY 4.0).

### Hebrew
לוח עברי מציג את התאריך העברי של היום ישירות על השעון.

מבט מהיר (Glance) מציג את יום השבוע ואת התאריך העברי המלא — יום, חודש ושנה — באותיות עבריות עם גימטריה. פתחו את הווידג'ט לתצוגה גדולה וברורה יותר, והחליקו או הקישו לדף השני לפרשת השבוע.

תכונות:
• התאריך העברי של היום: יום (גימטריה), שם החודש ושנה
• אות יום השבוע (א–ז)
• פרשת השבוע בדף השני — בחגים מוצג שם החג
• ספירת העומר בתקופת הספירה (מפסח עד שבועות)
• ספירה לאחור ליום העברי הקרוב — חג, צום, ראש חודש או יום מיוחד (ט"ו בשבט, ל"ג בעומר, יום העצמאות…); בשעוני Instinct/Solar בדף נפרד
• בחירת לוח קריאה: ארץ ישראל או חו"ל
• בחירת צבע הטקסט
• טקסט עברי מלא עם גופנים עבריים מותאמים
• תמיכה ב-Glance — התאריך העברי בלי לפתוח כלום
• קל ונטול הרשאות: ללא GPS, ללא אינטרנט, ללא הרשאות

שעונים נתמכים (95): fenix 6, 7, 8 ו-9 (כל הגדלים, כולל Pro / Solar / X / S), fenix E, epix (Gen 2) ו-epix Pro, Enduro ו-Enduro 3, MARQ (דור 1 ו-2), Forerunner 55 / 70 / 165 / 170 / 255 / 255S / 265 / 265S / 570 / 945 LTE / 955 / 965 / 970, Venu 2 / 2S / 2 Plus, Venu 3 / 3S, Venu 4, Venu Sq 2, Venu X1, vivoactive 5 ו-6, Instinct 2 / 2S / 2X, Instinct 3, Instinct E, Instinct Crossover (כולל AMOLED), Descent, Approach S50 / S70 ו-D2 — כולל גרסאות Solar, Sapphire, quatix ו-tactix.

חישוב פרשת השבוע מבוסס על ספריית הקוד הפתוח pyluach (רישיון MIT) ואומת מול Hebcal.com (רישיון CC BY 4.0).

## Permissions
None. The app uses no GPS, sensors, internet, or stored data.

## Attribution (see CREDITS.md)
- Parasha algorithm: port of **pyluach** — © 2014 Meir S. List, MIT License.
- Verified against **Hebcal.com** — CC BY 4.0. No Hebcal data or API calls ship in the app.

═══════════════════════════════════════════════════════════════════════
# Version history — "What's new" archive
═══════════════════════════════════════════════════════════════════════

## v1.8.0 — 2026-09-25
EN/HE: see "COPY-PASTE FOR THIS UPLOAD — v1.8.0" at the top (UP/DOWN page navigation).

## v1.7.0 — 2026-09-24
EN/HE: see "COPY-PASTE FOR THIS UPLOAD" at the top (79 more watches, 95 total).

## v1.6.0 — 2026-06-17
EN: Now with the Omer count and a countdown to the next date on the Hebrew calendar! During Sefirat HaOmer (Pesach to Shavuot) the parasha page shows the day's count; otherwise it counts down to the closest upcoming day — holiday, fast, Rosh Chodesh, or a special day like Tu BiShvat, Lag BaOmer or Yom HaAtzma'ut. On Instinct and Solar watches this gets its own new page.
HE: עכשיו עם ספירת העומר וספירה לאחור לתאריך הבא בלוח העברי! בתקופת הספירה (מפסח עד שבועות) דף הפרשה מציג את ספירת היום; בשאר השנה הוא סופר לאחור ליום הקרוב הבא — חג, צום, ראש חודש או יום מיוחד כמו ט"ו בשבט, ל"ג בעומר או יום העצמאות. בשעוני Instinct ו-Solar זה מקבל דף חדש משלו.

## v1.5.0 — 2026-06-14
EN: New weekly Torah portion page! Swipe or tap from the date to see this week's parasha (with festival names on holidays). Plus new settings: choose the Israel or Diaspora reading schedule, and pick your text color. Also sharper layout — the glance date no longer clips on round watches.
HE: דף פרשת השבוע החדש! החליקו או הקישו מהתאריך כדי לראות את פרשת השבוע (ובחגים — שם החג). בנוסף, הגדרות חדשות: בחירת לוח הקריאה (ארץ ישראל או חו"ל) ובחירת צבע הטקסט. גם הפריסה שופרה — התאריך ב-Glance כבר לא נחתך בשעונים העגולים.

## v1.4.0 — 2026-06-11
EN: Added support for the Forerunner 55, with text sizes and a launcher icon tuned for its screen.
HE: נוספה תמיכה ב-Forerunner 55, עם גדלי טקסט ואייקון מותאמים למסך שלו.

## v1.2.0 (published as v1.3.0) — 2026-06-11
EN: Now on 11 more watches! Added support for Instinct 2 (incl. Solar / Dual Power / dēzl Edition), fenix 7, fenix 8 (47mm), epix (Gen 2), Forerunner 255, 265, 955 and 965, Venu 2, Venu 3, and vivoactive 5 — with text and icons tuned for every screen.
HE: עכשיו על עוד 11 שעונים! נוספה תמיכה ב-Instinct 2 (כולל Solar / Dual Power / dēzl), fenix 7, fenix 8 (47 מ"מ), epix (Gen 2), Forerunner 255 / 265 / 955 / 965, Venu 2, Venu 3 ו-vivoactive 5 — עם גדלי טקסט ואייקונים מותאמים לכל מסך.

## v1.1.0 / v1.0.0
v1.1.0: accurate Hebrew date and day-of-week on all watches; larger, clearer text.
v1.0.0: initial release.

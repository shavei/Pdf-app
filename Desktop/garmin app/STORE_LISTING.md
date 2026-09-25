# Connect IQ Store Listing — לוח עברי / Hebrew Calendar

Upload at: https://apps.garmin.com/developer/dashboard → app → **Upload New Version**

═══════════════════════════════════════════════════════════════════════
# ▶ COPY-PASTE FOR THIS UPLOAD — v1.7.0
═══════════════════════════════════════════════════════════════════════

(v1.7.0 includes everything from 1.6.0. If 1.6.0 was never uploaded, this upload
replaces it — the What's New below covers both.)

### 1. Binary
`bin\HebrewCalendar.iq`  (built 2026-09-24 after the text-fit pass, 1.7.0, 95 devices / 147 part-number variants, ~4 MB)

### 2. "What's New" — paste into the version-notes field

**English:**
```
Now on 95 Garmin watches! Added the vivoactive 6, Venu 4, Venu 2S / 2 Plus / 3S / Sq 2 / X1, Forerunner 70, 165, 170, 255S, 265S, 570, 945 LTE and 970, every fenix 6, 7, 8 and 9 model, fenix E, epix Pro, Enduro, MARQ, Instinct 2S / 2X / E / Crossover, Descent, Approach and D2 — with text and icons tuned for every screen, and text that now fits perfectly on every watch. Also includes the Sefirat HaOmer count and a countdown to the next holiday, fast or Rosh Chodesh.
```

**Hebrew:**
```
עכשיו על 95 שעוני Garmin! נוספה תמיכה ב-vivoactive 6‏, Venu 4‏, Venu 2S / 2 Plus / 3S / Sq 2 / X1‏, Forerunner 70 / 165 / 170 / 255S / 265S / 570 / 945 LTE / 970‏, כל דגמי fenix 6, 7, 8 ו-9‏, fenix E‏, epix Pro‏, Enduro‏, MARQ‏, Instinct 2S / 2X / E / Crossover‏, Descent‏, Approach ו-D2 — עם גדלי טקסט ואייקונים מותאמים לכל מסך, וטקסט שנכנס בדיוק בכל שעון. כולל גם ספירת העומר וספירה לאחור לחג, לצום או לראש החודש הבא.
```

### 3. "Notes for the reviewer" field
```
v1.7.0 adds support for 79 more watches (95 total). No new features beyond 1.6.0: per-screen bitmap font sizes and launcher icons for the new screen sizes, and layout refinements so every line of text fits on every screen — the glance date moves beside the year on the longest dates, Instinct glances are laid out clear of the sub-screen circle, and on the Instinct Crossover all text stays clear of the physical watch hands. Still no permissions, GPS, internet or sensors; all date math is on-device.
```

### 4. Screenshots
No change needed — keep the current 5 gallery images (`bin\store_images\1_*.jpg`–`5_*.jpg`).

### 5. Full description
Re-paste the EN and HE full description below — the **supported-watches line changed**.

### 6. Fields that DON'T change
- App name, category (Widget → Lifestyle), short description, permissions (none),
  cover icon (`cover_500.png`), hero image.

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

שעונים נתמכים (95): fenix 6, 7, 8 ו-9 (כל הגדלים, כולל Pro / Solar / X / S)‏, fenix E‏, epix (Gen 2) ו-epix Pro‏, Enduro ו-Enduro 3‏, MARQ (דור 1 ו-2)‏, Forerunner 55 / 70 / 165 / 170 / 255 / 255S / 265 / 265S / 570 / 945 LTE / 955 / 965 / 970‏, Venu 2 / 2S / 2 Plus‏, Venu 3 / 3S‏, Venu 4‏, Venu Sq 2‏, Venu X1‏, vivoactive 5 ו-6‏, Instinct 2 / 2S / 2X‏, Instinct 3‏, Instinct E‏, Instinct Crossover (כולל AMOLED)‏, Descent‏, Approach S50 / S70 ו-D2 — כולל גרסאות Solar‏, Sapphire‏, quatix ו-tactix.

חישוב פרשת השבוע מבוסס על ספריית הקוד הפתוח pyluach (רישיון MIT) ואומת מול Hebcal.com (רישיון CC BY 4.0).

## Permissions
None. The app uses no GPS, sensors, internet, or stored data.

## Attribution (see CREDITS.md)
- Parasha algorithm: port of **pyluach** — © 2014 Meir S. List, MIT License.
- Verified against **Hebcal.com** — CC BY 4.0. No Hebcal data or API calls ship in the app.

═══════════════════════════════════════════════════════════════════════
# Version history — "What's new" archive
═══════════════════════════════════════════════════════════════════════

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
HE: עכשיו על עוד 11 שעונים! נוספה תמיכה ב-Instinct 2 (כולל Solar / Dual Power / dēzl)‏, fenix 7‏, fenix 8 (47 מ"מ)‏, epix (Gen 2)‏, Forerunner 255 / 265 / 955 / 965‏, Venu 2‏, Venu 3 ו-vivoactive 5 — עם גדלי טקסט ואייקונים מותאמים לכל מסך.

## v1.1.0 / v1.0.0
v1.1.0: accurate Hebrew date and day-of-week on all watches; larger, clearer text.
v1.0.0: initial release.

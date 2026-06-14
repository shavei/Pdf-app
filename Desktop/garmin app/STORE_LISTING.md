# Connect IQ Store Listing — לוח עברי / Hebrew Calendar

Upload at: https://apps.garmin.com/developer/dashboard → app → **Upload New Version**

═══════════════════════════════════════════════════════════════════════
# ▶ COPY-PASTE FOR THIS UPLOAD — v1.5.0
═══════════════════════════════════════════════════════════════════════

### 1. Binary
`bin\HebrewCalendar.iq`  (built 2026-06-14, 1.5.0, 26 device variants)

### 2. "What's New" — paste into the version-notes field

**English:**
```
New weekly Torah portion page! Swipe or tap from the date to see this week's parasha (with festival names on holidays). Plus new settings: choose the Israel or Diaspora reading schedule, and pick your text color. Also sharper layout — the glance date no longer clips on round watches.
```

**Hebrew:**
```
דף פרשת השבוע החדש! החליקו או הקישו מהתאריך כדי לראות את פרשת השבוע (ובחגים — שם החג). בנוסף, הגדרות חדשות: בחירת לוח הקריאה (ארץ ישראל או חו"ל) ובחירת צבע הטקסט. גם הפריסה שופרה — התאריך ב-Glance כבר לא נחתך בשעונים העגולים.
```

### 3. "Notes for the reviewer" field
```
v1.5.0 adds a second widget page (weekly parasha / festival), two app settings (Israel-vs-Diaspora schedule and text color), and layout fixes. No new permissions; still no GPS/internet. The parasha schedule is computed on-device (a port of the open-source pyluach library, MIT) and verified against pyluach and hebcal.com for both schedules.
```

### 4. Screenshots — replace the gallery (in `bin\store_images\`)
**Garmin allows a maximum of 5 gallery images.** Upload these 5 (styled cards —
watch cut-out on a gradient with a soft shadow, matching the hero):
1. `1_venu3_date.jpg` — date page (Venu 3, AMOLED)
2. `2_fr965_parasha.jpg` — **NEW** parasha page (FR965, AMOLED)
3. `3_instinct2_date.jpg` — date page (Instinct 2)
4. `4_fenix7_parasha.jpg` — **NEW** parasha page (fenix 7, MIP)
5. `5_fr265_glance.jpg` — glance (FR265)

(The matching `*.png` files are clean white-bg twins used only to build the
hero — do not upload those.)

### 5. Hero image (if the store asks / to refresh)
`bin\store_images\hero_1440x720.png` (regenerated, now shows the parasha page)

### 6. Fields that DO change this release
- **Full description:** updated below to list the parasha page + settings + attribution.
  Re-paste the EN and HE full description (section "Full description" below).

### 7. Fields that DON'T change
- App name, category (Widget → Lifestyle), short description, permissions (none),
  cover icon (`cover_500.png`), supported-watch list (no new devices in 1.5.0).

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
• Choose your reading schedule: Israel or Diaspora
• Pick your text color
• Fully Hebrew text, rendered with crisp custom Hebrew fonts
• Glance support — your Hebrew date without opening anything
• Lightweight and battery-friendly: no GPS, no internet, no permissions

Supported watches: Instinct 2 (incl. Solar / Dual Power / dēzl Edition), Instinct 3 (Solar 45mm, AMOLED 45mm & 50mm), Forerunner 55, Forerunner 165 Music, Forerunner 255, 265, 955 and 965, fenix 7, fenix 8 (47mm), epix (Gen 2), Venu 2, Venu 3, and vivoactive 5.

Parasha calculation is based on the open-source pyluach library (MIT License) and was verified against Hebcal.com (CC BY 4.0).

### Hebrew
לוח עברי מציג את התאריך העברי של היום ישירות על השעון.

מבט מהיר (Glance) מציג את יום השבוע ואת התאריך העברי המלא — יום, חודש ושנה — באותיות עבריות עם גימטריה. פתחו את הווידג'ט לתצוגה גדולה וברורה יותר, והחליקו או הקישו לדף השני לפרשת השבוע.

תכונות:
• התאריך העברי של היום: יום (גימטריה), שם החודש ושנה
• אות יום השבוע (א–ז)
• פרשת השבוע בדף השני — בחגים מוצג שם החג
• בחירת לוח קריאה: ארץ ישראל או חו"ל
• בחירת צבע הטקסט
• טקסט עברי מלא עם גופנים עבריים מותאמים
• תמיכה ב-Glance — התאריך העברי בלי לפתוח כלום
• קל ונטול הרשאות: ללא GPS, ללא אינטרנט, ללא הרשאות

שעונים נתמכים: Instinct 2 (כולל Solar / Dual Power / dēzl)‏, Instinct 3 (Solar 45 מ"מ, AMOLED ‏45/50 מ"מ)‏, Forerunner 55‏, Forerunner 165 Music‏, Forerunner 255 / 265 / 955 / 965‏, fenix 7‏, fenix 8 (47 מ"מ)‏, epix (Gen 2)‏, Venu 2‏, Venu 3 ו-vivoactive 5.

חישוב פרשת השבוע מבוסס על ספריית הקוד הפתוח pyluach (רישיון MIT) ואומת מול Hebcal.com (רישיון CC BY 4.0).

## Permissions
None. The app uses no GPS, sensors, internet, or stored data.

## Attribution (see CREDITS.md)
- Parasha algorithm: port of **pyluach** — © 2014 Meir S. List, MIT License.
- Verified against **Hebcal.com** — CC BY 4.0. No Hebcal data or API calls ship in the app.

═══════════════════════════════════════════════════════════════════════
# Version history — "What's new" archive
═══════════════════════════════════════════════════════════════════════

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

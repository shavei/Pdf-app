# Feature Ideas — Hebrew Calendar Widget

Research-backed roadmap, written after **v1.5.0** (2026-06-14).

> **Status (2026-09-25):** Tier 1 is **built** — Sefirat HaOmer, the holiday countdown and
> Rosh Chodesh were added in v1.6.0 (molad time not added; released in v1.6.0). v1.7.0 expanded support from 16 to
> **95 watches** with every screen text-fit checked. v1.6.0 is the latest on the store; v1.7.0 is the next upload.
> Still open below: Tier 2 (date browsing, Daf Yomi) and the separate-product ideas.

> Sourced from a deep-research pass (2026-06-14): 67 claims across 30 sources;
> competitor survey, Connect IQ feasibility, and app-store/ratings dynamics.
> Load-bearing facts were adversarially verified high-confidence (not refuted).

---

## TL;DR

Ship **pure calendar-math** enrichments that reinforce the existing date + parasha core.
Leave the location-dependent **zmanim / candle-lighting** category alone — it's already
owned by a dedicated competitor, it's the #1 reliability-complaint magnet on Garmin, and
it's exactly what was deliberately removed from this app.

**Recommended next update:** Sefirat HaOmer · Holiday countdown · Rosh Chodesh / molad.
All three: zero location, zero new permissions, tiny memory, same risk class as the
existing parasha page.

---

## The key strategic finding

The zmanim/candle-lighting niche is **already owned** by a dedicated competitor:
**"Jewish Prayer Times"** (Slipperybee, v4.0.0) on Connect IQ. It ships the full halachic
suite (Dawn/Alot, Candle Lighting, Sunset, Chatzot, Mincha Gedola → Tzeit) built on
KosherJava — **plus** Hebrew date, day of the Omer, current holiday, and a prayer-direction
compass. Its reviews report **GPS/location failures and sunset-computation bugs**.

Implications for this app:
- The "headline" feature users *think* they want (candle-lighting) is the one a competitor
  already does comprehensively, is the biggest source of 1-star reliability reviews, and is
  what this app intentionally removed.
- On Garmin, 1-star reviews from broken features **persist forever and can't be removed** —
  so the bar for shipping a location feature is very high.
- This app's competitive edge is **correct, minimalist, zero-config, works-on-all-95-devices**
  date + parasha. Lean into that, not away from it.

---

## Recommended for the next update (Tier 1)

All pure calendar math. No location, no new permissions, small memory, same feasibility
class as the existing parasha page (which is verified against pyluach + hebcal).

### 1. Sefirat HaOmer (Omer counter) — ✅ added in v1.6.0
- **What:** During the 49-day Omer period (Pesach → Shavuot), show the count
  (e.g. `Day 33 / ל״ג בעומר`).
- **Why:** Highest value-to-risk ratio researched. Self-contained computation — hebcal/pyluach
  ship `omer` as a standalone package (same lineage as the existing parasha math). Seasonal,
  so it's not permanent clutter. Competitors surface "day of the Omer" as a baseline.
- **Feasibility:** Pure date math off the existing Hebrew date. No location, no permission.
- **Suggested UX:** Auto-surface on the **existing parasha/festival page** during the Omer
  period rather than a permanent new page — keeps the minimalist feel.

### 2. Holiday awareness / next-holiday + "days until" — ✅ added in v1.6.0
- **What:** Show the upcoming holiday and a countdown (e.g. `ראש השנה · in 12 days`).
- **Why:** Holiday awareness is described as a *baseline expected* feature for this category.
- **Feasibility:** Festival computation already exists for the parasha fallback — extend it to
  look forward. Pure math, no location. Fits as enrichment of an existing screen.

### 3. Rosh Chodesh / molad indicator — ✅ Rosh Chodesh countdown added in v1.6.0 (molad not added)
- **What:** "Rosh Chodesh [month] in N days," optionally the molad time.
- **Why:** Very on-brand for a Hebrew-date purist app; small footprint.
- **Feasibility:** Pure calendar math. Natural third pure-math addition.

---

## Consider, but second-tier (Tier 2)

### 4. Hebrew date for any selected day (date browsing)
- Competitors have it; users interact with it (arrow/button navigation). Pure math.
- **Caveat:** Real cross-device pitfall — **hardware-button date-nav broke on Fenix** in a
  competitor app. Risk is input-handling across button-only devices (instinct2, fr55, fr255),
  not compute. Doable, but test on every button device before shipping.

### 5. Daf Yomi
- Feasible and zero-risk: one global daily counter from a fixed reference
  (Rosh Hashanah 5684 / Sept 11 1923). No location, no network. Devoted audience
  (Siyum HaShas drew 90,000+).
- Niche-ier than Omer/holiday, but the safe choice if a fourth optional page is wanted.

---

## The marquee-but-risky one: Candle-lighting / Shabbat times

Highest *requested* value, highest risk. **Recommendation: not in the next update.**

Feasibility, honestly:
- Garmin provides `Toybox.Weather.getSunset(location, date)` natively (API **3.3.0+**) — so a
  NOAA port isn't strictly required. But it needs a `Position.Location`, can return `null`, and
  **isn't available on the older CIQ-3.4 devices** in the fleet (instinct2, fr55).
- The pure-Monkey-C alternative (Meeus/NOAA solar math, as the SunCalc widget does across
  37 devices) works offline but **naive ports run ~2 min off** and have a classic
  "fold time-of-day into the Julian date" bug.
- Requires **re-adding the Positioning permission** that was deliberately removed — which the
  review guidelines say must be justified.
- The dedicated competitor already owns this and *still* gets sunset/GPS bug reports.

If ever done: single opt-in extra page, **3.3.0+ devices only**, sea-level sunset − 18 min,
stored last-known location, explicit "approximate" disclaimer — and only **after** the safe
pure-math wins have lifted ratings.

---

## Explicitly DO NOT add

- **Full zmanim suite** (30+ times, Magen Avraham/Gra method config, elevation handling) —
  literally the competitor's whole product and exactly what was removed. Bloat + config burden
  + 1-star magnet.
- **Prayer-direction compass** — location + sensor, niche, competitor turf.
- **Yahrzeit reminders** — needs user-entered dates, persistent storage, and notification
  scheduling; wrong complexity class for a glance/widget.
- **Watch-face / data-field rewrite** — a different *product type*, not an update to this widget.
  Possible future separate product, not this.
- **Anything adding `System.*` calls or heavy assets to the glance** — already known to silently
  reject the app on tiered-glance devices (instinct2/fr55); the glance has a hard 32 KB ceiling.

---

## Future: separate apps (not updates to this widget)

These are **different Connect IQ product types** — a new manifest type, lifecycle, and store
listing each. They are NOT bolt-ons to the Hebrew Calendar *widget*; they'd be standalone
second/third products. The big advantage: they **reuse the already-verified `HebrewDate.mc`
date math + bitmap-font engine** (the hard, hard-won parts), so the new work is mostly a new
view plus the type-specific lifecycle.

### A. Hebrew Date Watch Face
- **What:** A watch face showing today's Hebrew date on screen 24/7 — no opening a widget.
  Arguably the most-wanted form factor for "I just want to see the Hebrew date at a glance."
- **Reuse:** `HebrewDate.mc`, day-of-week letters, the per-device bitmap fonts.
- **New work / constraints:**
  - `type="watch-face"` app + `WatchFace`/`onUpdate` lifecycle.
  - **Low-power mode**: faces run in a restricted always-on mode (1 update/minute, no heavy
    draws, tight memory/battery budget). Design must degrade gracefully when not in high-power.
  - Layout per display class all over again (round/semi-octagon/AMOLED), plus a time + Hebrew
    date composition rather than the widget's full-screen date.
  - Separate store listing, screenshots, and review cycle.
- **Risk:** Moderate. The math is done; the watch-face lifecycle + always-on layout is the new
  surface area.

### B. Hebrew Date Data Field
- **What:** A data field users drop into their existing activity profiles / watch-face layouts
  to show the Hebrew date alongside their own stats.
- **Reuse:** Same `HebrewDate.mc` + fonts.
- **New work / constraints:**
  - `type="data-field"` (or `simple-data-field`) app + `DataField`/`compute`/`onUpdate`.
  - **Smallest memory ceiling of all types** (e.g. ~131 KB on some devices vs ~1 MB app) and a
    tiny, variable draw region — bitmap fonts and layout must fit a much smaller box.
  - Single field value/layout; no multi-page navigation.
  - Separate store listing + review cycle.
- **Risk:** Low-moderate compute, but the memory ceiling and tiny render area are the real
  constraints to design around.

**Sequencing:** Ship the Tier 1 pure-math wins in the widget first. Treat the watch face as the
most promising *next product* once the widget roadmap is settled — it has the broadest appeal and
reuses the most existing code.

---

## Why restraint (the ratings case)

- Feature bloat buries core functions and hurts usability/ratings.
- Significant updates that remove relied-on functions generate negative reviews.
- Reliability problems (crashes, slow perf, broken features) are a primary driver of low ratings.
- On Garmin specifically, old 1-star reviews persist across versions and can't be removed —
  shipping only features that work reliably across all 95 devices is the rating-safe path.
- Broad device support and timezone/Israel-vs-diaspora correctness are themselves valued —
  both already strengths of this app.

---

## Sources

**Competitors / category**
- Jewish Prayer Times (Connect IQ): <https://forums.garmin.com/developer/connect-iq/f/showcase/1202/widget-jewish-prayer-times>
- Jewish Prayer Times instructions (v4.0.0 feature list): <https://slipperybee.github.io/jewish-prayer-times-instructions/>
- Hebrew Calendar Daily and Shabbat Times (Connect IQ store): <https://apps.garmin.com/apps/85c97289-7664-422e-9fc6-b6059d11f4bc>
- Hebrew Cal Widget (Connect IQ showcase): <https://forums.garmin.com/developer/connect-iq/f/showcase/2073/hebrew-cal-widget>
- Calendar Watch Widget (Connect IQ store): <https://apps.garmin.com/en-US/apps/b770d325-23e0-4a08-855b-73f35d247f1c>

**Computation references**
- hebcal-go (omer / molad / sedra / zmanim packages): <https://github.com/hebcal/hebcal-go>
- KosherJava NOAACalculator: <https://github.com/KosherJava/zmanim/blob/master/src/main/java/com/kosherjava/zmanim/util/NOAACalculator.java>
- Fix to NOAA sunrise/sunset algorithm: <https://kosherjava.com/2008/04/13/fix-to-noaa-sunrisesunset-algorithm/>
- How accurate are candle-lighting times (Hebcal): <https://www.hebcal.com/home/94/how-accurate-are-candle-lighting-times>
- About our zmanim calculations (Chabad): <https://www.chabad.org/library/article_cdo/aid/3209349/jewish/About-Our-Zmanim-Calculations.htm>
- Daf Yomi (Wikipedia): <https://en.wikipedia.org/wiki/Daf_Yomi>

**Connect IQ feasibility**
- Toybox.Weather API (getSunset / getCurrentConditions): <https://developer.garmin.com/connect-iq/api-docs/Toybox/Weather.html>
- Toybox.Position API: <https://developer.garmin.com/connect-iq/api-docs/Toybox/Position.html>
- SunCalc (CIQ sunset/sunrise widget, 37 devices): <https://github.com/haraldh/SunCalc>
- Widget Glances announcement: <https://forums.garmin.com/developer/connect-iq/b/news-announcements/posts/widget-glances---a-new-way-to-present-your-data>
- Improve your Connect IQ app performance: <https://www.garmin.com/en-US/blog/developer/improve-your-app-performance/>
- Connect IQ App Review Guidelines: <https://developer.garmin.com/connect-iq/app-review-guidelines/>

**Ratings / product strategy**
- Dealing with superficial negative reviews (Garmin forums): <https://forums.garmin.com/developer/connect-iq/f/discussion/304635/dealing-with-superficial-negative-reviews>
- Avoiding feature bloat: <https://7t.ai/blog/stop-chasing-perfection-avoiding-feature-bloat-and-how-it-prevents-mobile-app-success/>
- Feature creep (Toptal): <https://www.toptal.com/designers/ux/feature-creep>
- Silent killers of app retention (OneSignal): <https://onesignal.com/blog/the-lifecycle-audit-6-silent-killers-of-mobile-retention/>

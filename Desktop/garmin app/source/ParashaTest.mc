import Toybox.Lang;
import Toybox.Test;
import Toybox.Time;
import Toybox.Time.Gregorian;

// Temporary unit tests for Parasha (run with monkeyc --unit-test + monkeydo -t).
// Expected values come from verify_parsha.py (pyluach + hebcal verified).
(:test)
function testParashaWeekly(logger as Test.Logger) as Boolean {
    // Shabbat 2026-06-13: Israel קרח (37), diaspora שלח (36)
    var jd = 2461205;
    var ri = Parasha.forShabbat(jd, true);
    Test.assert(ri != null);
    Test.assertEqual(ri[0], 37);
    var rd = Parasha.forShabbat(jd, false);
    Test.assert(rd != null);
    Test.assertEqual(rd[0], 36);
    logger.debug("isr=" + Parasha.displayName(jd, true)
        + " dia=" + Parasha.displayName(jd, false));
    return true;
}

(:test)
function testParashaDoubled(logger as Test.Logger) as Boolean {
    // Shabbat 2026-03-14: ויקהל-פקודי (21,22) both schedules
    var jd = 2461114;
    var r = Parasha.forShabbat(jd, true);
    Test.assert(r != null);
    Test.assertEqual(r.size(), 2);
    Test.assertEqual(r[0], 21);
    Test.assertEqual(r[1], 22);
    logger.debug("doubled=" + Parasha.displayName(jd, true));
    return true;
}

(:test)
function testParashaFestival(logger as Test.Logger) as Boolean {
    // Shabbat 2026-04-04 falls during Pesach: no weekly portion
    var jd = 2461135;
    Test.assert(Parasha.forShabbat(jd, true) == null);
    Test.assert(Parasha.forShabbat(jd, false) == null);
    Test.assertEqual(Parasha.festivalName(jd), "פסח");
    logger.debug("festival=" + Parasha.displayName(jd, true));
    return true;
}

(:test)
function testAppSettings(logger as Test.Logger) as Boolean {
    // Defaults from properties.xml: Israel schedule, white text.
    // NOTE: the sim persists stored settings in GARMIN\APPS\SETTINGS\*.SET
    // across installs (and even across device profiles) — delete those files
    // with the sim STOPPED if this fails after changing defaults.
    Test.assert(AppSettings.israelSchedule());
    Test.assertEqual(AppSettings.textColor(), 0xFFFFFF);
    return true;
}

// Omer count: 16 Nisan (1) .. 5 Sivan (49); Nisan=7, Iyar=8, Sivan=9.
(:test)
function testOmerDay(logger as Test.Logger) as Boolean {
    Test.assertEqual(HebrewDate.omerDay(7, 16), 1);   // 16 Nisan
    Test.assertEqual(HebrewDate.omerDay(7, 30), 15);  // 30 Nisan
    Test.assertEqual(HebrewDate.omerDay(8, 1), 16);   // 1 Iyar
    Test.assertEqual(HebrewDate.omerDay(8, 18), 33);  // Lag BaOmer
    Test.assertEqual(HebrewDate.omerDay(8, 29), 44);  // 29 Iyar
    Test.assertEqual(HebrewDate.omerDay(9, 5), 49);   // 5 Sivan
    Test.assertEqual(HebrewDate.omerDay(9, 6), 0);    // Shavuot — no count
    Test.assertEqual(HebrewDate.omerDay(7, 15), 0);   // Pesach — before count
    Test.assertEqual(HebrewDate.omerDay(1, 1), 0);    // Tishri — out of season
    Test.assertEqual(HebrewEvents.omerName(33), "ל״ג בעומר");
    return true;
}

// Helper: build HebrewDate for a Gregorian civil date (noon-safe).
function _hebFor(y as Number, m as Number, d as Number) as HebrewDate {
    return new HebrewDate(Gregorian.moment(
        {:year => y, :month => m, :day => d, :hour => 12}));
}

// nextEvent — soonest of the full event set (holidays, fasts, minor/festive,
// modern Israeli days) vs Rosh Chodesh. Expected values from pyluach.
(:test)
function testNextEvent(logger as Test.Logger) as Boolean {
    _assertEvent("2026-09-10", _hebFor(2026, 9, 10), "ראש השנה", 2, logger);     // Elul 28
    _assertEvent("2026-12-01", _hebFor(2026, 12, 1), "חנוכה", 4, logger);        // Kislev 21
    _assertEvent("2026-07-20", _hebFor(2026, 7, 20), "תשעה באב", 3, logger);     // Av 6
    _assertEvent("2026-06-14", _hebFor(2026, 6, 14), "ראש חודש תמוז", 1, logger);// Sivan 29
    // New event types:
    _assertEvent("2026-09-13", _hebFor(2026, 9, 13), "צום גדליה", 1, logger);    // Tishri 2 — fast
    _assertEvent("2026-10-02", _hebFor(2026, 10, 2), "הושענא רבה", 0, logger);   // Tishri 21 — today
    _assertEvent("2027-01-04", _hebFor(2027, 1, 4),  "ראש חודש שבט", 5, logger); // Tevet 25
    _assertEvent("2027-03-20", _hebFor(2027, 3, 20), "תענית אסתר", 2, logger);   // Adar II 11 (leap)
    return true;
}

function _assertEvent(tag as String, hd as HebrewDate, name as String,
                      days as Number, logger as Test.Logger) as Void {
    var ev = HebrewEvents.nextEvent(hd, true);
    Test.assert(ev != null);
    logger.debug(tag + " -> " + (ev[0] as String) + " in " + (ev[1] as Number));
    Test.assertEqual(ev[0] as String, name);
    Test.assertEqual(ev[1] as Number, days);
}

// contextual — Omer count wins in season, but an event ON its day wins over
// the count (so the modern Israeli days inside the Omer actually surface).
(:test)
function testContextual(logger as Test.Logger) as Boolean {
    // Plain Omer day → count.
    var a = HebrewEvents.contextual(_hebFor(2027, 5, 13), true);  // 6 Iyar = omer 21
    Test.assertEqual(a[0] as String, "omer");
    Test.assertEqual(a[1] as String, "כ״א בעומר");
    // Yom HaAtzmaut (5 Iyar) is inside the Omer but wins on its day.
    var b = HebrewEvents.contextual(_hebFor(2027, 5, 12), true);
    Test.assertEqual(b[0] as String, "event");
    Test.assertEqual(b[1] as String, "יום העצמאות");
    Test.assertEqual(b[2] as Number, 0);
    // Out of season → closest event (a fast here).
    var c = HebrewEvents.contextual(_hebFor(2026, 9, 13), true); // Tishri 2
    Test.assertEqual(c[0] as String, "event");
    Test.assertEqual(c[1] as String, "צום גדליה");
    return true;
}

(:test)
function testParashaHaazinu(logger as Test.Logger) as Boolean {
    // Shabbat 2025-10-04 (Shabbat before Sukkot 5786): האזינו (52)
    var jd = 2460953;
    var r = Parasha.forShabbat(jd, true);
    Test.assert(r != null);
    Test.assertEqual(r[0], 52);
    return true;
}

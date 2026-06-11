import Toybox.Lang;
import Toybox.Test;

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

(:test)
function testParashaHaazinu(logger as Test.Logger) as Boolean {
    // Shabbat 2025-10-04 (Shabbat before Sukkot 5786): האזינו (52)
    var jd = 2460953;
    var r = Parasha.forShabbat(jd, true);
    Test.assert(r != null);
    Test.assertEqual(r[0], 52);
    return true;
}

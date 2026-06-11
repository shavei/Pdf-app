import Toybox.Lang;

// Weekly Torah portion (parasha) for the Shabbat on or after a given day.
// Port of pyluach.parshios (Dr. Irv Bromberg's algorithm): a virtual reading
// sequence [וילך, האזינו, בראשית..וילך] walked Shabbat-by-Shabbat from Rosh
// Hashana, with six doubling rules and a festival-skip test.
// Verified by verify_parsha.py: 0 mismatches vs pyluach (every day 2020-2090)
// and vs hebcal.com (every Shabbat 2026-2029), Israel AND diaspora schedules.
// NOT (:glance) — widget memory only; the glance never shows the parasha.
class Parasha {

    // Torah order, 0 = בראשית. Spellings follow common Israeli usage.
    static var NAMES = [
        "בראשית", "נח", "לך לך", "וירא", "חיי שרה", "תולדות", "ויצא", "וישלח",
        "וישב", "מקץ", "ויגש", "ויחי", "שמות", "וארא", "בא", "בשלח", "יתרו",
        "משפטים", "תרומה", "תצוה", "כי תשא", "ויקהל", "פקודי", "ויקרא", "צו",
        "שמיני", "תזריע", "מצורע", "אחרי מות", "קדושים", "אמור", "בהר", "בחוקותי",
        "במדבר", "נשא", "בהעלותך", "שלח", "קרח", "חקת", "בלק", "פינחס", "מטות",
        "מסעי", "דברים", "ואתחנן", "עקב", "ראה", "שופטים", "כי תצא", "כי תבוא",
        "נצבים", "וילך", "האזינו", "וזאת הברכה"
    ] as Array<String>;

    // 0=Sunday .. 6=Shabbat
    private static function _dow(jd as Number) as Number {
        return (jd + 1) % 7;
    }

    private static function _shabbatOnOrAfter(jd as Number) as Number {
        return jd + (6 - _dow(jd)) % 7;
    }

    // Virtual reading sequence: וילך, האזינו, then בראשית..וילך
    private static function _seq(i as Number) as Number {
        if (i == 0) { return 51; }
        if (i == 1) { return 52; }
        return i - 2;
    }

    private static function _monthOrder(leap as Boolean) as Array<Number> {
        return leap
            ? [1, 2, 3, 4, 5, 13, 14, 7, 8, 9, 10, 11, 12]
            : [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as Array<Number>;
    }

    // Hebrew year containing jd
    private static function _yearOf(jd as Number) as Number {
        var yr = ((jd - 347997).toFloat() / 365.2468).toNumber() + 1;
        while (HebrewDate.hebrewNewYear(yr + 1) <= jd) { yr++; }
        while (HebrewDate.hebrewNewYear(yr) > jd)      { yr--; }
        return yr;
    }

    // JD of Hebrew date (y, m, d), Tishrei-based month numbering
    private static function _jdOf(y as Number, m as Number, d as Number,
                                  order as Array<Number>) as Number {
        var jd = HebrewDate.hebrewNewYear(y);
        for (var i = 0; i < order.size(); i++) {
            var mo = order[i] as Number;
            if (mo == m) { break; }
            jd += HebrewDate.hebrewDaysInMonth(mo, y);
        }
        return jd + d - 1;
    }

    // [month, day] of jd within known year y
    private static function _monthDay(jd as Number, y as Number,
                                      order as Array<Number>) as Array<Number> {
        var doy = jd - HebrewDate.hebrewNewYear(y);
        var elapsed = 0;
        for (var i = 0; i < order.size(); i++) {
            var mo = order[i] as Number;
            var ml = HebrewDate.hebrewDaysInMonth(mo, y);
            if (doy < elapsed + ml) {
                return [mo, doy - elapsed + 1];
            }
            elapsed += ml;
        }
        return [1, 1];
    }

    // Festival Shabbat with no weekly portion? Israel still reads on the
    // diaspora's second days (Tishrei 23 / Nisan 22 / Sivan 7).
    private static function _parshaless(m as Number, d as Number,
                                        israel as Boolean) as Boolean {
        if (israel and ((m == 1 and d == 23) or (m == 7 and d == 22)
                        or (m == 9 and d == 7))) {
            return false;
        }
        if (m == 1 and (d == 1 or d == 2 or d == 10 or (d >= 15 and d <= 23))) {
            return true;
        }
        if (m == 7 and d >= 15 and d <= 22) { return true; }
        if (m == 9 and (d == 6 or d == 7))  { return true; }
        return false;
    }

    // Parasha indices (1 or 2 entries) read on the Shabbat on-or-after jd,
    // or null when that Shabbat is a festival with no weekly portion.
    static function forShabbat(jdAny as Number, israel as Boolean) as Array<Number>? {
        var shab = _shabbatOnOrAfter(jdAny);
        var y = _yearOf(shab);
        var leap = HebrewDate.isHebrewLeapYear(y);
        var order = _monthOrder(leap);

        var rh = HebrewDate.hebrewNewYear(y);
        var pesachDow = _dow(_jdOf(y, 7, 15, order));
        var erevPesach = _jdOf(y, 7, 14, order);
        var av9 = _jdOf(y, 11, 9, order);
        var nextRhLate = _dow(HebrewDate.hebrewNewYear(y + 1)) >= 4;

        var cur = _shabbatOnOrAfter(rh);
        // RH on Thu/Sat: נצבים-וילך was doubled before RH, Shabbat Shuva reads האזינו
        var idx = (_dow(rh) >= 4) ? 1 : 0;

        while (cur <= shab) {
            var md = _monthDay(cur, y, order);
            if (_parshaless(md[0] as Number, md[1] as Number, israel)) {
                if (cur == shab) { return null; }
            } else {
                var p = _seq(idx);
                idx++;
                var dbl =
                    (p == 21 and (erevPesach - cur) / 7 < 3)            // ויקהל-פקודי
                    or ((p == 26 or p == 28) and !leap)                 // תזריע-מצורע, אחרי-קדושים
                    or (p == 31 and !leap
                        and (!israel or pesachDow != 6))                // בהר-בחוקותי
                    or (p == 38 and !israel and pesachDow == 4)         // חקת-בלק
                    or (p == 41 and (av9 - cur) / 7 < 2)                // מטות-מסעי
                    or (p == 50 and nextRhLate);                        // נצבים-וילך
                if (cur == shab) {
                    if (dbl) { return [p, _seq(idx)]; }
                    return [p];
                }
                if (dbl) { idx++; }
            }
            cur += 7;
        }
        return null; // unreachable: shab is always visited
    }

    // Name of the festival occupying a parasha-less Shabbat on-or-after jd.
    static function festivalName(jdAny as Number) as String {
        var shab = _shabbatOnOrAfter(jdAny);
        var y = _yearOf(shab);
        var order = _monthOrder(HebrewDate.isHebrewLeapYear(y));
        var md = _monthDay(shab, y, order);
        var m = md[0] as Number;
        var d = md[1] as Number;

        if (m == 1) {
            if (d <= 2)             { return "ראש השנה"; }
            if (d == 10)            { return "יום כיפור"; }
            if (d >= 15 and d <= 21) { return "סוכות"; }
            if (d == 22)            { return "שמיני עצרת"; }
            if (d == 23)            { return "שמחת תורה"; }
        }
        if (m == 7) { return "פסח"; }
        if (m == 9) { return "שבועות"; }
        return "";
    }

    // Display string for the Shabbat reading: parasha name (doubles joined
    // with a maqaf — the ASCII hyphen gets bidi-substituted to maqaf by the
    // renderer, so the fonts carry U+05BE and we use it directly), or the
    // festival name on a parasha-less Shabbat.
    static function displayName(jd as Number, israel as Boolean) as String {
        var r = forShabbat(jd, israel);
        if (r == null) {
            return festivalName(jd);
        }
        var s = NAMES[r[0] as Number] as String;
        if (r.size() > 1) {
            s = s + "־" + (NAMES[r[1] as Number] as String);
        }
        return s;
    }

    // True when the Shabbat on-or-after jd has a weekly portion.
    static function hasParasha(jd as Number, israel as Boolean) as Boolean {
        return forShabbat(jd, israel) != null;
    }
}

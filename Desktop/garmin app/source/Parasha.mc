import Toybox.Lang;

// Weekly Torah portion (parasha) for the Shabbat on or after a given day.
// Port of pyluach.parshios (Dr. Irv Bromberg's algorithm): a virtual reading
// sequence [וילך, האזינו, בראשית..וילך] walked Shabbat-by-Shabbat from Rosh
// Hashana, with six doubling rules and a festival-skip test.
// pyluach (c) 2014 Meir S. List, MIT License — see CREDITS.md for the full
// notice and the Hebcal verification credit.
// Verified by verify_parsha.py: 0 mismatches vs pyluach (every day 2020-2090)
// and vs hebcal.com (every Shabbat 2026-2029), Israel AND diaspora schedules.
//
// PERF (don't regress): instinct2's watchdog kills onUpdate if this is slow.
// hebrewNewYear is expensive, so the year's month lengths are prefix-summed
// ONCE into `starts` and the Shabbat walk uses plain arithmetic only.
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

    // Days in month mo given the year length — no hebrewNewYear recomputation
    // (the expensive part of HebrewDate.hebrewDaysInMonth).
    private static function _monthLen(mo as Number, yearLen as Number) as Number {
        if (mo == 2) { return (yearLen == 355 or yearLen == 385) ? 30 : 29; } // חשון
        if (mo == 3) { return (yearLen == 353 or yearLen == 383) ? 29 : 30; } // כסלו
        // 30-day months: תשרי שבט אדר א ניסן סיוון אב
        if (mo == 1 or mo == 5 or mo == 13 or mo == 7 or mo == 9 or mo == 11) {
            return 30;
        }
        return 29; // טבת אדר אדר ב אייר תמוז אלול
    }

    // Hebrew year containing jd
    private static function _yearOf(jd as Number) as Number {
        var yr = ((jd - 347997).toFloat() / 365.2468).toNumber() + 1;
        while (HebrewDate.hebrewNewYear(yr + 1) <= jd) { yr++; }
        while (HebrewDate.hebrewNewYear(yr) > jd)      { yr--; }
        return yr;
    }

    // Day-of-year (0-based from 1 Tishrei) of the start of order[i],
    // computed once per year: starts[i] = sum of lengths of months before i.
    private static function _monthStarts(order as Array<Number>,
                                         yearLen as Number) as Array<Number> {
        var n = order.size();
        var starts = new [n] as Array<Number>;
        var acc = 0;
        for (var i = 0; i < n; i++) {
            starts[i] = acc;
            acc += _monthLen(order[i] as Number, yearLen);
        }
        return starts;
    }

    // [month, day] for a 0-based day-of-year, via the cached starts table
    private static function _monthDayOf(doy as Number, order as Array<Number>,
                                        starts as Array<Number>) as Array<Number> {
        var i = order.size() - 1;
        while (i > 0 and (starts[i] as Number) > doy) { i--; }
        return [order[i] as Number, doy - (starts[i] as Number) + 1];
    }

    // Day-of-year of Hebrew date (m, d) via the starts table
    private static function _doyOf(m as Number, d as Number,
                                   order as Array<Number>,
                                   starts as Array<Number>) as Number {
        for (var i = 0; i < order.size(); i++) {
            if ((order[i] as Number) == m) {
                return (starts[i] as Number) + d - 1;
            }
        }
        return 0;
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

        // All year constants computed once — the walk below is cheap arithmetic.
        var rh = HebrewDate.hebrewNewYear(y);
        var yearLen = HebrewDate.hebrewNewYear(y + 1) - rh;
        var leap = HebrewDate.isHebrewLeapYear(y);
        var order = _monthOrder(leap);
        var starts = _monthStarts(order, yearLen);

        var pesachDoy = _doyOf(7, 15, order, starts);          // ניסן ט"ו
        var pesachDow = _dow(rh + pesachDoy);
        var erevPesachDoy = pesachDoy - 1;
        var av9Doy = _doyOf(11, 9, order, starts);
        var nextRhLate = _dow(rh + yearLen) >= 4;              // next RH Thu/Sat

        var shabDoy = shab - rh;
        var curDoy = _shabbatOnOrAfter(rh) - rh;
        // RH on Thu/Sat: נצבים-וילך was doubled before RH, Shabbat Shuva reads האזינו
        var idx = (_dow(rh) >= 4) ? 1 : 0;

        while (curDoy <= shabDoy) {
            var md = _monthDayOf(curDoy, order, starts);
            if (_parshaless(md[0] as Number, md[1] as Number, israel)) {
                if (curDoy == shabDoy) { return null; }
            } else {
                var p = _seq(idx);
                idx++;
                var dbl =
                    (p == 21 and (erevPesachDoy - curDoy) / 7 < 3)      // ויקהל-פקודי
                    or ((p == 26 or p == 28) and !leap)                 // תזריע-מצורע, אחרי-קדושים
                    or (p == 31 and !leap
                        and (!israel or pesachDow != 6))                // בהר-בחוקותי
                    or (p == 38 and !israel and pesachDow == 4)         // חקת-בלק
                    or (p == 41 and (av9Doy - curDoy) / 7 < 2)          // מטות-מסעי
                    or (p == 50 and nextRhLate);                        // נצבים-וילך
                if (curDoy == shabDoy) {
                    if (dbl) { return [p, _seq(idx)]; }
                    return [p];
                }
                if (dbl) { idx++; }
            }
            curDoy += 7;
        }
        return null; // unreachable: shab is always visited
    }

    // Name of the festival occupying a parasha-less Shabbat on-or-after jd.
    static function festivalName(jdAny as Number) as String {
        var shab = _shabbatOnOrAfter(jdAny);
        var y = _yearOf(shab);
        var rh = HebrewDate.hebrewNewYear(y);
        var yearLen = HebrewDate.hebrewNewYear(y + 1) - rh;
        var order = _monthOrder(HebrewDate.isHebrewLeapYear(y));
        var starts = _monthStarts(order, yearLen);
        var md = _monthDayOf(shab - rh, order, starts);
        var m = md[0] as Number;
        var d = md[1] as Number;

        if (m == 1) {
            if (d <= 2)              { return "ראש השנה"; }
            if (d == 10)             { return "יום כיפור"; }
            if (d >= 15 and d <= 21) { return "סוכות"; }
            if (d == 22)             { return "שמיני עצרת"; }
            if (d == 23)             { return "שמחת תורה"; }
        }
        if (m == 7) { return "פסח"; }
        if (m == 9) { return "שבועות"; }
        return "";
    }

    // Join 1-2 parasha indices with a maqaf (the ASCII hyphen gets
    // bidi-substituted to maqaf by the renderer, so the fonts carry U+05BE
    // and we use it directly).
    static function joinNames(r as Array<Number>) as String {
        var s = NAMES[r[0] as Number] as String;
        if (r.size() > 1) {
            s = s + "־" + (NAMES[r[1] as Number] as String);
        }
        return s;
    }

    // Display string for the Shabbat reading. NOTE: runs the full walk —
    // views that also need has/hasn't-parasha should call forShabbat once
    // and use joinNames/festivalName instead of calling this twice.
    static function displayName(jd as Number, israel as Boolean) as String {
        var r = forShabbat(jd, israel);
        if (r == null) {
            return festivalName(jd);
        }
        return joinNames(r);
    }
}

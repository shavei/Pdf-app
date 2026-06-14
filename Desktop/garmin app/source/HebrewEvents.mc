import Toybox.Lang;

// "What's next on the Hebrew calendar" helpers for the parasha page:
// the Omer count during its season, and the soonest upcoming holiday or
// Rosh Chodesh with a countdown. Pure date math — no location, no
// permission — same lineage as HebrewDate / Parasha.
// NOT (:glance) — widget page only (the glance never shows this).
class HebrewEvents {

    // ---- Omer ----------------------------------------------------------

    // Display name for omer day n (1..49): gematria + " בעומר", e.g. ל״ג בעומר.
    static function omerName(n as Number) as String {
        return HebrewDate.numberToGematria(n) + " בעומר";
    }

    // ---- Countdown phrasing -------------------------------------------

    // "היום" / "מחר" / "בעוד יומיים" / "בעוד <gematria> ימים".
    static function countdownText(days as Number) as String {
        if (days <= 0) { return "היום"; }
        if (days == 1) { return "מחר"; }
        if (days == 2) { return "בעוד יומיים"; }
        return "בעוד " + HebrewDate.numberToGematria(days) + " ימים";
    }

    // ---- Next event (holiday or Rosh Chodesh) --------------------------

    // [name, daysUntil] for the soonest event on/after hd, or null.
    static function nextEvent(hd as HebrewDate) as Array<Object>? {
        var todayJd = hd.jd;
        var bestName = null as String?;
        var bestJd = 0;

        // Fixed-date holidays across this Hebrew year and the next, so a
        // date late in Elul still sees Rosh Hashana of the coming year.
        for (var k = 0; k < 2; k++) {
            var yr = hd.year + k;
            var leap = HebrewDate.isHebrewLeapYear(yr);
            var rh = HebrewDate.hebrewNewYear(yr);
            var yearLen = HebrewDate.hebrewNewYear(yr + 1) - rh;
            var hols = _holidays(leap);
            for (var i = 0; i < hols.size(); i++) {
                var ev = hols[i] as Array;
                var jd = rh + _doy(ev[0] as Number, ev[1] as Number, leap, yearLen);
                if (jd >= todayJd and (bestName == null or jd < bestJd)) {
                    bestJd = jd;
                    bestName = ev[2] as String;
                }
            }
        }

        // Rosh Chodesh — always within ~30 days, derived from today's date.
        var rc = _roshChodesh(hd);
        if (rc != null) {
            var rcJd = todayJd + (rc[1] as Number);
            // Strict <: on a tie a real holiday wins over Rosh Chodesh.
            if (bestName == null or rcJd < bestJd) {
                bestJd = rcJd;
                bestName = rc[0] as String;
            }
        }

        if (bestName == null) { return null; }
        return [bestName, bestJd - todayJd] as Array<Object>;
    }

    // Major holidays as [projMonth, day, name]. Adar resolves to Adar II in
    // a leap year so Purim lands in the right month. Month numbering matches
    // HebrewDate: Tishri=1 .. Elul=12, Adar I=13, Adar II=14.
    private static function _holidays(leap as Boolean) as Array {
        var adar = leap ? 14 : 6;
        return [
            [1, 1,   "ראש השנה"],
            [1, 10,  "יום כיפור"],
            [1, 15,  "סוכות"],
            [3, 25,  "חנוכה"],
            [5, 15,  "ט״ו בשבט"],
            [adar, 14, "פורים"],
            [7, 15,  "פסח"],
            [8, 18,  "ל״ג בעומר"],
            [9, 6,   "שבועות"],
            [11, 9,  "תשעה באב"]
        ];
    }

    // [name, daysUntil] for the next Rosh Chodesh, or null when the upcoming
    // month is Tishri (that onset is Rosh Hashana, already a holiday).
    private static function _roshChodesh(hd as HebrewDate) as Array? {
        var m = hd.month;
        var d = hd.day;
        var leap = hd.isLeapYear;
        // Day 1 is itself Rosh Chodesh — announce the current month today.
        if (d == 1) {
            if (m == 1) { return null; }  // 1 Tishri = Rosh Hashana
            return ["ראש חודש " + HebrewDate.monthNameOf(m, leap), 0];
        }
        // Otherwise the next month's Rosh Chodesh onset is exactly 30-d days
        // away (the 30th of a full month, or the 1st of the next — both 30-d).
        var order = _monthOrder(leap);
        var idx = 0;
        for (var i = 0; i < order.size(); i++) {
            if ((order[i] as Number) == m) { idx = i; break; }
        }
        var nextM = order[(idx + 1) % order.size()] as Number;
        if (nextM == 1) { return null; }  // next onset is Rosh Hashana
        return ["ראש חודש " + HebrewDate.monthNameOf(nextM, leap), 30 - d];
    }

    private static function _monthOrder(leap as Boolean) as Array<Number> {
        return leap
            ? [1, 2, 3, 4, 5, 13, 14, 7, 8, 9, 10, 11, 12]
            : [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as Array<Number>;
    }

    // Cheap month length from the year length (no hebrewNewYear recompute).
    private static function _monthLen(mo as Number, yearLen as Number) as Number {
        if (mo == 2) { return (yearLen == 355 or yearLen == 385) ? 30 : 29; } // חשון
        if (mo == 3) { return (yearLen == 353 or yearLen == 383) ? 29 : 30; } // כסלו
        if (mo == 1 or mo == 5 or mo == 13 or mo == 7 or mo == 9 or mo == 11) {
            return 30;
        }
        return 29;
    }

    // 0-based day-of-year (from 1 Tishri) of Hebrew date (m, d).
    private static function _doy(m as Number, d as Number,
                                 leap as Boolean, yearLen as Number) as Number {
        var order = _monthOrder(leap);
        var acc = 0;
        for (var i = 0; i < order.size(); i++) {
            var mo = order[i] as Number;
            if (mo == m) { return acc + d - 1; }
            acc += _monthLen(mo, yearLen);
        }
        return acc + d - 1;
    }
}

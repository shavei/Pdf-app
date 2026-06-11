import Toybox.Lang;
import Toybox.Time;
import Toybox.Time.Gregorian;

// Converts Gregorian dates to Hebrew calendar dates
// (:glance) — required by glance view to display today's Hebrew date
(:glance)
class HebrewDate {

    static var MONTH_NAMES = [
        "תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר",
        "ניסן", "אייר", "סיוון", "תמוז", "אב", "אלול",
        "אדר א׳", "אדר ב׳"
    ] as Array<String>;

    static var ONES    = ["", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט"] as Array<String>;
    static var TENS    = ["", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ"] as Array<String>;
    static var HUNDREDS = ["", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק"] as Array<String>;

    var day        as Number  = 1;
    var month      as Number  = 1;
    var year       as Number  = 5785;
    var isLeapYear as Boolean = false;
    var dayOfWeek  as Number  = 0; // 0=Sun, 1=Mon, ... 6=Sat
    var jd         as Number  = 0; // Julian Day of this date (used by Parasha)

    static var DOW_LETTERS = ["א", "ב", "ג", "ד", "ה", "ו", "ז"] as Array<String>;

    function initialize(gregMoment as Time.Moment) {
        var info = Gregorian.info(gregMoment, Time.FORMAT_SHORT);
        // Gregorian.Info.day_of_week is 1-based (Sunday=1); DOW_LETTERS is 0-based.
        dayOfWeek = info.day_of_week - 1;
        jd = gregorianToJD(info.year, info.month, info.day);
        fromJD(jd);
    }

    function getDayOfWeekLetter() as String {
        return DOW_LETTERS[dayOfWeek] as String;
    }

    // Gregorian → Julian Day Number
    private function gregorianToJD(y as Number, m as Number, d as Number) as Number {
        var a = (14 - m) / 12;
        var yy = y + 4800 - a;
        var mm = m + 12 * a - 3;
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
    }

    // Days from the Hebrew epoch to 1 Tishri of year y, with the molad-zaken and
    // lo-ADU-rosh postponements folded into a single dechiya test
    // (Reingold & Dershowitz, "Calendrical Calculations").
    static function hebrewElapsedDays(y as Number) as Number {
        var m = (235 * y - 234) / 19;          // months elapsed
        var parts = 12084 + 13753 * m;          // molad in halakim
        var day = 29 * m + parts / 25920;
        // Combined molad-zaken + lo-ADU-rosh: delay one day when (3*(day+1)) mod 7 < 3.
        if ((3 * (day + 1)) % 7 < 3) {
            day++;
        }
        return day;
    }

    // Julian Day of 1 Tishri of Hebrew year y. Adds the two year-length dechiyot
    // (GaTaRaD and BeTUTaKPaT) so Cheshvan/Kislev lengths come out correct.
    static function hebrewNewYear(y as Number) as Number {
        var n1 = hebrewElapsedDays(y);
        var corr = 0;
        if (hebrewElapsedDays(y + 1) - n1 == 356) {        // GaTaRaD
            corr = 2;
        } else if (n1 - hebrewElapsedDays(y - 1) == 382) {  // BeTUTaKPaT
            corr = 1;
        }
        return 347998 + n1 + corr;
    }

    // Convert Julian Day to Hebrew date
    private function fromJD(jd as Number) as Void {
        // Use float division to avoid overflow for approximation
        var approxYear = ((jd - 347997).toFloat() / 365.2468).toNumber() + 1;

        // Fine-tune year (usually 0-1 iterations)
        var yr = approxYear;
        while (hebrewNewYear(yr + 1) <= jd) { yr++; }
        while (hebrewNewYear(yr) > jd)      { yr--; }

        year = yr;
        isLeapYear = isHebrewLeapYear(yr);

        // Find month: start from Tishri (1), iterate forward
        var mo = 1;
        var daysInYear = hebrewNewYear(yr + 1) - hebrewNewYear(yr);

        // Months in order starting from Tishri
        var monthOrder = isLeapYear
            ? [1, 2, 3, 4, 5, 13, 14, 7, 8, 9, 10, 11, 12]
            : [1, 2, 3, 4, 5,  6,  7, 8, 9, 10, 11, 12] as Array<Number>;

        var dayOfYear = jd - hebrewNewYear(yr); // 0-based
        var elapsed = 0;

        for (var i = 0; i < monthOrder.size(); i++) {
            var mLen = hebrewDaysInMonth(monthOrder[i], yr);
            if (dayOfYear < elapsed + mLen) {
                month = monthOrder[i];
                day = dayOfYear - elapsed + 1;
                return;
            }
            elapsed += mLen;
        }

        // Fallback
        month = 1;
        day = 1;
    }

    static function isHebrewLeapYear(y as Number) as Boolean {
        return ((7 * y) + 1) % 19 < 7;
    }

    static function hebrewDaysInMonth(mo as Number, y as Number) as Number {
        // Full (30-day) months: Tishri, Sivan, Av, Nisan, Tevet(no), Shvat, Adar-I
        // The variable months are Cheshvan and Kislev
        var yearLen = hebrewNewYear(y + 1) - hebrewNewYear(y);

        if (mo == 1)  { return 30; } // Tishri
        if (mo == 2)  { return (yearLen == 355 or yearLen == 385) ? 30 : 29; } // Cheshvan
        if (mo == 3)  { return (yearLen == 353 or yearLen == 383) ? 29 : 30; } // Kislev
        if (mo == 4)  { return 29; } // Tevet
        if (mo == 5)  { return 30; } // Shvat
        if (mo == 6)  { return 29; } // Adar (regular year)
        if (mo == 13) { return 30; } // Adar I (leap year)
        if (mo == 14) { return 29; } // Adar II (leap year)
        if (mo == 7)  { return 30; } // Nisan
        if (mo == 8)  { return 29; } // Iyar
        if (mo == 9)  { return 30; } // Sivan
        if (mo == 10) { return 29; } // Tammuz
        if (mo == 11) { return 30; } // Av
        if (mo == 12) { return 29; } // Elul
        return 29;
    }

    function getDayGematria() as String {
        return numberToGematria(day);
    }

    function getYearGematria() as String {
        return numberToGematria(year % 1000);
    }

    static function numberToGematria(n as Number) as String {
        if (n == 15) { return "ט״ו"; }
        if (n == 16) { return "ט״ז"; }

        var rem = n;
        var h = rem / 100; rem = rem - h * 100;
        var t = rem / 10;  rem = rem - t * 10;
        var o = rem;

        var result = (HUNDREDS[h] as String) + (TENS[t] as String) + (ONES[o] as String);
        var len = result.length();
        if (len == 1) {
            result = result + "׳";
        } else if (len > 1) {
            result = result.substring(0, len - 1) + "״" + result.substring(len - 1, len);
        }
        return result;
    }

    function getMonthName() as String {
        if (isLeapYear and month == 13) { return MONTH_NAMES[12] as String; }
        if (isLeapYear and month == 14) { return MONTH_NAMES[13] as String; }
        if (month >= 1 and month <= 12) { return MONTH_NAMES[month - 1] as String; }
        return "";
    }

    function getFullDateString() as String {
        return getDayGematria() + " " + getMonthName() + " " + getYearGematria();
    }
}

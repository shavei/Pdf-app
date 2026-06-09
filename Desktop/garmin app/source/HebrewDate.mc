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

    static var DOW_LETTERS = ["א", "ב", "ג", "ד", "ה", "ו", "ז"] as Array<String>;

    function initialize(gregMoment as Time.Moment) {
        var info = Gregorian.info(gregMoment, Time.FORMAT_SHORT);
        dayOfWeek = info.day_of_week;
        var jd = gregorianToJD(info.year, info.month, info.day);
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

    // Julian Day of 1 Tishri of Hebrew year y
    // Uses standard molad algorithm with postponement rules
    static function hebrewNewYear(y as Number) as Number {
        // Months elapsed since Hebrew epoch (molad of Tishri year 1)
        var m = (235 * y - 234) / 19;

        // Molad: 12084 halakim + 13753 halakim per month
        // Use floats to avoid 32-bit overflow (13753 * 71551 ~ 984M is fine,
        // but we do this in pieces to be safe)
        var parts = 12084 + 13753 * m;

        // Day offset from JD 347997
        var day = m * 29 + parts / 25920;

        // Day of week (0=Sun, 1=Mon, ... 6=Sat)
        // JD 347997 = Saturday (day 6), so:
        var dow = (day + 2) % 7; // 0=Sun

        // Apply postponement rules (dechiyot)
        // Rule 1: Molad Zaken — if molad >= 18h (parts % 25920 >= 19440), postpone
        var partsMod = parts - (parts / 25920) * 25920;
        if (partsMod >= 19440) {
            day++;
            dow = (dow + 1) % 7;
        }

        // Rule 2: Lo ADU Rosh — no RH on Sun(0), Wed(3), Fri(5)
        if (dow == 0 or dow == 3 or dow == 5) {
            day++;
        }

        return 347997 + day;
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

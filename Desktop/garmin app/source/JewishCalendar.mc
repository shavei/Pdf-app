import Toybox.Lang;
import Toybox.Time;

class JewishCalendar {

    static var PARASHIYOT = [
        "בראשית", "נח", "לך לך", "וירא", "חיי שרה",
        "תולדות", "ויצא", "וישלח", "וישב", "מקץ",
        "ויגש", "ויחי", "שמות", "וארא", "בא",
        "בשלח", "יתרו", "משפטים", "תרומה", "תצוה",
        "כי תשא", "ויקהל", "פקודי", "ויקרא", "צו",
        "שמיני", "תזריע", "מצורע", "אחרי מות", "קדושים",
        "אמור", "בהר", "בחוקותי", "במדבר", "נשא",
        "בהעלתך", "שלח", "קרח", "חקת", "בלק",
        "פינחס", "מטות", "מסעי", "דברים", "ואתחנן",
        "עקב", "ראה", "שופטים", "כי תצא", "כי תבוא",
        "נצבים", "וילך", "האזינו", "וזאת הברכה"
    ] as Array<String>;

    // [month(1=Tishri), day, name]
    // Using Tishri-based month numbering
    static var HOLIDAYS = [
        [1, 1,  "ראש השנה"],
        [1, 2,  "ראש השנה ב׳"],
        [1, 3,  "צום גדליה"],
        [1, 10, "יום כיפור"],
        [1, 15, "סוכות"],
        [1, 22, "שמיני עצרת"],
        [1, 23, "שמחת תורה"],
        [3, 25, "חנוכה"],
        [3, 26, "חנוכה"],
        [3, 27, "חנוכה"],
        [3, 28, "חנוכה"],
        [3, 29, "חנוכה"],
        [4, 1,  "חנוכה"],
        [4, 2,  "חנוכה"],
        [4, 3,  "חנוכה"],
        [4, 10, "עשרה בטבת"],
        [5, 15, "ט״ו בשבט"],
        [7, 13, "תענית אסתר"],
        [7, 14, "פורים"],
        [7, 15, "שושן פורים"],
        [7, 15, "פסח"],
        [7, 16, "פסח ב׳"],
        [7, 21, "שביעי של פסח"],
        [7, 22, "אחרון של פסח"],
        [8, 5,  "יום הזיכרון"],
        [8, 6,  "יום העצמאות"],
        [8, 18, "ל״ג בעומר"],
        [8, 28, "יום ירושלים"],
        [9, 6,  "שבועות"],
        [9, 7,  "שבועות ב׳"],
        [10, 17, "שבעה עשר בתמוז"],
        [11, 9, "תשעה באב"]
    ] as Array<Array>;

    var hebrewDate as HebrewDate;

    function initialize(hDate as HebrewDate) {
        hebrewDate = hDate;
    }

    function getCurrentParasha() as String {
        var weekOfYear = getDaysFromTishri() / 7;
        var idx = weekOfYear % 54;
        if (idx < 0) { idx = 0; }
        if (idx >= PARASHIYOT.size()) { idx = 0; }
        return PARASHIYOT[idx] as String;
    }

    function getDaysFromTishri() as Number {
        var m = hebrewDate.month;
        var d = hebrewDate.day;

        // Sum days of full months from Tishri to current month
        var monthOrder = hebrewDate.isLeapYear
            ? [1, 2, 3, 4, 5, 13, 14, 7, 8, 9, 10, 11, 12]
            : [1, 2, 3, 4, 5,  6,  7, 8, 9, 10, 11, 12] as Array<Number>;

        var elapsed = 0;
        for (var i = 0; i < monthOrder.size(); i++) {
            var mo = monthOrder[i] as Number;
            if (mo == m) { break; }
            elapsed += HebrewDate.hebrewDaysInMonth(mo, hebrewDate.year);
        }
        return elapsed + d - 1;
    }

    function getNextHoliday() as String {
        var currentDays = getDaysFromTishri();
        var bestName = "ראש השנה";
        var bestDiff = 999;

        var monthOrder = hebrewDate.isLeapYear
            ? [1, 2, 3, 4, 5, 13, 14, 7, 8, 9, 10, 11, 12]
            : [1, 2, 3, 4, 5,  6,  7, 8, 9, 10, 11, 12] as Array<Number>;

        for (var i = 0; i < HOLIDAYS.size(); i++) {
            var hol = HOLIDAYS[i] as Array;
            var holMonth = hol[0] as Number;
            var holDay   = hol[1] as Number;
            var holName  = hol[2] as String;

            // Find position of this month in year order
            var holDays = holDay - 1;
            for (var j = 0; j < monthOrder.size(); j++) {
                var mo = monthOrder[j] as Number;
                if (mo == holMonth) { break; }
                holDays += HebrewDate.hebrewDaysInMonth(mo, hebrewDate.year);
            }

            var diff = holDays - currentDays;
            if (diff > 0 and diff < bestDiff) {
                bestDiff = diff;
                bestName = holName;
            }
        }

        if (bestDiff == 999) { return "ראש השנה"; }
        if (bestDiff == 1)   { return "מחר: " + bestName; }
        return "עוד " + bestDiff.toString() + " ימים: " + bestName;
    }

    function getTodayHoliday() as String {
        for (var i = 0; i < HOLIDAYS.size(); i++) {
            var hol = HOLIDAYS[i] as Array;
            if ((hol[0] as Number) == hebrewDate.month and
                (hol[1] as Number) == hebrewDate.day) {
                return hol[2] as String;
            }
        }
        return "";
    }

    // Sefirat HaOmer: Nisan 16 (month 7) through Sivan 5 (month 9)
    function getOmerCount() as String {
        var m = hebrewDate.month;
        var d = hebrewDate.day;
        var omerDay = 0;

        if (m == 7 and d >= 16)      { omerDay = d - 15; }       // Nisan 16-30
        else if (m == 8)              { omerDay = 15 + d; }        // Iyar 1-29
        else if (m == 9 and d <= 5)   { omerDay = 44 + d; }        // Sivan 1-5

        if (omerDay <= 0 or omerDay > 49) { return ""; }

        var weeks = omerDay / 7;
        var days  = omerDay % 7;
        var result = "ספירת העומר: " + HebrewDate.numberToGematria(omerDay);
        if (weeks > 0 and days > 0) {
            result += "\n" + weeks.toString() + " שבועות ו-" + days.toString() + " ימים";
        } else if (weeks > 0) {
            result += "\n" + weeks.toString() + " שבועות שלמים";
        }
        return result;
    }
}

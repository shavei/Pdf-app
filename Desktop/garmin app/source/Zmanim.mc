import Toybox.Lang;
import Toybox.Math;
import Toybox.Position;
import Toybox.Time;
import Toybox.Time.Gregorian;

// Calculates halachic times (zmanim) based on GPS location
class Zmanim {

    var lat as Float;
    var lon as Float;
    var julianDay as Float;

    function initialize(moment as Time.Moment) {
        lat = 31.7683; // Default: Jerusalem
        lon = 35.2137;
        var info = Gregorian.info(moment, Time.FORMAT_SHORT);
        julianDay = calcJulianDay(info.year, info.month, info.day).toFloat();
    }

    function setLocation(latitude as Float, longitude as Float) as Void {
        lat = latitude;
        lon = longitude;
    }

    private function calcJulianDay(y as Number, m as Number, d as Number) as Number {
        var a = (14 - m) / 12;
        var yy = y + 4800 - a;
        var mm = m + 12 * a - 3;
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
    }

    // Calculate sunrise or sunset. type: 0=sunrise, 1=sunset
    // Returns time as minutes from midnight (local)
    private function calcSunTime(isSunset as Boolean) as Float {
        var D2R = Math.PI / 180.0;
        var R2D = 180.0 / Math.PI;

        // Day of year
        var n = julianDay - 2451545.0;

        // Mean longitude
        var L = 280.46 + 0.9856474 * n;
        L = L - 360.0 * (L / 360.0).toNumber().toFloat();
        if (L < 0) { L += 360.0; }

        // Mean anomaly
        var g = 357.528 + 0.9856003 * n;
        g = g - 360.0 * (g / 360.0).toNumber().toFloat();
        if (g < 0) { g += 360.0; }

        // Ecliptic longitude
        var lambda = L + 1.915 * Math.sin(g * D2R) + 0.020 * Math.sin(2.0 * g * D2R);

        // Obliquity
        var eps = 23.439 - 0.0000004 * n;

        // Right ascension
        var sinLambda = Math.sin(lambda * D2R);
        var RA = Math.atan2(Math.cos(eps * D2R) * sinLambda, Math.cos(lambda * D2R)) * R2D;
        RA = RA / 15.0;

        // Declination
        var sinDec = Math.sin(eps * D2R) * sinLambda;
        var dec = Math.asin(sinDec) * R2D;

        // Equation of time
        var EqT = L / 15.0 - RA;

        // Hour angle for sunrise/sunset (zenith = 90.833 degrees)
        var cosH = (Math.cos(90.833 * D2R) - Math.sin(lat * D2R) * Math.sin(dec * D2R)) /
                   (Math.cos(lat * D2R) * Math.cos(dec * D2R));

        if (cosH > 1.0 or cosH < -1.0) {
            return -1.0; // No sunrise/sunset
        }

        var H = Math.acos(cosH) * R2D;

        var utcTime = isSunset
            ? 12.0 + H - EqT - lon / 15.0
            : 12.0 - H - EqT - lon / 15.0;

        return utcTime * 60.0; // Return in minutes from UTC midnight
    }

    // Get UTC offset in minutes (simplified)
    private function getUtcOffsetMinutes() as Number {
        // Israel is UTC+2 (UTC+3 in summer - simplified)
        return 120;
    }

    private function minutesToTimeString(minutes as Float) as String {
        if (minutes < 0) { return "--:--"; }
        var offset = getUtcOffsetMinutes();
        var totalMin = minutes + offset.toFloat();
        var localMinutes = totalMin - 1440.0 * (totalMin / 1440.0).toNumber().toFloat();
        if (localMinutes < 0) { localMinutes += 1440.0; }
        var h = localMinutes.toNumber() / 60;
        var m = localMinutes.toNumber() % 60;
        return h.toString() + ":" + (m < 10 ? "0" : "") + m.toString();
    }

    // Alot Hashachar = sunrise - 72 minutes
    function getAlotHashachar() as String {
        var sunrise = calcSunTime(false);
        if (sunrise < 0) { return "--:--"; }
        return minutesToTimeString(sunrise - 72.0);
    }

    function getSunrise() as String {
        return minutesToTimeString(calcSunTime(false));
    }

    function getSunset() as String {
        return minutesToTimeString(calcSunTime(true));
    }

    // Tzet Hakochavim = sunset + 18 minutes
    function getTzetHakochavim() as String {
        var sunset = calcSunTime(true);
        if (sunset < 0) { return "--:--"; }
        return minutesToTimeString(sunset + 18.0);
    }

    // Candle lighting = sunset - 18 minutes
    function getCandleLighting() as String {
        var sunset = calcSunTime(true);
        if (sunset < 0) { return "--:--"; }
        return minutesToTimeString(sunset - 18.0);
    }

    // Shaah Zmanit (GRA): 1/12 of time between sunrise and sunset
    function getShaahZmanit() as String {
        var sunrise = calcSunTime(false);
        var sunset  = calcSunTime(true);
        if (sunrise < 0 or sunset < 0) { return "--:--"; }
        var shaah = (sunset - sunrise) / 12.0;
        var h = shaah.toNumber() / 60;
        var m = shaah.toNumber() % 60;
        return h.toString() + ":" + (m < 10 ? "0" : "") + m.toString();
    }
}

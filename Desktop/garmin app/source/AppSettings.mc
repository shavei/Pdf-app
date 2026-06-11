import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;

// Connect IQ app-settings access (edited from the Connect IQ phone app).
// NOT (:glance) — only widget views read settings.
class AppSettings {

    // Israel parasha schedule (1, default) vs diaspora (0).
    static function israelSchedule() as Boolean {
        try {
            var v = Application.Properties.getValue("israelSchedule");
            if (v instanceof Lang.Number) { return v != 0; }
        } catch (e) { }
        return true;
    }

    // Primary text color. Instinct (2-color MIP) is always white; everything
    // else (AMOLED + 64-color MIP) honors the setting.
    static function textColor() as Number {
        if (DeviceInfo.isSolar()) { return Graphics.COLOR_WHITE; }
        try {
            var v = Application.Properties.getValue("textColor");
            if (v instanceof Lang.Number) { return v; }
        } catch (e) { }
        return Graphics.COLOR_WHITE;
    }
}

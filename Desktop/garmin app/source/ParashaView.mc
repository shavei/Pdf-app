import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.WatchUi;

// Second widget page: this week's parasha (or the festival name on a
// parasha-less Shabbat). Entered from the date page via select/tap/swipe.
class ParashaView extends WatchUi.View {

    var fSmall  as FontDef;
    var fMedium as FontDef;
    var fLarge  as FontDef;

    function initialize() {
        View.initialize();
        fSmall  = HebrewFontsEx.getSmall();
        fMedium = HebrewFontsEx.getMedium();
        fLarge  = HebrewFontsEx.getLarge();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        var w  = dc.getWidth();
        var h  = dc.getHeight();
        var cx = w / 2;

        var hebrewDate = new HebrewDate(Time.now());
        var israel = AppSettings.israelSchedule();
        var color  = AppSettings.textColor();
        var name   = Parasha.displayName(hebrewDate.jd, israel);
        var header = Parasha.hasParasha(hebrewDate.jd, israel) ? "פרשת השבוע" : "שבת";

        if (DeviceInfo.isSolar()) {
            // Day-of-week letter in the subscreen circle — same as the date page
            var gpsR  = 27;
            var gpsCX = 120;
            var gpsCY = 52;
            if (WatchUi has :getSubscreen) {
                var sub = WatchUi.getSubscreen();
                if (sub != null) {
                    gpsR  = sub.width / 2;
                    gpsCX = sub.x + gpsR;
                    gpsCY = sub.y + gpsR;
                }
            }
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_WHITE);
            dc.fillCircle(gpsCX, gpsCY, gpsR);
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
            dc.drawText(gpsCX, gpsCY, fMedium, hebrewDate.getDayOfWeekLetter(),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 96, fSmall, header,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            _drawNameFit(dc, name, cx, 135, w - 20, color);
        } else {
            var yHdr = (h * 22) / 100;
            dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, yHdr, fMedium, header,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            _drawNameFit(dc, name, cx, (h * 48) / 100, w - 24, color);
        }

        drawDots(dc, 1);
    }

    // Draw the parasha name vcentered at (cx, cy), picking the largest font
    // that fits maxW; doubled portions fall back to two lines split at the hyphen.
    private function _drawNameFit(dc as Graphics.Dc, name as String,
                                  cx as Number, cy as Number,
                                  maxW as Number, color as Number) as Void {
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);

        var fonts = [fLarge, fMedium, fSmall] as Array<FontDef>;
        for (var i = 0; i < fonts.size(); i++) {
            if (dc.getTextWidthInPixels(name, fonts[i]) <= maxW) {
                dc.drawText(cx, cy, fonts[i], name,
                    Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
                return;
            }
        }

        // Too wide even at fSmall — split a doubled name into two lines
        var splitAt = name.find("־");
        if (splitAt == null) { splitAt = name.length() / 2; }
        var line1 = name.substring(0, splitAt + 1) as String; // keep the maqaf
        var line2 = name.substring(splitAt + 1, name.length()) as String;
        var font = fMedium;
        if (dc.getTextWidthInPixels(line1, font) > maxW
            or dc.getTextWidthInPixels(line2, font) > maxW) {
            font = fSmall;
        }
        var lh = dc.getFontHeight(font);
        dc.drawText(cx, cy - lh / 2, font, line1,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx, cy + lh / 2, font, line2,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // Page-indicator dots shared by both widget pages. active: 0 = date, 1 = parasha.
    static function drawDots(dc as Graphics.Dc, active as Number) as Void {
        var cx = DeviceInfo.centerX();
        var y  = DeviceInfo.dotsY();
        var sp = DeviceInfo.dotsSpacing();
        var r  = DeviceInfo.dotsRadius();
        for (var i = 0; i < 2; i++) {
            var x = cx - sp / 2 + i * sp;
            if (i == active) {
                dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
                dc.fillCircle(x, y, r);
            } else if (DeviceInfo.isSolar()) {
                // 2-color display: gray doesn't exist, use a white outline
                dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
                dc.drawCircle(x, y, r);
            } else {
                dc.setColor(DeviceInfo.colorDotInactive(), Graphics.COLOR_TRANSPARENT);
                dc.fillCircle(x, y, r);
            }
        }
    }
}

// Input handling on the parasha page: back/select/swipe-right all return
// to the date page.
class ParashaDelegate extends WatchUi.BehaviorDelegate {

    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onBack() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }

    function onSelect() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }

    function onSwipe(e as WatchUi.SwipeEvent) as Boolean {
        if (e.getDirection() == WatchUi.SWIPE_RIGHT) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
            return true;
        }
        return false;
    }

    function onKey(keyEvent as WatchUi.KeyEvent) as Boolean {
        var key = keyEvent.getKey();
        if (key == WatchUi.KEY_ESC or key == WatchUi.KEY_LAP) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
            return true;
        }
        return false;
    }
}

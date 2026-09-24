import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.WatchUi;

// Third widget page — SOLAR ONLY (Instinct 3 Solar / Instinct 2, 176px 2-color).
// The 176px screen has no room for a contextual line under the parasha, so the
// Omer count (in season) / next holiday / Rosh Chodesh gets its own page here.
// Other devices show this inline on the parasha page instead.
class OmerView extends WatchUi.View {

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
        var cx = w / 2;

        var hebrewDate = new HebrewDate(Time.now());

        // Day-of-week letter in the subscreen circle — same as the other pages.
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

        var lift = DeviceInfo.solarLift();
        var c = HebrewEvents.contextual(hebrewDate, AppSettings.israelSchedule());
        if (c != null and (c[0] as String).equals("omer")) {
            // Title + prominent count: ספירת העומר / ל״ג בעומר
            dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 98 - lift, fSmall, "ספירת העומר",
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            _drawFit(dc, c[1] as String, cx, 134 - lift, w - 16);
        } else if (c != null) {
            // Closest event: "הבא" header (only when it's still upcoming),
            // the name, and the countdown below.
            var days = c[2] as Number;
            if (days > 0) {
                dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
                dc.drawText(cx, 96 - lift, fSmall, "הבא",
                    Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            }
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            _drawFit(dc, c[1] as String, cx, 126 - lift, w - 16);
            dc.drawText(cx, 152 - lift, fSmall, HebrewEvents.countdownText(days),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }

        ParashaView.drawDots(dc, 2, 3);
    }

    // Draw text vcentered at (cx, cy) in the largest of large/medium/small
    // that fits maxW.
    private function _drawFit(dc as Graphics.Dc, text as String,
                              cx as Number, cy as Number, maxW as Number) as Void {
        var fonts = [fLarge, fMedium, fSmall] as Array<FontDef>;
        for (var i = 0; i < fonts.size(); i++) {
            if (dc.getTextWidthInPixels(text, fonts[i]) <= maxW) {
                dc.drawText(cx, cy, fonts[i], text,
                    Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
                return;
            }
        }
        dc.drawText(cx, cy, fSmall, text,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

// Last page — back/select/swipe-right return to the parasha page.
class OmerDelegate extends WatchUi.BehaviorDelegate {

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

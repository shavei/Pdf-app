import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.WatchUi;

class HebrewCalendarView extends WatchUi.View {

    var hebrewDate as HebrewDate;

    var fSmall  as FontDef;
    var fMedium as FontDef;
    var fLarge  as FontDef;

    function initialize() {
        View.initialize();
        hebrewDate = new HebrewDate(Time.now());
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
        var solar = DeviceInfo.isSolar();

        if (solar) {
            // Measure actual font heights so spacing is always correct
            var lh = dc.getFontHeight(fLarge);
            var mh = dc.getFontHeight(fMedium);

            // GPS circle sits at ~y=52, r=27 — start content below it
            var contentTop = 52 + 27 + 10; // ≈89
            var y1 = contentTop + lh / 2;
            var y2 = y1 + lh / 2 + 10 + mh / 2;

            // Day-of-week letter in a circle, mirroring the GPS button (upper-left).
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
            var mirCX = gpsCX;
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_WHITE);
            dc.fillCircle(mirCX, gpsCY, gpsR);
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
            dc.drawText(mirCX, gpsCY, fMedium, hebrewDate.getDayOfWeekLetter(),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y1, fLarge,
                hebrewDate.getDayGematria() + " " + hebrewDate.getMonthName(),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y2, fMedium, hebrewDate.getYearGematria(),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        } else {
            // AMOLED / fr165m: full round screen, everything centered.

            // Day letter — small, near top
            var yDow = (h * 20) / 100;
            dc.setColor(DeviceInfo.colorAccent(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, yDow, fMedium,
                hebrewDate.getDayOfWeekLetter(),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            // Day + month
            var y1 = (h * 46) / 100;
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y1, fLarge,
                hebrewDate.getDayGematria() + " " + hebrewDate.getMonthName(),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            // Year
            var y2 = (h * 66) / 100;
            dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y2, fMedium, hebrewDate.getYearGematria(),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
    }
}

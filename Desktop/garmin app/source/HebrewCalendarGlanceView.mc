import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.WatchUi;

(:glance)
class HebrewCalendarGlanceView extends WatchUi.GlanceView {

    var dayMonth  as String = "";
    var year      as String = "";
    var dowLetter as String = "";
    var fSmall  as FontDef;
    var fMedium as FontDef;

    function initialize() {
        GlanceView.initialize();
        var hDate = new HebrewDate(Time.now());
        dayMonth  = hDate.getDayGematria() + " " + hDate.getMonthName();
        year      = hDate.getYearGematria();
        dowLetter = hDate.getDayOfWeekLetter();
        fSmall  = HebrewFonts.getSmall();
        fMedium = HebrewFonts.getMedium();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        var w   = dc.getWidth();
        var h   = dc.getHeight();
        var cy  = h / 2;
        var pad = 10;

        // Day letter — left side, large, acts as an icon
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(pad, cy, fMedium, dowLetter,
            Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);

        // Date — right-aligned (natural Hebrew RTL)
        // Day + month on top line, year dimmer below
        var mh   = dc.getFontHeight(fMedium);
        var sh   = dc.getFontHeight(fSmall);
        var xR   = w - pad;
        var yTop = cy - sh / 2 - 1;
        var yBot = cy + mh / 2 + 1;

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(xR, yTop, fSmall, dayMonth,
            Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(xR, yBot, fSmall, year,
            Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

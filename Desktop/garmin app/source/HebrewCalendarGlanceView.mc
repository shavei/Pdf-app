import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.WatchUi;

// Right-edge inset for the glance date lines, resolved at compile time via
// monkey.jungle excludeAnnotations (runtime System.getDeviceSettings() in the
// glance gets the app REMOVED as unsupported on tiered-glance 3.4 devices —
// fr55/instinct2). Round screens inset so the carousel's high band doesn't
// clip the date's first chars; Instinct keeps the flush-right look.
(:glance, :roundGlance)
module GlanceShape {
    function insetPct() as Lang.Number { return 20; }
}

(:glance, :flatGlance)
module GlanceShape {
    function insetPct() as Lang.Number { return 0; }
}

(:glance)
class HebrewCalendarGlanceView extends WatchUi.GlanceView {

    var dayMonth  as String = "";
    var year      as String = "";
    var dowLetter as String = "";
    var dayStr    as String = "";
    var monthStr  as String = "";
    var fSmall  as FontDef;
    var fMedium as FontDef;
    var layout  as Array<Number>;

    function initialize() {
        GlanceView.initialize();
        var hDate = new HebrewDate(Time.now());
        dayMonth  = hDate.getDayGematria() + " " + hDate.getMonthName();
        year      = hDate.getYearGematria();
        dowLetter = hDate.getDayOfWeekLetter();
        dayStr    = hDate.getDayGematria();
        monthStr  = hDate.getMonthName();
        fSmall  = HebrewFonts.getSmall();
        fMedium = HebrewFonts.getMedium();
        layout  = Application.loadResource(Rez.JsonData.GlanceLayout) as Array<Number>;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        // Instincts: a solved layout that keeps every line clear of the
        // sub-screen window / carousel mask (see fit_instinct_glance.py).
        var mode = layout[0];
        if (mode != 0) {
            _drawSolved(dc, mode);
            return;
        }

        var w   = dc.getWidth();
        var h   = dc.getHeight();
        var cy  = h / 2;
        var pad = 10;
        var gap = 8;          // min px between the day letter and the date

        var dmW = dc.getTextWidthInPixels(dayMonth, fSmall);
        var ltW = dc.getTextWidthInPixels(dowLetter, fMedium);
        var mh  = dc.getFontHeight(fMedium);
        var sh  = dc.getFontHeight(fSmall);
        var yTop = cy - sh / 2 - 1;
        var yBot = cy + mh / 2 + 1;

        // Normal layout: day letter left (vertically centred), date + year
        // right-aligned. If the date doesn't fit beside the letter, first
        // shrink the side pads (down to 6px); if it still collides — only the
        // longest dates (כ״ט אדר א׳, כ״ט תשרי…) on narrow glance areas — use the
        // COMPACT layout: date gets the full top line, the day letter moves
        // down beside the year. Checked for every device by
        // tools/verify/check_glance_fit.py.
        var slack = w - 2 * pad - ltW - gap - dmW;
        var compact = false;
        if (slack < 0) {
            pad = 10 + (slack - 1) / 2;
            if (pad < 6) { compact = true; pad = 10; }
        }

        if (compact) {
            if (dmW > w - 2 * pad) {
                pad = (w - dmW) / 2;
                if (pad < 4) { pad = 4; }
            }
            var xRc = w - pad;
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(xRc, yTop, fSmall, dayMonth,
                Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
            dc.drawText(pad, yBot, fSmall, dowLetter,
                Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(xRc, yBot, fSmall, year,
                Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }

        // Day letter — left side, large, acts as an icon
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(pad, cy, fMedium, dowLetter,
            Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);

        // Date — right-aligned (natural Hebrew RTL); year dimmer below.
        // Round glances inset the text (GlanceShape), capped so the date never
        // comes closer than `gap` to the day letter.
        var inset    = (w * GlanceShape.insetPct()) / 100;
        var maxInset = (w - pad - dmW) - (pad + ltW + gap);
        if (maxInset < 0) { maxInset = 0; }
        if (inset > maxInset) { inset = maxInset; }
        var xR   = w - pad - inset;

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(xR, yTop, fSmall, dayMonth,
            Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(xR, yBot, fSmall, year,
            Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // Instinct layouts solved per device by tools/fonts/fit_instinct_glance.py
    // against real carousel masks (GlanceLayout jsonData):
    // mode 1 (MIP Instincts, [1, y1, y2, yYear, xYear, x0]) — "stacked":
    //   line 1: day letter + day, line 2: month (both GlanceMedium, left
    //   column clear of the sub-screen window); year small, bottom-right strip.
    // mode 2 (Instinct 3 AMOLED, [2, yTop, yBot, xDate, xYear, x0]):
    //   top row date right-aligned at xDate; bottom row day letter at x0,
    //   year right-aligned at xYear (all GlanceSmall).
    private function _drawSolved(dc as Graphics.Dc, mode as Number) as Void {
        var L = Graphics.TEXT_JUSTIFY_LEFT  | Graphics.TEXT_JUSTIFY_VCENTER;
        var R = Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER;
        var x0 = layout[5];
        if (mode == 1) {
            var ltW = 0;
            var letters = HebrewDate.DOW_LETTERS;
            for (var i = 0; i < letters.size(); i++) {
                var lw = dc.getTextWidthInPixels(letters[i] as String, fMedium);
                if (lw > ltW) { ltW = lw; }
            }
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(x0, layout[1], fMedium, dowLetter, L);
            dc.drawText(x0 + ltW + 8, layout[1], fMedium, dayStr, L);
            dc.drawText(x0, layout[2], fMedium, monthStr, L);
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(layout[4], layout[3], fSmall, year, R);
        } else {
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(layout[3], layout[1], fSmall, dayMonth, R);
            dc.drawText(x0, layout[2], fSmall, dowLetter, L);
            dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(layout[4], layout[2], fSmall, year, R);
        }
    }
}

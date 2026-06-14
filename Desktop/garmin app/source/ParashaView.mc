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
        // One walk per render — instinct2's watchdog is tight (see Parasha.mc)
        var reading = Parasha.forShabbat(hebrewDate.jd, israel);
        var name    = (reading != null)
            ? Parasha.joinNames(reading) : Parasha.festivalName(hebrewDate.jd);
        var header  = (reading != null) ? "פרשת השבוע" : "שבת";

        if (DeviceInfo.isSolar()) {
            // Day-of-week letter in the subscreen circle — same as the date page.
            // The Omer / next-event line gets its OWN page (page 3) on this 176px
            // 2-color screen — there is no room for a 3rd line under the circle.
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

            _drawNameFit(dc, name, cx, 135, w - 20, color, 0);
        } else {
            // 28%/53% (not higher): at 22% the header clipped on the round
            // top edge of small screens — user-reported on fr55
            var yHdr = (h * 28) / 100;
            dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, yHdr, fMedium, header,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            _drawNameFit(dc, name, cx, (h * 53) / 100, w - 24, color, 0);

            // Non-solar has room: append the contextual line under the name.
            // Omer in season, else the soonest holiday / Rosh Chodesh + countdown.
            var omer = hebrewDate.getOmerDay();
            if (omer > 0) {
                _drawExtra(dc, HebrewEvents.omerName(omer), null, null,
                    cx, (h * 74) / 100, w - 24, true);
            } else {
                var ev = HebrewEvents.nextEvent(hebrewDate);
                if (ev != null) {
                    _drawExtra(dc, null, ev[0] as String,
                        HebrewEvents.countdownText(ev[1] as Number),
                        cx, (h * 74) / 100, w - 24, true);
                }
            }
        }

        drawDots(dc, 1, DeviceInfo.isSolar() ? 3 : 2);
    }

    // Draw the parasha name vcentered at (cx, cy), picking the largest font
    // that fits maxW; doubled portions fall back to two lines split at the hyphen.
    private function _drawNameFit(dc as Graphics.Dc, name as String,
                                  cx as Number, cy as Number,
                                  maxW as Number, color as Number,
                                  startIdx as Number) as Void {
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);

        var fonts = [fLarge, fMedium, fSmall] as Array<FontDef>;
        for (var i = startIdx; i < fonts.size(); i++) {
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

    // Draw the contextual line (Omer, or next-event name + countdown) centered
    // at (cx, cy) in the highlight color. `single` is a one-line string (Omer);
    // otherwise `name`/`count` are drawn together, wrapping to two lines when
    // twoLines is set and the combined string is too wide (else count dropped).
    // cy is clamped up so nothing collides with the page-indicator dots.
    private function _drawExtra(dc as Graphics.Dc, single as String?,
                               name as String?, count as String?,
                               cx as Number, cy as Number,
                               maxW as Number, twoLines as Boolean) as Void {
        if (single == null and name == null) { return; }
        dc.setColor(DeviceInfo.colorHighlight(), Graphics.COLOR_TRANSPARENT);
        var lh = dc.getFontHeight(fSmall);
        var bottom = DeviceInfo.dotsY() - 6;

        // Round screens narrow toward the bottom — cap the width to the actual
        // chord at this row so a medium line wraps instead of clipping the bezel.
        var chordW = DeviceInfo.usableWidthAtY(cy);
        if (chordW < maxW) { maxW = chordW; }

        if (single != null) {
            if (cy + lh / 2 > bottom) { cy = bottom - lh / 2; }
            dc.drawText(cx, cy, fSmall, single,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }

        var combined = name + " " + count;
        var fits = dc.getTextWidthInPixels(combined, fSmall) <= maxW;
        if (fits or !twoLines) {
            var s = fits ? combined : name;
            if (cy + lh / 2 > bottom) { cy = bottom - lh / 2; }
            dc.drawText(cx, cy, fSmall, s,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }

        if (cy + lh > bottom) { cy = bottom - lh; }
        dc.drawText(cx, cy - lh / 2, fSmall, name,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx, cy + lh / 2, fSmall, count,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // Page-indicator dots shared by the widget pages. active = current page;
    // count = total pages (2 normally, 3 on Solar with its Omer/events page).
    static function drawDots(dc as Graphics.Dc, active as Number, count as Number) as Void {
        var cx = DeviceInfo.centerX();
        var y  = DeviceInfo.dotsY();
        var sp = DeviceInfo.dotsSpacing();
        var r  = DeviceInfo.dotsRadius();
        for (var i = 0; i < count; i++) {
            var x = cx - (sp * (count - 1)) / 2 + i * sp;
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

// Input handling on the parasha page. Back/swipe-right return to the date page.
// Select/swipe-left: on Solar this advances to the Omer/events page (page 3);
// elsewhere page 2 is the last page, so select just returns to the date page.
class ParashaDelegate extends WatchUi.BehaviorDelegate {

    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onBack() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }

    function onSelect() as Boolean {
        if (DeviceInfo.isSolar()) {
            WatchUi.pushView(new OmerView(), new OmerDelegate(), WatchUi.SLIDE_LEFT);
        } else {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
        }
        return true;
    }

    function onSwipe(e as WatchUi.SwipeEvent) as Boolean {
        if (e.getDirection() == WatchUi.SWIPE_RIGHT) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
            return true;
        }
        if (e.getDirection() == WatchUi.SWIPE_LEFT and DeviceInfo.isSolar()) {
            WatchUi.pushView(new OmerView(), new OmerDelegate(), WatchUi.SLIDE_LEFT);
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

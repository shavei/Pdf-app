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
            var k = Hands.keepOut();
            var bottom = DeviceInfo.dotsY() - DeviceInfo.dotsRadius() - 3;
            if (k > 0) {
                // Instinct Crossover: hands cover the centre band — header
                // above it, name below it; no day-letter circle (no sub-screen).
                var sInk = dc.getFontHeight(fSmall) * 3 / 8;
                dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
                dc.drawText(cx, h / 2 - k - sInk, fSmall, header,
                    Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
                _drawNameFit(dc, name, cx, (h / 2 + k + bottom) / 2, w - 20, color, 0,
                    h / 2 + k, bottom);
            } else {
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
            dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, 96 - lift, fSmall, header,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            // Two-line (doubled) names must sit between the header and the dots.
            _drawNameFit(dc, name, cx, 135 - lift, w - 20, color, 0,
                96 - lift + dc.getFontHeight(fSmall) * 2 / 5, bottom);
            }
        } else {
            // 28%/53% (not higher): at 22% the header clipped on the round
            // top edge of small screens — user-reported on fr55
            var yHdr = (h * 28) / 100;
            // Instinct Crossover AMOLED: header AND name go above the hands
            // band (header raised to 15% to make room), contextual below it.
            var k = Hands.keepOut();
            if (k > 0) { yHdr = (h * 15) / 100; }
            // Small round screens (FR55 208px, FR255S 218px) need the room for a
            // doubled parasha + two-line countdown: header in the small font.
            var hdrFont = (h < 230) ? fSmall : fMedium;
            dc.setColor(DeviceInfo.colorDim(), Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, yHdr, hdrFont, header,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

            // Non-solar has room: append the contextual line under the name —
            // Omer count in season, else the closest Jewish date + countdown.
            // Laid out FIRST so the name (which may wrap to two lines) is
            // fitted above wherever the contextual block actually starts.
            var extraTop = (h * 74) / 100 - dc.getFontHeight(fSmall) * 3 / 8;
            var c = HebrewEvents.contextual(hebrewDate, israel);
            if (c != null) {
                if ((c[0] as String).equals("omer")) {
                    extraTop = _drawExtra(dc, c[1] as String, null, null,
                        cx, (h * 74) / 100, w - 24, true);
                } else {
                    extraTop = _drawExtra(dc, null, c[1] as String,
                        HebrewEvents.countdownText(c[2] as Number),
                        cx, (h * 74) / 100, w - 24, true);
                }
            }

            // ...and the name below it (between the hands and the contextual line)
            var nameTop = yHdr + dc.getFontHeight(hdrFont) * 3 / 8;
            var nameBot = (k > 0) ? h / 2 - k - 3 : extraTop - 4;
            var nameCy  = (k > 0) ? (nameTop + nameBot) / 2 : (h * 53) / 100;
            _drawNameFit(dc, name, cx, nameCy, w - 24, color, 0,
                nameTop, nameBot);
        }

        drawDots(dc, 1, DeviceInfo.isSolar() ? 3 : 2);
    }

    // Draw the parasha name vcentered at (cx, cy), picking the largest font
    // that fits maxW; doubled portions fall back to two lines split at the hyphen.
    private function _drawNameFit(dc as Graphics.Dc, name as String,
                                  cx as Number, cy as Number,
                                  maxW as Number, color as Number,
                                  startIdx as Number,
                                  minTop as Number, maxBottom as Number) as Void {
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);

        var fonts = [fLarge, fMedium, fSmall] as Array<FontDef>;
        for (var i = startIdx; i < fonts.size(); i++) {
            if (dc.getTextWidthInPixels(name, fonts[i]) <= maxW) {
                // Keep the ink between minTop and maxBottom: nudge up if it
                // would touch what's below; if it can't fit, try a smaller font.
                var ink = dc.getFontHeight(fonts[i]) * 3 / 8;
                var y = cy;
                if (y + ink > maxBottom - 4) { y = maxBottom - 4 - ink; }
                if (y - ink < minTop + 4 and i < fonts.size() - 1) { continue; }
                dc.drawText(cx, y, fonts[i], name,
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
        // Fit the two-line block between minTop (header ink bottom) and
        // maxBottom (dots / contextual line): Hebrew glyph ink is ~3/4 of the
        // font height, lines are pitched at 3/4 of it. Drop to fSmall if
        // Medium can't fit, then centre the block in the gap.
        // Lines are pitched at 9/10 of the font height so the ascenders of
        // line 2 (ק, ל) never touch line 1 (was 3/4: cramped on FR255S).
        var lh = dc.getFontHeight(font);
        var gap = maxBottom - minTop - 10;   // >= 5px clear of header and line below
        if (lh * 9 / 10 + lh * 3 / 4 > gap and font == fMedium) {
            font = fSmall;
            lh = dc.getFontHeight(font);
        }
        var pitch = lh * 9 / 10;
        var blockH = pitch + lh * 3 / 4;           // ink top of line1 .. ink bottom of line2
        var y1 = cy - pitch / 2;
        var inkTop = y1 - lh * 3 / 8;
        if (inkTop + blockH > maxBottom - 5) { inkTop = maxBottom - 5 - blockH; }
        if (inkTop < minTop + 5) { inkTop = minTop + 5 + (gap - blockH) / 2; }
        y1 = inkTop + lh * 3 / 8;
        dc.drawText(cx, y1, font, line1,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx, y1 + pitch, font, line2,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // Draw the contextual line (Omer, or next-event name + countdown) centered
    // at (cx, cy) in the highlight color. `single` is a one-line string (Omer);
    // otherwise `name`/`count` are drawn together, wrapping to two lines when
    // twoLines is set and the combined string is too wide (else count dropped).
    // cy is clamped up so nothing collides with the page-indicator dots.
    // Returns the ink top of what it drew, so the name can be fitted above it.
    private function _drawExtra(dc as Graphics.Dc, single as String?,
                               name as String?, count as String?,
                               cx as Number, cy as Number,
                               maxW as Number, twoLines as Boolean) as Number {
        var ink = dc.getFontHeight(fSmall) * 3 / 8;
        if (single == null and name == null) { return cy - ink; }
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
            return cy - ink;
        }

        var combined = name + " " + count;
        var fits = dc.getTextWidthInPixels(combined, fSmall) <= maxW;
        if (fits or !twoLines) {
            var s = fits ? combined : name;
            if (cy + lh / 2 > bottom) { cy = bottom - lh / 2; }
            dc.drawText(cx, cy, fSmall, s,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            return cy - ink;
        }

        if (cy + lh > bottom) { cy = bottom - lh; }
        dc.drawText(cx, cy - lh / 2, fSmall, name,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(cx, cy + lh / 2, fSmall, count,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        return cy - lh / 2 - ink;
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

    // DOWN button (or swipe up): next page — the Omer page on Solar;
    // page 2 is the last page elsewhere, so it does nothing there.
    function onNextPage() as Boolean {
        if (DeviceInfo.isSolar()) {
            WatchUi.pushView(new OmerView(), new OmerDelegate(), WatchUi.SLIDE_UP);
        }
        return true;
    }

    // UP button (or swipe down): previous page = the date page
    function onPreviousPage() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_DOWN);
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

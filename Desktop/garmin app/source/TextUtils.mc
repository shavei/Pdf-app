import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.WatchUi;

// Smart text drawing that wraps around the Instinct 3 Solar subscreen obstruction.
// On devices with a subscreen (GPS button circle), available horizontal width
// varies by Y position — full width below the subscreen, narrower above it.
class TextUtils {

    // Cached subscreen bounds [x, y, size, size] (always square), or null if not available
    static var _subscreenBounds as Array<Number>? = null;
    static var _boundsChecked as Boolean = false;
    static const MARGIN as Number = 8;
    // Extra breathing room between content and the subscreen edge on all sides
    static const SUBSCREEN_BORDER as Number = 4;
    // Pixels to shift the protected zone upward (firmware Y may sit slightly low)
    static const SUBSCREEN_Y_ADJUST as Number = 18;
    // Grow the square by this many pixels on every side so tuning Y_ADJUST
    // doesn't uncover the bottom — one knob instead of two
    static const SUBSCREEN_GROW as Number = 4;

    // Fetch and cache the subscreen obstruction zone once.
    // The protected box is always square (max of width/height) so the circular
    // GPS button is fully cleared regardless of its reported aspect ratio.
    static function getSubscreenBounds() as Array<Number>? {
        if (_boundsChecked) {
            return _subscreenBounds;
        }
        _boundsChecked = true;

        if (WatchUi has :getSubscreen) {
            var sub = WatchUi.getSubscreen();
            if (sub != null) {
                // Normalise to a square. Shift the top up by Y_ADJUST and
                // add Y_ADJUST back into the height so the bottom edge stays
                // at its original firmware position. SUBSCREEN_GROW only
                // widens the left edge slightly.
                var size = sub.width > sub.height ? sub.width : sub.height;
                _subscreenBounds = [
                    (sub.x - SUBSCREEN_GROW) as Number,
                    (sub.y - SUBSCREEN_Y_ADJUST) as Number,
                    (size + SUBSCREEN_GROW) as Number,
                    (size + SUBSCREEN_Y_ADJUST) as Number
                ] as Array<Number>;
                return _subscreenBounds;
            }
        }
        return null;
    }

    // Return the maximum text width available at a given Y coordinate.
    // In the subscreen zone: limited to the left of the obstruction.
    // Below/above it: full screen width minus margins.
    static function maxWidthAtY(y as Number) as Number {
        var screenW = System.getDeviceSettings().screenWidth;
        var full    = screenW - MARGIN * 2;

        var bounds = getSubscreenBounds();
        if (bounds == null) {
            return full;
        }

        var subX = bounds[0] as Number;
        var subY = bounds[1] as Number;
        var subH = bounds[3] as Number; // square: width == height

        // Is this Y inside the obstruction row range (plus border clearance)?
        if (y >= subY - SUBSCREEN_BORDER and y <= subY + subH + SUBSCREEN_BORDER) {
            // Push the right edge left by MARGIN + SUBSCREEN_BORDER
            var limit = subX - MARGIN - SUBSCREEN_BORDER;
            return limit > 0 ? limit : 20;
        }

        return full;
    }

    // Right edge of the circular screen at row Y (chord geometry).
    // On a rectangular screen this is just screenW - MARGIN.
    private static function _bezelRightAtY(y as Number, screenW as Number) as Number {
        var r  = screenW / 2;
        var dy = r - y;
        if (dy < 0) { dy = -dy; }
        if (dy >= r) { return MARGIN; } // degenerate row
        var chord = Math.sqrt((r * r - dy * dy).toFloat()).toNumber();
        var edge  = r + chord - MARGIN;
        return edge > MARGIN ? edge : MARGIN;
    }

    // Return the right-edge X for RTL text at a given Y.
    // Respects both the subscreen obstruction and the circular screen bezel.
    static function safeRightXAtY(y as Number) as Number {
        var screenW    = System.getDeviceSettings().screenWidth;
        var bezelRight = _bezelRightAtY(y, screenW);

        var bounds = getSubscreenBounds();
        if (bounds != null) {
            var subX = bounds[0] as Number;
            var subY = bounds[1] as Number;
            var subH = bounds[3] as Number;
            if (y >= subY - SUBSCREEN_BORDER and y <= subY + subH + SUBSCREEN_BORDER) {
                var subRight = subX - MARGIN - SUBSCREEN_BORDER;
                if (subRight < 20) { subRight = 20; }
                return subRight < bezelRight ? subRight : bezelRight;
            }
        }
        return bezelRight;
    }

    // Draw text with smart word-wrapping that respects the subscreen obstruction.
    // Returns the Y coordinate after the last line drawn.
    // text       — the string to draw (words separated by spaces)
    // font       — any font type accepted by dc.drawText
    // startY     — top Y where text starts
    // lineH      — pixels between lines
    // color      — foreground color
    // centerFull — if true, center text when full width is available
    static function drawWrapped(
        dc       as Graphics.Dc,
        text     as String,
        font     as FontDef,
        startY   as Number,
        lineH    as Number,
        color    as Number,
        centerFull as Boolean
    ) as Number {
        var screenW = System.getDeviceSettings().screenWidth;

        // Split into words (Monkey C has no String.split)
        var words = _splitWords(text);
        var currentLine = "" as String;
        var y = startY;

        for (var i = 0; i < words.size(); i++) {
            var word = words[i] as String;
            var testLine = currentLine.equals("") ? word : currentLine + " " + word;
            var maxW     = maxWidthAtY(y);
            var lineW    = dc.getTextWidthInPixels(testLine, font);

            if (lineW > maxW and !currentLine.equals("")) {
                // Flush current line, start new line
                _drawLine(dc, currentLine, font, y, color, maxW, MARGIN, screenW, centerFull);
                y += lineH;
                currentLine = word;
            } else {
                currentLine = testLine;
            }
        }

        // Draw last line
        if (!currentLine.equals("")) {
            var maxW = maxWidthAtY(y);
            _drawLine(dc, currentLine, font, y, color, maxW, MARGIN, screenW, centerFull);
            y += lineH;
        }

        return y;
    }

    // Manual word splitter (Monkey C has no String.split)
    private static function _splitWords(text as String) as Array<String> {
        var words = [] as Array<String>;
        var len   = text.length();
        var start = 0;
        for (var i = 0; i <= len; i++) {
            var isEnd   = (i == len);
            var isSpace = !isEnd and text.substring(i, i + 1).equals(" ");
            if (isSpace or isEnd) {
                if (i > start) {
                    words.add(text.substring(start, i) as String);
                }
                start = i + 1;
            }
        }
        return words;
    }

    private static function _drawLine(
        dc         as Graphics.Dc,
        line       as String,
        font       as FontDef,
        y          as Number,
        color      as Number,
        maxW       as Number,
        margin     as Number,
        screenW    as Number,
        centerFull as Boolean
    ) as Void {
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        var fullW = screenW - margin * 2;
        if (centerFull and maxW >= fullW) {
            // Full width available — center
            dc.drawText(screenW / 2, y, font, line, Graphics.TEXT_JUSTIFY_CENTER);
        } else {
            // Constrained width — right-align within available area (RTL Hebrew)
            dc.drawText(maxW + margin, y, font, line, Graphics.TEXT_JUSTIFY_RIGHT);
        }
    }
}

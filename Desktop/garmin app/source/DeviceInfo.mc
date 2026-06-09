import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;

// Centralises device-specific layout/color decisions
// (:glance) — used by HebrewFonts which is needed in glance mode
(:glance)
class DeviceInfo {

    static function screenW() as Number {
        return System.getDeviceSettings().screenWidth;
    }

    // True on Instinct 3 Solar (176x176, 2-color)
    static function isSolar() as Boolean {
        return screenW() <= 176;
    }

    // True on Instinct 3 AMOLED 50mm (416x416)
    static function isLarge() as Boolean {
        return screenW() >= 416;
    }

    // Accent color (title, highlights) — white on Solar, blue on color devices
    static function colorAccent() as Number {
        return isSolar() ? Graphics.COLOR_WHITE : Graphics.COLOR_BLUE;
    }

    // Dim/secondary text color
    static function colorDim() as Number {
        return isSolar() ? Graphics.COLOR_WHITE : Graphics.COLOR_LT_GRAY;
    }

    // Highlight color (holidays)
    static function colorHighlight() as Number {
        return isSolar() ? Graphics.COLOR_WHITE : Graphics.COLOR_YELLOW;
    }

    // Inactive page dot color
    static function colorDotInactive() as Number {
        return isSolar() ? Graphics.COLOR_BLACK : Graphics.COLOR_DK_GRAY;
    }

    // Distance from page top (y=0) to the title text
    static function titleY() as Number {
        return isSolar() ? 8 : 12;
    }

    // Height of the title area (title text + separator line)
    static function titleAreaHeight() as Number {
        return isSolar() ? 24 : 38;
    }

    // Offset from page top to where body content starts (below title + separator)
    static function contentOffset() as Number {
        return titleAreaHeight() + 4;
    }

    // Line height for the zmanim list
    static function zmanimLineH() as Number {
        return isSolar() ? 26 : 28;
    }

    // Safe left X at a given Y, accounting for the circular Solar bezel.
    // On a 176px round screen the chord width shrinks near the top/bottom,
    // so the left anchor must move right as Y approaches 0 or screenH.
    static function safeLeftX(y as Number) as Number {
        var margin = 10;
        if (!isSolar()) { return margin; }
        var r  = screenW() / 2;   // 88 on Solar
        var dy = r - y;
        if (dy < 0) { dy = -dy; }
        if (dy >= r) { return margin; }
        // Chord half-width at this Y row
        var chord = Math.sqrt((r * r - dy * dy).toFloat()).toNumber();
        var leftEdge = r - chord + margin;
        return leftEdge > margin ? leftEdge : margin;
    }

    // Safe right X at a given Y — avoids the subscreen obstruction when inside it.
    // Delegates to TextUtils which caches the subscreen bounds at runtime.
    static function safeRightXAtY(y as Number) as Number {
        return TextUtils.safeRightXAtY(y);
    }

    // Full-width center — same for all devices below the GPS circle
    static function centerX() as Number {
        return System.getDeviceSettings().screenWidth / 2;
    }

    // Scroll step in pixels per button press
    static function scrollStep() as Number {
        return isSolar() ? 18 : 30;
    }

    // Page dots Y position
    static function dotsY() as Number {
        var h = System.getDeviceSettings().screenHeight;
        return h - 8;
    }

    // Pixel gap between the scrollable clip region and the dots row
    static function dotsGap() as Number {
        return isSolar() ? 4 : 6;
    }

    // Horizontal spacing between page dots
    static function dotsSpacing() as Number {
        return isSolar() ? 9 : 12;
    }

    // Radius of each page dot
    static function dotsRadius() as Number {
        return isSolar() ? 2 : 3;
    }
}

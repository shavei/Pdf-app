import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

(:glance)
typedef FontDef as Graphics.VectorFont or Graphics.FontReference or Number;

// Glance-safe font loading: vector font OR system font. Never touches Rez.
(:glance)
class HebrewFonts {

    static var HEBREW_FACES = ["NotoSansHebrewBold", "NotoSansHebrewRegular", "FrankRuehl"] as Array<String>;

    static function _sizes() as Array<Number> {
        var w = System.getDeviceSettings().screenWidth;
        if (w >= 390) {
            return [30, 45, 61] as Array<Number>;
        }
        return [16, 20, 24] as Array<Number>;
    }

    // Glance: tries vector font (NotoSansHebrew on real hardware), falls back to a
    // glance-scoped bitmap font (Rez.Fonts.HebrewGlance*) so Hebrew always renders.
    static function getTitle() as FontDef  { return _glanceGet(0, Rez.Fonts.HebrewGlanceSmall);  }
    static function getSmall() as FontDef  { return _glanceGet(0, Rez.Fonts.HebrewGlanceSmall);  }
    static function getMedium() as FontDef { return _glanceGet(1, Rez.Fonts.HebrewGlanceMedium); }
    static function getLarge() as FontDef  { return _glanceGet(2, Rez.Fonts.HebrewGlanceMedium); }

    (:glance)
    private static function _glanceGet(idx as Number, rezFont as ResourceId) as FontDef {
        if (Graphics has :getVectorFont) {
            var px = _sizes()[idx];
            var vf = Graphics.getVectorFont({:face => HEBREW_FACES, :size => px});
            if (vf != null) {
                return vf;
            }
        }
        // Fall back to glance-scoped bitmap font (getVectorFont may return Latin-only)
        return WatchUi.loadResource(rezFont) as Graphics.FontReference;
    }
}

// Extended font loading for the main widget (non-glance): adds bitmap Rez fallback.
// NOT annotated with (:glance) so it's excluded from the glance binary.
class HebrewFontsEx {

    static function getTitle() as FontDef  { return _rezGet(0, Rez.Fonts.HebrewSmall,  Graphics.FONT_SMALL);  }
    static function getSmall() as FontDef  { return _rezGet(0, Rez.Fonts.HebrewSmall,  Graphics.FONT_SMALL);  }
    static function getMedium() as FontDef { return _rezGet(1, Rez.Fonts.HebrewMedium, Graphics.FONT_MEDIUM); }
    static function getLarge() as FontDef  { return _rezGet(2, Rez.Fonts.HebrewLarge,  Graphics.FONT_LARGE);  }

    private static function _rezGet(idx as Number, rezFont as ResourceId, sysFallback as Number) as FontDef {
        // Always use bundled bitmap fonts — getVectorFont() may return a Latin-only
        // system font that renders Hebrew as '?' diamonds.
        return WatchUi.loadResource(rezFont) as Graphics.FontReference;
    }
}

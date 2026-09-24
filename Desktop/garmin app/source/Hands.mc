import Toybox.Application;
import Toybox.Lang;

// Hybrid watches with physical hands (Instinct Crossover / Crossover AMOLED)
// park the hands at 9:15 while an app is open, covering a horizontal band
// through the screen centre. keepOut() is that band's half-height in px
// (0 on every other watch); pages lay text out above/below it.
module Hands {
    var _k as Number? = null;

    function keepOut() as Number {
        if (_k == null) {
            _k = Application.loadResource(Rez.JsonData.HandsKeepOut) as Number;
        }
        return _k as Number;
    }
}

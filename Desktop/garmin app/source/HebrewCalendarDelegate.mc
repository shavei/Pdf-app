import Toybox.Lang;
import Toybox.WatchUi;

class HebrewCalendarDelegate extends WatchUi.BehaviorDelegate {

    function initialize(v as HebrewCalendarView) {
        BehaviorDelegate.initialize();
    }

    function onKey(keyEvent as WatchUi.KeyEvent) as Boolean {
        var key = keyEvent.getKey();
        if (key == WatchUi.KEY_ESC or key == WatchUi.KEY_LAP) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
            return true;
        }
        return false;
    }

    function onBack() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }

    // Select button / screen tap -> parasha page
    function onSelect() as Boolean {
        WatchUi.pushView(new ParashaView(), new ParashaDelegate(), WatchUi.SLIDE_LEFT);
        return true;
    }

    // Swipe left (RTL "next") -> parasha page
    function onSwipe(e as WatchUi.SwipeEvent) as Boolean {
        if (e.getDirection() == WatchUi.SWIPE_LEFT) {
            WatchUi.pushView(new ParashaView(), new ParashaDelegate(), WatchUi.SLIDE_LEFT);
            return true;
        }
        return false;
    }
}

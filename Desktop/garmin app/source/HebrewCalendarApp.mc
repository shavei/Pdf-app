import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

class HebrewCalendarApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Lang.Dictionary?) as Void {
    }

    function onStop(state as Lang.Dictionary?) as Void {
    }

    // Settings edited in the Connect IQ phone app while the widget is open
    function onSettingsChanged() as Void {
        WatchUi.requestUpdate();
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        var view = new HebrewCalendarView();
        var delegate = new HebrewCalendarDelegate(view);
        return [view, delegate];
    }

    // Annotate with (:glance) so only this function (and glance-annotated classes)
    // load in the low-memory glance process.
    (:glance)
    function getGlanceView() as [WatchUi.GlanceView] or [WatchUi.GlanceView, WatchUi.GlanceViewDelegate] or Null {
        return [new HebrewCalendarGlanceView()];
    }
}

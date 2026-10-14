import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

class FmdRemoteApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
        Comm.initialize();
    }

    function getInitialView() {
        return [ new FmdRemoteMenu() ];
    }
}

class FmdRemoteMenu extends WatchUi.Menu2 {

    function initialize() {
        var items = new Array();
        for (var i = 0; i < Comm.commands.size(); i++) {
            var pair = Comm.commands.get(i);
            items.add({
                :title => pair.get(0),
                :id => pair.get(1)
            });
        }
        Menu2.initialize(new WatchUi.Menu({
            :title => "FMD Remote",
            :items => items
        }));
    }

    function onMenuItem(item) {
        Comm.send(item);
    }
}

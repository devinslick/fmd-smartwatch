import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

/** FMD Remote comms: send command suffixes to the phone bridge via the
 *  Connect IQ Communications path (Garmin Connect relays to the ConnectIQ
 *  Android SDK listener inside FMD Watch Bridge). */
module Comm {

    // FMD command suffixes; the bridge composes trigger word + pin.
    var commands = [
        ["Ring", "ring"],
        ["Ring long", "ring long"],
        ["Locate", "locate"],
        ["Locate last", "locate last"],
        ["Locate GPS", "locate gps"],
        ["Stats", "stats"],
        ["Lock", "lock"],
        ["Flash", "flash"],
        ["Ringer silent", "ringermode silent"],
        ["Ringer normal", "ringermode normal"],
        ["DND on", "nodisturb on"],
        ["DND off", "nodisturb off"],
        ["BT scan", "bluetoothscan"],
        ["Camera front", "camera front"],
        ["Camera back", "camera back"]
    ];

    function initialize() {
        Communications.registerForPhoneAppMessages(method(:onPhoneAppMessage));
    }

    private function onPhoneAppMessage(msg) {
        var data = msg.getData();
        if (data != null && data.has("result")) {
            WatchUi.showToast(data.get("result"));
        }
    }

    function send(command) {
        var payload = new Dictionary();
        payload.put("cmd", command);
        Communications.transmit(payload, {
            :method => Communications.CONNECTIVITY_OTA
        }, new Communications.ConnectionListener() {
            function onOpen() {
            }
            function onMessage(msg) {
            }
            function onClose() {
            }
            function onError(err, msg) {
                if (err == Communications.BLE_QUEUE_FULL) {
                    WatchUi.showToast("BLE busy — retry");
                } else if (err == Communications.BLE_CONNECTION_UNAVAILABLE) {
                    WatchUi.showToast("No phone link");
                } else {
                    WatchUi.showToast("Error " + err);
                }
                System.println("FMD transmit error: " + err);
            }
        });
    }
}

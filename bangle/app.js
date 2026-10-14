// FMD Remote — Bangle.js
// Sends Find My Device (FMD) command strings to the FMD Watch Bridge
// Android app via Gadgetbridge's intent forwarding (the watch's single
// BLE connection is owned by Gadgetbridge — this rides it instead of
// fighting it).
//
// Requirements (documented in README.md):
//   - Gadgetbridge paired to the watch
//   - Gadgetbridge Settings → "Allow intents" experimental setting ON
//   - FMD Watch Bridge v0.9.0+ installed on the phone
//
// Result feedback: Gadgetbridge intents are one-way; the phone's FMD
// result notification is the feedback surface for now.

var ACTION = "com.devinslick.fmdring.action.BANGLE";

var commands = [
  { t: "Ring", c: "ring" },
  { t: "Ring long", c: "ring long" },
  { t: "Locate", c: "locate" },
  { t: "Locate last", c: "locate last" },
  { t: "Locate GPS", c: "locate gps" },
  { t: "Stats", c: "stats" },
  { t: "Lock", c: "lock" },
  { t: "Flash", c: "flash" },
  { t: "Ringer silent", c: "ringermode silent" },
  { t: "Ringer normal", c: "ringermode normal" },
  { t: "DND on", c: "nodisturb on" },
  { t: "DND off", c: "nodisturb off" },
  { t: "BT scan", c: "bluetoothscan" },
  { t: "Camera front", c: "camera front" },
  { t: "Camera back", c: "camera back" }
];

function send(cmd, label) {
  if (typeof Bluetooth === "undefined") {
    E.showAlert("No Bluetooth\n(emulator?)", "FMD Remote");
    return;
  }
  Bluetooth.println(JSON.stringify({
    t: "intent",
    target: "broadcastreceiver",
    action: ACTION,
    cmd: cmd
  }));
  E.showAlert(/*LANG*/"Sent:\n" + label, "FMD Remote");
  setTimeout(showMenu, 1500);
}

function showMenu() {
  var menu = { "": { title: "FMD Remote" } };
  commands.forEach(function (entry) {
    menu[entry.t] = function () { send(entry.c, entry.t); };
  });
  E.showMenu(menu);
}

showMenu();

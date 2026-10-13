# fmd-smartwatch

Trigger [Find My Device (FMD)](https://gitlab.com/fmd-foss/fmd-android) commands
from a smartwatch — fully offline, over the watch's existing Bluetooth
connection to the phone. One repo per platform under one roof:

- **`android/`** — the bridge APK ("FMD Ring"). A ~25 KB gradle-free Android
  app. Trigger-based only: exported manifest receivers/services that Android
  spawns on demand. No persistent process, no server, no sockets.
- **`pebble/`** — "FMD Remote", the Pebble watchapp (C, SDK 3, all classic
  platforms). Action list: Ring / Locate (plain, last known, GPS) / Stats /
  Lock / Flash / Ringer mode / DND / BT scan / Camera (front + back) — short
  titles with small-text subtitles so nothing truncates on 144px screens.
- **`wear/`** — "FMD Remote" for Wear OS (Galaxy Watch 5+, planned — see
  `wear/README.md`).

The watch apps are the remotes; `android/` is the bridge that turns their
messages into FMD commands.

## Why a bridge APK? (it is not the goal)

The goal is watch control **native to FMD**: FMD itself receiving watch
commands, with no third-party phone app in the loop. The bridge APK is
scaffolding toward that:

- **For Pebble, it is a stopgap.** Stock FMD releases cannot receive anything
  from a watch. The bridge fills that gap by turning watch messages into the
  notification FMD already trusts (`fmd <pin> <command>`). Once the FMD MR
  below merges and ships in a release, Pebble remotes talk to FMD directly
  (the pbw's companion package points at FMD) and the bridge is no longer
  needed for Pebble.
- **For Wear OS, it is permanent.** FMD's maintainers will not take Google
  Play Services dependencies — so the Wear Data Layer listener can never live
  in FMD. The bridge APK is the forever-home of the Wear phone side.

## Platform status

| Platform | Remote | State | Upstream dependency |
|---|---|---|---|
| Pebble (classic) | `pebble/` v1.5.0 | ✅ tested & working | [fmd-android MR !542](https://gitlab.com/fmd-foss/fmd-android/-/merge_requests/542) (optional PebbleKit 2 companion service) — **pending review**; until it ships, use the bridge APK |
| Wear OS (GW5+) | `wear/` | 🚧 planned | none possible — bridge APK is permanent for Wear (FMD will not take play-services) |

MR !542 status: draft opened, builds and lint green locally; validated
end-to-end on-device (watch → Pebble app → FMD dev build, no bridge).

## How it works (Pebble today)

```
watchapp SELECT
  └─ AppMessage (Cmd string, key 10000)
       └─ Pebble app binds the PK2 companion service (on demand)
            └─ FmdPebbleService (io.rebble.pebblekit2.RECEIVE_DATA_FROM_WATCH)
                 └─ posts notification: "fmd <pin> <command>"
                      └─ FMD's NotificationListenService executes it
                           └─ replies through the notification's RemoteInput
                                └─ ReplyReceiver
                                     ├─ phone notification (geo: map pin for locate)
                                     └─ WatchPusher → SEND_DATA_TO_WATCH → watch display
```

FMD holds every privileged permission (max alarm volume, DND override, device
admin); the bridge is a dumb, permissionless trigger. The FMD notification
password lives in the app's settings (SharedPreferences), never in the binary.

An upstream FMD MR (optional, cleaner path) adds a PebbleKit 2 companion
service to FMD itself, removing the bridge from the loop:
<https://gitlab.com/fmd-foss/fmd-android/-/merge_requests/542>

## FMD prerequisites (one-time)

1. FMD → Settings → enable **notification access**.
2. FMD → Access Control → create a **notification password** with the commands
   you want (Ring/Locate/Flash are in FMD's defaults; Lock needs its box
   ticked plus FMD's device admin).
3. Install the APK, open it once, set trigger word + password, Save.

## Install order (always)

1. If your FMD build does not yet include the PebbleKit 2 companion service
   (MR !542 pending — i.e., any stock release): sideload `FMDRing.apk` (the
   bridge). In-place updates keep settings.
2. Sideload the `.pbw`. The Pebble app reads the companion package
   (`companionApp` in the pbw) **at install time** — if the watchapp fires but
   the phone stays silent, fully delete the watchapp in the Pebble app and
   re-sideload.

## Usage

- **Watch**: UP/DOWN picks an action, SELECT fires. Keep the watchapp open to
  receive results (coordinates, confirmations). Bind it to a quick-launch slot
  for two-press access.
- **Anything else on the phone** (Tasker, Termux, adb, another app):

```
adb shell am broadcast -a com.devinslick.fmdring.action.RING \
    -n com.devinslick.fmdring/.RingReceiver
# optional suffix, composed with trigger word + pin from settings:
#   --es command "locate"
```

- The persistent "Find phone" notification also works: select it on the watch
  or tap it on the phone.

## CI

`.github/workflows/build.yml` builds the artifacts on every push to `main`
and uploads them as run artifacts (`FMDRing-apk`, `fmd-remote-pbw`).

Local builds: `android/build.sh` (needs JDK 17 + Android SDK build-tools
34/platforms 34, paths overridable via `ANDROID_SDK`/`JAVA_HOME`) and
`pebble build` in `pebble/` (needs
[`pebble-tool`](https://developer.repebble.com/sdk/)).

## Security notes

- The `RING` receiver is exported and unauthenticated: any app on the phone
  can fire an FMD command (annoyance-only; the FMD pin never leaves the app).
- `android/debug.keystore` is committed so CI builds update in place over the
  installed app. **If this repo ever goes public, regenerate the keystore and
  strip the old one** — anyone holding it could sign matching updates.
- The PK2 companion wire contract (`io.rebble.pebblekit2.common` AIDL) is
  replicated from [pebble-dev/PebbleKitAndroid2](https://github.com/pebble-dev/PebbleKitAndroid2)
  (Apache-2.0) — a protocol reimplementation, no code copied.
- PK2 diagnostic notifications (settings checkbox) surface every hop as a
  notification; `adb logcat -s FMDRing` shows the same.

# wear/ — FMD Remote for Wear OS (planned)

Galaxy Watch 5+ / any Wear OS 3+ watch.

Architecture (planned, mirrors the Pebble pattern):

- Watch app (Kotlin): same action list as the Pebble app; sends commands
  via the Data Layer API (`MessageClient`) — local IPC through the Wear
  stack the watch already requires; nothing transits any cloud.
- Phone bridge: `android/` gains a `WearableListenerService` that turns
  watch messages into the same FMD trigger notification.
- FMD upstream stays dependency-free; Wear/Google code lives only here.

Gradle module (play-services-wearable AARs make the gradle-free
toolchain impractical here). Not yet scaffolded.

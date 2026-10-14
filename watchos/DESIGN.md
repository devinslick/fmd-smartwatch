# watchOS — FMD Remote (design decided, not yet started)

**Decision (2026-10-13): Option A — a standalone watch-only app talking
directly to an FMD Server over the watch's own network.** No bridge APK, no
Android-side code, no FMD upstream changes. This is a different transport
class from Pebble/Wear: remote (server-mediated) instead of local (phone BT).

## Persona

Apple Watch owners carry an iPhone — the Android device being located is a
*secondary* one (family member's phone, kid's device, work phone). FMD
Server's web UI already serves this from any browser; the watch app brings
it to the wrist. The rare cellular-standalone watch works too (same
codebase — the app never assumes an iPhone exists).

## Why this option

| Option | Verdict | Reason |
|---|---|---|
| **A. Watch → FMD Server API** | ✅ chosen | zero bridge; same E2E trust model as the web UI; works anywhere the watch has LTE/Wi-Fi |
| B. Watch → LAN listener on phone | ❌ rejected | needs a resident HTTP service on the phone (contradicts the no-servers bridge philosophy), LAN attack surface, home-only |
| C. iPhone host app + WatchConnectivity relay | ❌ rejected | requires building a full **fmd-ios** client first (5–10× scope, duplicates the working web UI) and buys nothing — the watch reaches the server directly |

An iPhone is still the watch's *caretaker* (activation, updates, Xcode
deployment) — but never a relay.

## Architecture

- **Target**: watch-only SwiftUI app (no iOS companion target). One-time
  on-watch config: server URL, username, password (Keychain).
- **Protocol**: FMD Server **API v2** + Server Protocol v2 (end-to-end
  encrypted; the operator can neither read data nor forge commands).
  - Auth: `GET /account/{name}/salt` → Argon2id → `POST /account/login`
  - Commands: `POST /data/command` (encrypted blob); phone pulls via
    UnifiedPush
  - Results: poll `GET /data/location`, `GET /data/picture` after sending,
    then decrypt client-side
  - Spec: https://server-edge.fmd-foss.org/openapi.yaml ·
    https://gitlab.com/fmd-foss/server-protocol
- **Crypto stack**: CryptoKit (HKDF, AES-GCM) · Security framework
  (`SecKey` RSA-OAEP) · Argon2id via a Swift package wrapping the C
  reference — the single new dependency (not in CryptoKit).
- **Reference implementation**: [devinslick/fmd_api](https://github.com/devinslick/fmd_api)
  (Python; v1 + v2 flows, validated command helpers) — port, don't re-derive.
- **Command set v1**: play sound, locate (all/gps/cell/last), camera
  front/back, lock, ringer/DND/BT toggles. Wipe excluded from the watch UI
  (misfire risk on a 45 mm screen) unless behind a long-press confirm.
- **UX**: List UI mirroring the Pebble/Wear remotes; status/result line;
  MapKit mini-map for locate results is a stretch goal.

## Build & tooling

- Xcode required → build in the macOS VM, artifacts committed. No CI
  initially (GitHub macOS runners cost 10× minutes on private repos).
- Signing: free Apple ID = 7-day expiry (weekly redeploys); $99/yr developer
  account = 1-year signing / TestFlight. Decide when hardware is in hand.
- Layout (when started): `watchos/` — Swift package for the protocol core
  (reusable, mirrors fmd_api) + thin SwiftUI app target.

## Open items

1. Hardware: access to an iPhone + Apple Watch (dev + test)
2. Spike: `xcodebuild` in the macOS VM; wireless deploy to a paired watch
3. Argon2 Swift package selection
4. Multi-device account UI (pick target device after login)

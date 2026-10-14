# Contributing

The fastest way to help: **test a platform you own hardware for.** Every
remote in this repo needs eyes and wrists — most of them were written
without access to the watch they target.

## What each platform needs right now

| Platform | You need | The task |
|---|---|---|
| Pebble (classic) | Pebble watch + Android phone | ✅ working — regression-test against new FMD releases; help review [fmd-android MR !542](https://gitlab.com/fmd-foss/fmd-android/-/merge_requests/542) |
| Wear OS | Wear OS 3+ watch (GW5 etc.) | Test the watch app + bridge pair (`wear/README.md`) — drafted, never run on a watch |
| Garmin Connect IQ | Any paired Garmin | Build the `.prg` (one-time free Garmin account, see `garmin/README.md`) and test Ring/Locate |
| Bangle.js | Bangle.js 1/2 + Gadgetbridge | Enable Gadgetbridge's "Allow intents", load the app, test the command path (`bangle/README.md`) |
| Amazfit (Zepp OS) | Amazfit watch + Zepp dev account | Port the scaffold via `zeus create`, design the relay's last hop (`zepp/README.md`) |
| watchOS | Mac (Xcode), Apple Watch | SwiftUI app on top of the (already tested) Swift protocol core (`watchos/`) |

## Testing the bridge (all Android platforms)

1. Grab the `FMDRing-apk` artifact from the latest green
   [CI run](https://github.com/devinslick/fmd-smartwatch/actions)
2. Install it on the phone, open it once, set your FMD trigger word +
   notification password
3. Follow your platform's README for the watch side
4. **Open an issue** with: watch model, phone Android version, FMD version,
   what worked, what didn't (screenshots of the bridge's diagnostic
   notifications help — enable them in its settings)

## Code contributions

- `android/` is the single bridge APK (gradle): every platform's phone-side
  listener lives here. New transport → new listener + README.
- Watch remotes live in per-platform directories with their own toolchains
  (see each module's README for build instructions).
- Keep the command vocabulary in sync across remotes (`ring`, `locate gps`,
  `camera front`, …) — it's the contract with FMD.
- PRs: CI must be green; if you ship a new module or change behavior,
  update the root README's platform table and the module README together.
- Contributor builds sign with AGP's default debug keystore — expect a
  signature mismatch with official CI builds (uninstall first).

## Security

- Never commit credentials, tokens, or the FMD notification password —
  the bridge reads them from its settings at runtime, nowhere else.
- Report security issues privately via GitHub security advisories rather
  than public issues.

## License

MIT (see [LICENSE](LICENSE)). Vendored third-party code keeps its own
license file in place (e.g. the Argon2 reference implementation under
`watchos/fmd-server-core/`).

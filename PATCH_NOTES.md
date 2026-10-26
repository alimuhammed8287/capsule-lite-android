# Capsule Lite v38 — remaining-phase implementation

## Activity engine and lifecycle
- Added `LiveActivityPolicy` as a shared classifier for ongoing chronometer and progress notifications.
- Applied deterministic priority ordering: timers/stopwatches before progress activities, then most recently posted notification.
- Prevented lower-priority progress notifications from replacing the currently displayed timer.
- Kept ordinary notifications queued behind an active live timer/download and restored the queued notification when no other eligible live activity remains.
- Added completion/expiry handoff so a completed activity does not leave the pill idle when another eligible activity is available.

## Controls and accessibility
- Added an optional live-activity app allow-list in Notifications settings. Empty means all apps except those hidden in Privacy settings.
- Added a dedicated Reduce motion setting. It disables staggered entrance/morph motion; Android's system animator setting is still respected.

## Build pipeline
- Updated app version to 1.3.0 / versionCode 38.
- Added `scripts/validate_source.py` to CI to parse XML and assert critical source/build features before Gradle compilation.
- CI artifact is `capsule-lite-v38-debug-apk`.

## Validation honesty
- Source/XML validation runs locally.
- Actual Gradle/Kotlin compilation, APK installation, emulator tests, OEM/HyperOS checks, and measured battery testing were not possible in this environment and are not claimed as complete.

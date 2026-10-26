# Capsule Lite v36 — upgrade and validation report

## Scope implemented

This is a source-level upgrade on top of the supplied v35 archive. Existing source files and features were preserved; changes are additive except for the service ticker's scheduling policy and the label for the existing progress-notification setting.

### Visual redesign
- Refined the iOS-inspired graphite and Futuristic Cyan surfaces with subtle vertical gradients while retaining their existing outlines and compositor-blur fallback. This is a static drawable treatment and does not add continuous animation work.

### Runtime efficiency
- Service ticker uses activity-sensitive intervals (500 ms active UI/call/navigation/stopwatch, 750 ms active music, 1,800 ms idle).
- Fullscreen checks and call reconciliation use elapsed-time guards rather than tick-count divisors, preserving approximately two-second cadence when idle polling slows.
- Heartbeat uses an elapsed-time guard to retain a five-minute cadence.
- Microphone visualizer checks `PowerManager.isInteractive` before requesting capture, avoiding screen-off capture.

### Advanced live activities
- Progress notifications can be surfaced whether they use `CATEGORY_PROGRESS` or simply expose Android's standard progress extras while ongoing.
- Progress percent calculation uses `Long` intermediate arithmetic and clamps the displayed percentage.
- Progress activity retains its content intent, enabling tap-through to the source app.
- Ongoing timer/stopwatch notifications using `EXTRA_SHOW_CHRONOMETER`, `when`, and `EXTRA_CHRONOMETER_COUNT_DOWN` can display locally updated elapsed/remaining time.
- A new `liveActivities` setting controls timer/stopwatch live activity handling. Existing `showDownloads` remains the switch for progress activities.

### Release / CI
- Android version metadata: `versionName=1.1.0`, `versionCode=36`.
- GitHub Actions artifact renamed to `capsule-lite-v36-debug-apk`.

## Validation performed

- Archive extracted and re-packed from the supplied source.
- 38 Android manifest/resource XML files parsed with Python's XML parser.
- 9 Kotlin source files passed a lightweight delimiter scan after ignoring comments/string literals; expected feature declarations/settings were checked.
- Gradle configuration and workflow inspected for consistency.
- ZIP integrity test performed after packaging.

## Not performed / not claimed

- Kotlin/Android compilation: no `kotlinc`, Gradle executable/wrapper, Android SDK, or `ANDROID_HOME` is available in the current environment.
- Unit/instrumentation tests: no test source set exists in the supplied archive.
- Runtime tests on Android 14 or HyperOS: require a physical/emulated Android device.
- Battery, CPU, memory, animation, overlay positioning, OEM process management, notification click, and timer display behavior have not been measured on device.

## Manual verification checklist

1. Install the debug APK after building and grant overlay + notification access.
2. Confirm collapsed pill alignment in portrait, landscape, lock/unlock, and after rotation.
3. Test rapid notifications, replies, call state, Maps navigation, charging, music metadata/artwork, and control-panel interactions.
4. Start a download/progress notification; confirm percentage updates, tap-through works, and removal collapses the activity.
5. Use a timer app that exposes a chronometer notification; confirm elapsed/countdown direction and tap-through.
6. Start music visualizer, turn screen off, and confirm microphone capture stops; unlock and confirm expected resumption.
7. Compare idle CPU/battery before and after for at least several hours; verify calls/navigation remain responsive.
8. Test on the target HyperOS build with battery restrictions both enabled and disabled; document any service restart/overlay issues.


## v37 follow-up implementation (2026-10-26)

- Promotes another still-active timer/download when the activity currently shown by the pill is removed, instead of always leaving the pill idle.
- Countdown chronometers now show a one-shot `Timer complete` state and collapse after a short grace period.
- Completed determinate progress activities show `Complete` and collapse after a short grace period.
- Bumped app metadata to versionCode 37 / versionName 1.2.0 and updated the CI APK artifact name.
- Existing package-name exclusion setting is reused for activity restoration, so hidden apps are not reintroduced during handoff.

Build caveat: this container does not have Gradle or an Android SDK (`android.jar`) installed. Static/source checks can run here, but an APK build must run in Android Studio or the included GitHub Actions workflow. No APK build is claimed.

## v38 follow-up implementation (2026-10-26)

- Added `LiveActivityPolicy` to classify ongoing chronometer and progress notifications consistently and order timers above progress activities.
- Added priority-aware promotion so a lower-priority progress update does not replace a visible timer.
- Kept ordinary notifications queued behind an active live timer/download and added a fallback to show the waiting notification when no other eligible live activity remains.
- Completion/expiry now triggers a handoff attempt to another eligible activity.
- Added an optional package-name allow-list for timer/progress live activities; hidden-app exclusions still apply.
- Added a Reduce motion setting that disables staggered/morph animations while respecting Android's system animator setting.
- Bumped metadata to versionCode 38 / versionName 1.3.0 and added a source/XML validation script to the CI workflow.

## v38 validation status

- Python source/XML validation passed: 38 XML files and 13 feature/build assertions.
- Lightweight Kotlin delimiter scan passed for all 10 Kotlin files.
- ZIP integrity passed after packaging.
- Actual Kotlin/Gradle compilation, APK assembly, emulator/device tests, OEM/HyperOS validation, and battery measurements remain unperformed because Gradle and the Android SDK are unavailable in this environment.

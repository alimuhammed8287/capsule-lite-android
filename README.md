# Capsule Lite — v38 advanced source upgrade

Capsule Lite is an Android Dynamic Island-style overlay. This source upgrade retains the existing v35/v36/v37 implementation and adds safer activity arbitration, app-level live-activity filtering, completion handoff, and reduced-motion support.

## v38 changes

- **Shared live-activity policy:** one classifier recognizes Android chronometer/timer and progress notifications, with timer priority above progress and newest activity as the tie-breaker.
- **Priority-aware presentation:** a lower-priority progress update cannot replace a visible timer. Calls remain highest priority. Ordinary notifications are queued behind a live timer/download rather than interrupting it.
- **Completion handoff:** when a timer expires or progress reaches 100%, the pill displays the completion state briefly, collapses, and attempts to promote another eligible live activity. A waiting ordinary notification is presented if no live activity remains.
- **Per-app live-activity allow-list:** optional comma-separated package names restrict which apps can create timer/progress activities. Blank means all apps except apps already hidden in Privacy settings.
- **Reduced motion:** added a dedicated setting to disable staggered entrance and morph animations while retaining the existing global animation toggle and Android animator-scale handling.
- **Release/build metadata:** version 1.3.0 / versionCode 38; CI runs source/XML validation and then compiles `assembleDebug`, uploading the v38 APK artifact.

## Build

### Android Studio
1. Extract the ZIP and open the project root (the directory containing `settings.gradle.kts`).
2. Use JDK 17 and allow Gradle to resolve the Android Gradle Plugin and dependencies.
3. Run **Build → Make Project**, then **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or run `gradle --no-daemon --stacktrace assembleDebug` from the project root.
4. The debug APK should be at `app/build/outputs/apk/debug/app-debug.apk`.

### GitHub Actions
Push the project to a GitHub repository and run **Actions → Build Capsule Lite APK**. The workflow runs `scripts/validate_source.py`, compiles the debug APK, and uploads `capsule-lite-v38-debug-apk`.

## Important limits

Local static validation is not a substitute for Kotlin compilation. This delivery environment has no Gradle executable/wrapper, Android SDK, or `android.jar`, so this source archive has **not** been compiled into an APK here. A real build and device run remain release gates.

## Suggested final device test

See `MANUAL_TEST_PLAN.md`. Test the installed app only after CI or Android Studio successfully produces the APK. Verify existing media, calls, Maps, charging, notification actions/replies, quick settings, gestures, themes, accessibility, and service recovery as well as the new live-activity behavior.

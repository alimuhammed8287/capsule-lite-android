# Build and validation results — Capsule Lite v38

Date: 2026-10-26

## Completed locally

- `scripts/validate_source.py`: PASS — 38 XML files parsed and all 13 source/metadata assertions passed.
- Kotlin delimiter scan: PASS — all 10 Kotlin files have balanced delimiters; this is a sanity check, not a compiler.
- ZIP integrity: checked when packaging.

## Android build status

**APK build: NOT RUN / BLOCKED by environment.** This runtime has no Gradle executable, Gradle wrapper JAR, Android SDK, or `android.jar`. Network/DNS access to obtain a Gradle distribution is unavailable. Therefore no APK compilation, emulator test, or physical-device test is claimed.

## Reproducible build

From the project root in an Android development environment with JDK 17 and Android SDK installed:

```bash
gradle --no-daemon --stacktrace assembleDebug
```

Alternatively, push to GitHub and run `.github/workflows/build.yml` (`workflow_dispatch` is enabled). The workflow validates source/XML, runs `assembleDebug`, and uploads `app/build/outputs/apk/debug/app-debug.apk` as `capsule-lite-v38-debug-apk`.

## Release gate

Do not consider v38 release-ready until CI or Android Studio compiles it and the device test plan passes. A static source check cannot establish Kotlin compilation or runtime correctness.

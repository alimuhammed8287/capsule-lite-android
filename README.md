# Capsule Lite — Android Dynamic Island

Android Dynamic Island-style overlay app, currently at **v38 (1.3.0)**.

## Status

**Source upload pending.** This repository has been initialized, but the full v38 Android source tree has not yet been committed. Do not interpret the current contents as a buildable app.

## Planned build

Once the full source is uploaded, use Android Studio with JDK 17 or run `gradle --no-daemon --stacktrace assembleDebug` from the project root. Expected output: `app/build/outputs/apk/debug/app-debug.apk`.

## CI

The GitHub Actions workflow will run source validation and compile a debug APK after the complete project is present.

## Source

The current v38 source archive is `capsule-lite-android14-v38-remaining-phases-source.zip`, available in the project conversation. Extract the inner `capsule_lite_v37_source/` folder into the repository root; do not upload the ZIP alone if you want CI to build.

## Testing

Compilation and Android device behavior are not yet verified.

# Capsule Lite v45 — charging pill bug-fix and regression test plan

Run after the v44 debug APK builds successfully. Record pass/fail and device/OS version for each section.

## A. Install and permissions
- [ ] Install over the prior version without losing existing settings.
- [ ] Grant notification-listener and overlay access; launch the pill.
- [ ] Deny overlay access and verify the app reports the missing permission without crashing.
- [ ] Revoke and restore notification access; confirm service rebind/recovery.

## B. Live-activity priority and handoff
- [ ] Start a timer and a download/progress activity: timer stays visible.
- [ ] Update the visible timer: it remains stable and continues counting.
- [ ] Complete/expire the timer: completion appears briefly, then the next eligible activity appears.
- [ ] Complete progress at 100%: completion appears briefly, then another activity is promoted if available.
- [ ] Remove the visible notification manually: next eligible activity is promoted.
- [ ] Verify no stale activity remains after its source notification is removed.
- [ ] Send an ordinary notification during a live activity; verify it is queued and appears after live activities finish.
- [ ] Verify an incoming/ongoing call takes priority over all live activities.

## C. App filtering
- [ ] Blank allow-list accepts timer/progress activities from eligible apps.
- [ ] Add one package name and verify only that package's timer/progress activities appear.
- [ ] Add a package to the existing hidden-app list and verify it remains hidden even if allow-listed.
- [ ] Clear the allow-list and confirm the default behavior returns.

## D. Existing feature regression
- [ ] Music metadata, artwork, play/pause, next/previous, and seek.
- [ ] Maps navigation, turn updates, ETA, and driving mode.
- [ ] Incoming call, answered call, hangup, and call dismissal behavior.
- [ ] Charging percentage, metrics, and alerts.
- [ ] Quick settings/control panel, gestures, notification reply/actions, history, themes, blur fallback, and custom image.

## E. Motion, battery, and lifecycle
- [ ] Reduce motion disables staggered/morph animations without breaking expansion.
- [ ] Android system animation scale off is respected.
- [ ] Screen off/on, lock/unlock, landscape, and display cutout.
- [ ] Force-stop/reopen and service disconnect/reconnect behavior.
- [ ] Leave idle for 30–60 minutes and compare battery use with visualizer off/on.
- [ ] Confirm no repeated crash/notification errors in diagnostics.

## Record results
- Device / Android / OEM skin:
- APK version and build commit:
- Failed checks and copied diagnostics:
- Battery observations (duration, start/end percentage, visualizer state):


## F. v44 focused acceptance checks
- [ ] During active Maps navigation, the collapsed pill shows remaining route distance first when available.
- [ ] If total route distance is unavailable, the collapsed pill falls back to next-turn distance, then ETA, then Maps.
- [ ] Switch repeatedly between charging, Maps, media, calls, and ordinary notifications; verify no stale icon/text or stuck overlay remains.
- [ ] Enable Reduce Motion and repeat expand/collapse; confirm dimensions and text remain correct.
- [ ] Disable and restore notification access; confirm service recovery and overlay restoration.
- [ ] Confirm blur and tinted fallback paths do not leave a dim layer behind.
- [ ] Reboot and verify configured keep-running behavior and diagnostics.

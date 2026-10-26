# Capsule Lite build timeline

This log tracks source changes separately from their CI output. A build is considered ready only when the relevant GitHub Actions run succeeds and its APK artifact is uploaded. A successful CI build is not the same as physical-device validation.

| Date (UTC) | Version / build | Main output | CI result / APK |
|---|---|---|---|
| 2026-10-26 | v1.3.0 / v38 | Live-activity priority and handoff, per-app allow-list, reduced-motion setting, initial source/XML validation workflow | [Build run 40312071261](https://github.com/alimuhammed8287/capsule-lite-android/actions/runs/40312071261) · artifact: `capsule-lite-v38-debug-apk` |
| 2026-10-26 | v1.3.1 / v39 | Expanded charging-pill controls and UI/accessibility refinements; versioned settings backup work | [Successful v39 run 40318404824](https://github.com/alimuhammed8287/capsule-lite-android/actions/runs/40318404824) · artifact: `capsule-lite-v39-debug-apk` |
| 2026-10-26 | v1.3.2 / v40 | Settings restore merge explanation and visible failure feedback | [Successful v40 run 40319436164](https://github.com/alimuhammed8287/capsule-lite-android/actions/runs/40319436164) · artifact: `capsule-lite-v40-debug-apk` |
| 2026-10-26 | v1.3.3 / v41 | **Settings UI redesign:** clearer section title hierarchy, short section-specific descriptions, dividers, adjusted card spacing, and theme-aware card surfaces | [v41 redesign build run](https://github.com/alimuhammed8287/capsule-lite-android/actions/runs/40326572203) · expected artifact name: `capsule-lite-v41-debug-apk` · status must be checked before installation |
| 2026-10-26 | v1.3.5 / v43 | Coordinated charging-pill motion: liquid fill, overshoot resize, detail crossfade, stable battery icon, and swipe expand/collapse. | [Successful build run 40337689986](https://github.com/alimuhammed8287/capsule-lite-android/actions/runs/40337689986) · artifact: `capsule-lite-v43-debug-apk` · device test pending |
| 2026-10-26 | v1.3.7 / v45 | Combined hardening pass: prioritize remaining route distance in collapsed Maps text, retain existing service teardown and motion-setting guards, expand regression/device test checklist, and run source/XML validation plus Gradle compilation in CI. | CI pending · artifact target: `capsule-lite-v44-debug-apk` |

## v1.3.3 UI redesign output

- Settings cards use clearer title styling rather than small all-caps headings.
- Each section includes a short explanation of what its controls affect.
- A subtle divider separates the section introduction from its controls.
- Card surface, border, and elevation are tuned for Light, Dark, and AMOLED themes.
- The category header identifies the settings context and v1.3.3.
- Existing settings controls and stored preference keys are retained; this change is intended as a presentation redesign, not a settings behavior rewrite.

| 2026-10-26 | v1.3.7 / v45 | Charging pill tap-to-expand default, active-charging-only visibility, touch-window adjustment, retained full-charge alert, charging regression checklist | CI pending; device validation pending |

## Current validation state

- v43 CI passed and `capsule-lite-v43-debug-apk` was uploaded; physical-device test of charging-pill motion and swipe gestures remains pending.
- v44 CI passed and `capsule-lite-v44-debug-apk` was uploaded; charging-pill defects were reported during device testing.
- v45 contains fixes for tap-to-expand, active-charging-only visibility, and overlay touch handling. CI and physical-device regression tests are pending.
- The animation uses the existing main-pill physics spring and a spring-like overshoot resize on the secondary pill; this is not yet a full shared-element/container-transform implementation across every notification type.

## Build output record template

For each later build, record: UTC date, versionCode/versionName, phase/change summary, commit SHA, CI run URL, validation result, artifact name, artifact expiry, and whether a physical-device test was performed.

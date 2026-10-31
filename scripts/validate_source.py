#!/usr/bin/env python3
"""Fast source/metadata checks; these are not a substitute for Kotlin compilation."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []
warnings = []
xml_files = list(ROOT.rglob("*.xml"))
for path in xml_files:
    try:
        ET.parse(path)
    except Exception as exc:
        errors.append(f"XML parse failed: {path.relative_to(ROOT)}: {exc}")

build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
service = (ROOT / "app/src/main/java/com/example/capsule/CapsuleService.kt").read_text(encoding="utf-8")
policy_path = ROOT / "app/src/main/java/com/example/capsule/LiveActivityPolicy.kt"
policy = policy_path.read_text(encoding="utf-8") if policy_path.exists() else ""
main = (ROOT / "app/src/main/java/com/example/capsule/MainActivity.kt").read_text(encoding="utf-8")
history = (ROOT / "app/src/main/java/com/example/capsule/ChargingSessionHistory.kt").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
activity_model_path = ROOT / "app/src/main/java/com/example/capsule/CapsuleActivityModel.kt"
activity_model = activity_model_path.read_text(encoding="utf-8") if activity_model_path.exists() else ""
agp_match = re.search(r'id\("com\.android\.application"\)\s*version\s*"([^"]+)"', root_build)
compile_sdk_match = re.search(r"compileSdk\s*=\s*(\d+)", build)
ci_gradle_match = re.search(r"gradle-version:\s*'([^']+)'", workflow)
wrapper_gradle_match = re.search(r"gradle-(\d+(?:\.\d+)+)-bin\.zip", wrapper)

def version_tuple(value: str) -> tuple[int, ...]:
    return tuple(int(part) for part in re.findall(r"\d+", value))

if agp_match and compile_sdk_match:
    agp_version = version_tuple(agp_match.group(1))
    compile_sdk = int(compile_sdk_match.group(1))
    if compile_sdk >= 37 and agp_version < (9, 1, 1):
        warnings.append(f"compileSdk {compile_sdk} needs AGP 9.1.1 or newer; configured AGP is {agp_match.group(1)}")
    if agp_version[:2] == (8, 13) and ci_gradle_match and version_tuple(ci_gradle_match.group(1)) < (8, 13):
        warnings.append(f"AGP {agp_match.group(1)} needs Gradle 8.13 or newer; CI pins Gradle {ci_gradle_match.group(1)}")
    if agp_version[:2] == (8, 13) and wrapper_gradle_match and version_tuple(wrapper_gradle_match.group(1)) < (8, 13):
        warnings.append(f"AGP {agp_match.group(1)} needs Gradle 8.13 or newer; wrapper pins Gradle {wrapper_gradle_match.group(1)}")
charging_icon_match = re.search(r'val icons = listOf\(([^)]*)\)', main)
charging_icon_names = re.findall(r'"([^"]+)"', charging_icon_match.group(1)) if charging_icon_match else []
art_start = service.find("icon = ImageView(uiCtx)")
art_end = service.find("title = TextView(uiCtx)", art_start) if art_start >= 0 else -1
media_art_view = service[art_start:art_end] if art_start >= 0 and art_end > art_start else ""
checks = {
    "versionCode is positive": bool(re.search(r"versionCode\s*=\s*[1-9]\d*\b", build)),
    "v1.4.21 upgrade metadata": 'versionCode = 69' in build and 'versionName = "1.4.21"' in build,
    "notification count badge has adjustable size and independent colors": all(token in main for token in (
        '"Count badge size (dp)"', '"notificationBadgeSize"', 'notificationBadgeColorButton(queueLook',
    )) and all(token in service for token in (
        'notificationBadgeSizeDp()', 'notificationBadgeFillColor()', 'applyNotificationBadgeAppearance()',
    )) and all(token in (ROOT / "app/src/main/java/com/example/capsule/CapsulePrefs.kt").read_text(encoding="utf-8") for token in (
        '"theme" to "Match queue / pill theme"', '"blue" to "Blue"', '"gray" to "Graphite"',
    )),
    "companion collapse preserves main pill bounds": service.count("releaseCompanionSlotWithoutMainMotion(previousSideSlots)") >= 4 and
        "private fun releaseCompanionSlotWithoutMainMotion(previousSideSlots: Int)" in service and
        "springW.offsetBy(windowDelta.toFloat())" in service and "fun offsetBy(delta: Float)" in (ROOT / "app/src/main/java/com/example/capsule/CapsuleMotion.kt").read_text(encoding="utf-8"),
    "app version is declared and home title is present": bool(re.search(r'versionName\s*=\s*"([^"]+)"', build)) and
        'title = "Capsule Lite"' in main,
    "adaptive ticker": "nextTickDelayMs()" in service,
    "screen-aware visualizer": "isInteractive" in service,
    "live activity classification": "fun classify(sbn: StatusBarNotification)" in policy,
    "activity priority ordering": "fun ordered(candidates" in policy,
    "progress promotion guard": "canPromoteLiveActivity(sbn.key, LiveActivityPolicy.Kind.PROGRESS.priority)" in service,
    "timer promotion guard": "canPromoteLiveActivity(sbn.key, LiveActivityPolicy.Kind.TIMER.priority)" in service,
    "live activity app allow-list": 'getString("liveActivityApps"' in service and 'liveActivityApps' in main,
    "completion handoff": "finishLiveActivity" in service and "restoreNextLiveActivity" in service,
    "reduced motion setting": '"reduceMotion"' in main and '"reduceMotion"' in service,
    "CI builds debug APK": "assembleDebug" in workflow,
    "local wrapper and CI use the same Gradle version": bool(wrapper_gradle_match and ci_gradle_match) and wrapper_gradle_match.group(1) == ci_gradle_match.group(1),
    "CI APK and artifact names include app version, build code, and unique Actions run number": all(token in workflow for token in (
        'Dynamic Island Project-v${APP_VERSION}-build${APP_VERSION_CODE}-run${RUN_NUMBER}.apk',
        'Dynamic-Island-Project-v${{ steps.app-version.outputs.name }}-build${{ steps.app-version.outputs.code }}',
        "steps.signing.outputs.mode", "github.run_number",
    )),
    "main and companion notification swipes dismiss in either horizontal direction": all(token in service for token in (
        "if (far && abs(direction) > 0f)", "if (direction > 0f) 1f else -1f",
        "private fun mainPillNotificationSwipeTarget(): Boolean",
        "shouldShowCollapsedNotificationPreview()",
        "dismissMediaNotification(notificationQueue.values.lastOrNull()?.key ?: notifKey)",
        "dismissMediaNotification(notificationBubbleKey)",
        "private fun dismissExpandedNotification()",
    )) and "swipe right to browse older notifications" not in service,
    "notification body uses a fixed non-scrolling container and horizontal dismiss gestures": all(token in service for token in (
        "private class NotificationBodyPanel(ctx: Context) : FrameLayout(ctx)",
        "horizontalGestureHandler = { event -> onNotificationBodyTouch(event) }",
        "var horizontalGestureHandler: ((MotionEvent) -> Boolean)? = null",
        "override fun onInterceptTouchEvent(event: MotionEvent): Boolean",
        "override fun onTouchEvent(event: MotionEvent): Boolean",
        "private fun onNotificationBodyTouch(event: MotionEvent): Boolean",
        "abs(dx) > abs(dy) * 1.25f", "notificationBodyHorizontalSwipe",
        "subtitle.maxLines = bodyLines", ".setMaxLines(maxLines.coerceAtLeast(1))",
    )) and "private class NotificationBodyPanel(ctx: Context) : android.widget.ScrollView(ctx)" not in service,
    "expanded notification auto-fits content to adjustable height without vertical scrolling": all(token in service for token in (
        "notificationPanelContentHeight(configuredMaximum)",
        "private fun notificationPanelContentHeight(maximumHeight: Int): Int",
        'notificationWinner == "notifications"',
        "return maxOf(safeMinimum, contentHeight).coerceAtMost(maxOf(safeMinimum, maximumHeight))",
    )) and 'slider(heights, "Maximum expanded notification height (dp)", "expH_notif", 64, 360, 220)' in main,
    "idle notification badge swipe-down expands the latest message and preserves taps": all(token in service for token in (
        "badgeSwipeCaptured", "badgeExpandedFromSwipe = true", "dy >= dp(10)",
        "notificationQueue.values.lastOrNull() ?: pending", "setExpanded(true)",
    )) and "contentDescription = \"Unread notifications\"" in service,
    "gesture settings open an explicit action choice list": "Choose ${CapsulePrefs.gestureLabels[key]} action" in main and "setSingleChoiceItems(labels, selected)" in main,
    "launcher icon resources are declared in the manifest": all(token in manifest for token in (
        'android:icon="@mipmap/ic_launcher"', 'android:roundIcon="@mipmap/ic_launcher_round"',
    )) and all((ROOT / path).exists() for path in (
        "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
        "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
        "app/src/main/res/drawable/capsule_launcher_foreground.xml",
    )),
    "secondary charging overlay removed": all(token not in service for token in ("chargeRoot", "chargeWindow", "updateSeparateChargingPill", "separateChargePill")) and "Separate pill gestures" not in main,
    "compact and expanded charging use the same selected icon": service.count('getInt("chargeIconShape", 9)') >= 3 and service.count("ChargeRingView(uiCtx") >= 2,
    "expanded media shows one swipe-browsable notification with dismiss": all(token in service for token in ("shouldShowMediaWithNotification", "mediaNotificationCard", "browseMediaNotification", "dismissMediaNotification", "notificationQueue.isNotEmpty()")),
    "collapsed queue keeps details out of the compact pill": "if (!expanded) return collapsedTargetHeight()" in service and "notificationBadge.visibility = vis(total > 0)" in service,
    "notification card supports independent and blended themes": "mediaNotificationCard.background = notificationQueueSurface()" in service and "notificationQueueThemeId() == \"blend\"" in service and "private fun notificationQueueStyle()" in service,
    "10-second seek controls have visible vector glyphs and seek actions": all(token in service for token in ("R.drawable.ic_rewind_10", "R.drawable.ic_forward_10", "seekBy(-10_000L)", "seekBy(10_000L)")) and all((ROOT / f"app/src/main/res/drawable/{name}.xml").exists() for name in ("ic_rewind_10", "ic_forward_10")),
    "charging ring size is adjustable and compact bolt is removed": 'slider(tuning, "Charging ring size (dp)", "chargeRingSize", 36, 80, 48, onValueChanged = { value ->' in main and 'getInt("chargeRingSize", 48)' in service and 'val percentText = "$chargePct%"' in service and 'miniRight.text = "⚡ $chargePct%"' not in service,
    "collapsed charging readout offers percentage watts and both": all(token in main for token in ('"chargeMiniReadout"', '"Percentage", "Watts", "Both"')) and all(token in service for token in ('"watts" ->', '"both" ->', 'chargeWatts?.takeIf')),
    "collapsed charging width previews during drag and persists on release": 'slider(tuning, "Collapsed charging pill width (dp)", "chargeMiniW", 104, 240, 116, onValueChanged = { value ->' in main and 'prefs.getInt("chargeMiniW", 116)' in service,
    "expanded charging height is visible and shared with custom sizing": all(token in main for token in (
        '"Expanded charging height (dp)", "expH_charge", 40, 440, 64',
        '"sizeExpandedHeight_charging", value',
        'startValueOverride = effectiveChargingHeight',
    )) and 'customSizePx(activePillSizingSection(), "ExpandedHeight", desired, floor, available)' in service,
    "Circular Wave controls update preview and save on release": all(token in main for token in (
        '"chargeRingThickness", 2, 10, 3, onValueChanged = { value ->',
        '"chargeRemainderThickness", 1, 6, 2, onValueChanged = { value ->',
        '"chargeRemainderColor"', '"Dark gray"',
        '"chargeWaveSpacingDp", 3, 24, legacyWaveSpacingDp, onValueChanged = { value ->',
        '"chargeWaveSpeed", 1, 40, previewSpeed',
        'cycleDurationMs = previewCycleDuration()',
        'waveSpeedFromLegacy(prefs.getInt("chargeRingSpeed", 5))',
        '"chargeWaveHeight", 2, 22, 9, onValueChanged = { value ->',
        '"chargeWaveInnerSpacingPercent", 0, 70, 28, onValueChanged = { value ->',
        '"chargeWaveSoftness", 0, 100, 55, onValueChanged = { value ->',
    )) and "onStopTrackingTouch(sb: SeekBar)" in main and 'else prefs.edit().putInt(key, value).apply()' in main and
        "innerWaveSpacingPercent = previewInnerSpacing" in main and 'getInt("chargeWaveInnerSpacingPercent", 28)' in service and
        'chargeWaveCycleDurationMs()' in service and "referenceRingSizeDp = referenceRingSizeDp()" in service and
        "innerWaveSpacingPercent = innerWaveSpacing()" in service and "innerWaveSpacingPercent: Int" in (ROOT / "app/src/main/java/com/example/capsule/CircularWaveChargePreview.kt").read_text(encoding="utf-8"),
    "Circular Wave animates over a separately colored and sized fixed remainder": all(token in (ROOT / "app/src/main/java/com/example/capsule/CircularWaveChargePreview.kt").read_text(encoding="utf-8") for token in (
        "canvas.drawArc(remainingBounds, -90f + chargedSweep, 360f - chargedSweep, false, remaining)",
        "remaining.alpha = 255",
        "remaining.color = remainderColor",
        "remaining.strokeWidth = remainderThickness",
        "remainderThicknessDp: Int",
        "remainderColor: Int",
        "val phaseOffset = if (motionEnabled)",
        "val movingWave = shapedWave(angleFromTop + phaseOffset",
        "val envelope = if (full) 1f else smoothJoin",
        "val shouldAnimate = motionEnabled && isAttachedToWindow && isShown &&",
        "canvas.drawPath(charged, active)",
        "No traveling white highlight",
    )) and "canvas.drawPath(contour, remaining)" not in (ROOT / "app/src/main/java/com/example/capsule/CircularWaveChargePreview.kt").read_text(encoding="utf-8") and all(token in service for token in (
        'getInt("chargeRemainderThickness", 2).coerceIn(1, 6)',
        'getInt("chargeRemainderColor", 0)',
        "remainderThicknessDp = remainderThickness()",
        "remainderColor = remainderTint()",
    )),
    "Circular Wave stroke weight scales with each live ring size": all(token in (ROOT / "app/src/main/java/com/example/capsule/CircularWaveChargePreview.kt").read_text(encoding="utf-8") for token in (
        "val displayScale = unitPx / referenceUnitPx",
        "configuredThickness * displayScale",
        "configuredRemainderThickness * displayScale",
        "density * 1.5f * displayScale",
    )),
    "expanded, collapsed, and companion Circular Wave rings share one phase": service.count("{ chargeWavePhase() }") >= 3 and
        "private fun chargeWavePhase(): Float" in service and "sharedWavePhase?.invoke()?.coerceIn(0f, 1f) ?: phase" in service,
    "notification companion dismisses its displayed queue item": "dismissMediaNotification(notificationBubbleKey)" in service and "private fun dismissMediaNotification(key: String? = null)" in service and "notificationQueue[key]" in service,
    "Maps, charging, and notifications can occupy three separate pills": "hasPersistentNotificationHost" in activity_model and "notificationsStayInTheirOwnPillWhenMapsAndChargingAreActive" in (ROOT / "app/src/test/java/com/example/capsule/CapsuleActivitySchedulerTest.kt").read_text(encoding="utf-8"),
    "companion pill motion follows the selected animation style in both directions": "private fun companionMotionProfile(expanding: Boolean)" in service and
        "companionMotionProfile(activityBubbleMotionExpanding)" in service and "companionMotionProfile(notificationBubbleMotionExpanding)" in service and
        "companionMotionExpansionOverride = !e" in service and "activityBubbleMotionExpanding = companionMotionExpansionOverride ?: desired" in service and
        "notificationBubbleMotionExpanding = companionMotionExpansionOverride ?: desired" in service and
        "private fun companionHiddenScale()" in service and "private fun companionDamping(" in service,
    "main pill tap and charging companion retain their touch-down owner": all(token in service for token in (
        "tapGestureOwnerId = expandGestureActivityId", "val tapOwner = if (tapGestureOwnerCaptured) tapGestureOwnerId else activityWinner()",
        "tapGestureOwnerCaptured = !expandSwipeDelegatedToChild",
        'tapOwner in setOf(null, "notifications")', 'tapOwner == "charging"', 'tapOwner == "media"',
        "val touchedKind = activityBubbleTouchDownKind", "activityBubble.performClick()",
    )),
    "main-pill swipe down intercepts child gestures only for configured vertical expansion": all(token in service for token in ("override fun onInterceptTouchEvent(event: MotionEvent)", "shouldCapturePillDownSwipe(dx, dy)", "interceptingPillDownSwipe = true", "dy >= dp(10)", "abs(dy) > abs(dx) * 1.15f")),
    "companion and notification swipes retain their own activity owner": all(token in service for token in ("expandSwipeDelegatedToChild = dedicatedGestureSurfaceAt(event.rawX, event.rawY)", "if (!expandSwipeDelegatedToChild && shouldCapturePillDownSwipe(dx, dy))", "private fun dedicatedGestureSurfaceAt(rawX: Float, rawY: Float)", "touchInsideVisibleView(activityBubble", "touchInsideVisibleView(notificationBubble", "focusActivity(it, expand = true)", "private fun onNotificationBubbleTouch(event: MotionEvent)")),
    "manual notification focus clears a previously selected activity": "private fun focusNotifications()" in service and "focusedActivityId = null\n        userFocusedNotification = true" in service and service.count("focusNotifications()") >= 5,
    "Circular Wave joins its stationary remainder with a short smooth endpoint blend": all(token in (ROOT / "app/src/main/java/com/example/capsule/CircularWaveChargePreview.kt").read_text(encoding="utf-8") for token in ("canvas.drawArc(remainingBounds", "val fadeSpan = (Math.PI * 2.0 / lobes) * 0.04", "val smoothJoin = joinProgress * joinProgress * (3f - 2f * joinProgress)", "val sampleCount = max(32, (640f * progress).toInt())")),
    "media cover art is clipped to a perfect circle": "clipToOutline = true" in media_art_view and "shape = GradientDrawable.OVAL" in media_art_view,
    "reference charge ring maps gap to battery percentage": all(token in service for token in ("drawIrregularRing", "val progress = percent().coerceIn(0, 100) / 100f", "val gapSweep = 360f * (1f - progress)", "movingPhase")),
    "charging icon uses a shared interruptible continuity transition": all(token in service for token in ("private fun prepareChargeIconFlight", "private fun startChargeIconFlight", "chargeFlightRect", "ValueAnimator.ofFloat(0f, 1f)", "destination.getLocationOnScreen(targetLoc)", "cancelChargeIconFlight(keepImage = true)")) and "chargeFlightRect.set(rect)" in service,
    "pill animation selector is visible with appearance choices": all(token in main for token in ("pillAnimationStyleButton(look)", "Liquid morph", "Icon continuity", "Settling dock", "pillAnimationStyle", "Expansion animation:")) and "pillAnimationStyleButton(motion)" not in main and all(token in service for token in ('"liquid_morph"', '"continuity"', '"settling_dock"')),
    "charging animation cleanup is safe before overlay initialization": all(token in service for token in ("if (::chargeRing.isInitialized)", "if (::miniChargeRing.isInitialized)", "if (::chargeBox.isInitialized)", "if (::miniRight.isInitialized)")) and "if (::chargeTop.isInitialized && ::chargeBottom.isInitialized)" in service,
    "settling dock unfolds content without the shared icon flight": all(token in service for token in ("settlingDockEntrance", "animateSettlingChargeIcon", ".translationY(0f)", "DecelerateInterpolator(1.3f)")) and 'style == "continuity" && motionEnabled() && prepareChargeIconFlight(e)' in service and 'pillAnimationStyle() == "settling_dock"' in service,
    "charging settings expose main pill and icon selectors": 'Show charging status in the main pill' in main and 'Charging style:' in main and 'Charging icon:' in main and all(token not in main for token in ("chargePillTap", "chargeMiniH", "chargeMiniYOffset")),
    "charging selection follows the user activity priority": 'winner == "charging" && charging && showCharging()' in service and 'activityWinner() == "charging"' in service,
    "collapsed Maps pill prioritises next-turn distance": bool(re.search(r"private fun mapsMiniText\(\): String = MapsNav\.turnDistance\.ifEmpty\s*\{\s*MapsNav\.distanceLeft", service)),
    "Maps navigation pill enabled by default": '"mapsInfo", true' in main and 'getBoolean("mapsInfo", true)' in service and 'getBoolean("mapsInfo", false)' not in service,
    "settings switches have readable touch targets": "minHeight = dp(56)" in main and "thumbTintList" in main and "setTextColor(primaryTextColor())" in main,
    "diagnostic and save status text follows app theme": "repairText = TextView(this).apply {" in main and "setTextColor(secondaryTextColor())" in main and "saveMsg = TextView(this).apply { textSize = 13f; setTextColor(primaryTextColor()) }" in main,
    "frosted glass avoids whole-window blur": "flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND" not in service and "translucent, bordered surface" in service,
    "twelve charging icon designs are selectable and rendered": len(charging_icon_names) == 12 and all(name in main for name in ("One UI battery", "Pixel battery", "Nothing glyph dots", "Pixel charge ring", "Horologist wavy ring", "Circular Wave ring")) and "selectedShape().coerceIn(0, 11)" in service,
    "setup opens a separate diagnostic page": 'button(health, "Open diagnostic details") { open("diagnostics") }' in main and '"diagnostics" -> buildDiagnostics()' in main,
    "home has one compact title without the default action bar or oversized top gap": all(token in main for token in ("CollapsingToolbarLayout(materialContext)", 'title = ""', "isTitleEnabled = false", 'title = "Capsule Lite"', "dp(if (landscape) 96 else 112)", "bottomMargin = dp(8)", "AppBarLayout.ScrollingViewBehavior()", 'menu.add(0, 9001, 0, "Search")', 'menu.add(0, 9002, 1, homeThemeLabel())')) and 'android:theme="@style/CapsuleMaterialComponentsTheme"' in manifest and 'parent="Theme.MaterialComponents.DayNight.NoActionBar"' in (ROOT / "app/src/main/res/values/styles.xml").read_text(encoding="utf-8"),
    "Material collapsing header receives its required AppCompat theme": "ContextThemeWrapper(" in main and "R.style.CapsuleMaterialComponentsTheme" in main and 'parent="Theme.MaterialComponents.DayNight.NoActionBar"' in (ROOT / "app/src/main/res/values/styles.xml").read_text(encoding="utf-8"),
    "settings sections use a compact toolbar and clear heading hierarchy": all(token in main for token in ("private fun addSectionToolbar()", "private fun addSettingsIntro()", "textSize = 30f", "sectionDescriptions[cat]")) and 'button(col, "‹  All settings")' not in main,
    "settings redesign has per-section guidance": all(label in main for label in ("Service health, recent events, and troubleshooting details.", "Choose the appearance of the Capsule Lite settings screens.", "Use a translucent layered look with a safe fallback on unsupported devices.")),
    "theme modes are available from the home header": all(token in main for token in ('"system", "light", "dark", "amoled"', '"App theme"', '"AMOLED black"', "private fun homeThemeLabel()")),
    "home tiles have accessible spoken descriptions": 'contentDescription = if (destination == "more")' in main and 'else "$title. $subtitle${if (homeCustomizationSection(destination)?.let(::isSectionCustomized) == true) ". Customized" else ""}"' in main and "isFocusable = true" in main,
    "home category icons exist and section previews remain scoped": all((ROOT / f"app/src/main/res/drawable/{name}.xml").exists() for name in ("ic_cat_setup", "ic_cat_pill", "ic_cat_music", "ic_cat_tasks", "ic_cat_notifications", "ic_cat_maps", "ic_cat_apps", "ic_cat_charging", "ic_cat_appearance", "ic_cat_privacy", "ic_cat_tools")) and "sectionPreview(cat!!)?.let" in main,
    "home tile navigation arrows keep light-theme contrast": 'setTextColor(if (isLightAppTheme()) 0xFF64748B.toInt() else 0xFF8CA2B8.toInt())' in main,
    "slider accessibility announces its current value": 'contentDescription = "$label: $start"' in main and 'sb.contentDescription = "$label: $value"' in main,
    "editable fields follow selected app theme": main.count("setHintTextColor(secondaryTextColor())") >= 4 and main.count("setTextColor(primaryTextColor())") >= 4,
    "editable fields and charging symbol have explicit accessibility labels": all(label in main for label in ("Live-activity app allow-list, comma-separated package names", "Quick reply messages, comma-separated, up to three", "Apps whose notifications should be hidden, comma-separated package names", "Choose charging icon design", "Search installed apps to pin")),
    "package-name fields disable autocorrect suggestions": main.count("TYPE_TEXT_FLAG_NO_SUGGESTIONS") >= 2,
    "custom settings saves show confirmation and pinned apps persist immediately": all(message in main for message in ("Live-activity app list saved", "Quick replies saved", "Hidden-app list saved", 'putString("pinned", saved).apply()')),
    "reset to defaults requires explicit confirmation": 'setTitle("Reset all settings?")' in main and 'setNegativeButton("Cancel", null)' in main and 'setPositiveButton("Reset")' in main,
    "settings import rejects empty or unsupported files": "var restoredCount = 0" in main and 'if (restoredCount == 0) error("no supported settings found in this file")' in main and 'else -> error("unsupported value for setting $k")' in main,
    "settings import rejects numeric overflow": "val floatValue = v.toFloat()" in main and 'if (!floatValue.isFinite()) error("number is out of range for $k")' in main,
    "settings export reports stream-open and write failures": 'val output = contentResolver.openOutputStream(uri, "wt")' in main and 'error("could not open the selected file for writing")' in main and "stream.flush()" in main,
    "settings export shows success and failure feedback": 'Toast.makeText(this, "Settings backup saved"' in main and 'Toast.makeText(this, "Settings backup failed"' in main and 'it.message ?: "file access error"' in main,
    "settings backups include compatible version metadata": 'json.put("_capsuleLiteBackupVersion", 1)' in main and 'json.put("_capsuleLiteExportedAt", System.currentTimeMillis())' in main and 'if (backupVersion != 1) error("unsupported backup version: $backupVersion")' in main and 'k == "_capsuleLiteBackupVersion" || k == "_capsuleLiteExportedAt"' in main,
    "tools explain backup compatibility and restore confirmation": "Settings backups use a versioned JSON format." in main and "Older backups are supported; restore asks before changing your current settings." in main,
    "restoring settings requires explicit confirmation": 'setTitle("Restore settings?")' in main and 'setPositiveButton("Restore")' in main and 'setNegativeButton("Cancel", null)' in main and 'Settings restored from the file.' in main,
    "restore confirmation explains merge behavior": "Settings in this file will overwrite matching Capsule Lite choices." in main and "Settings not included in the file will remain unchanged." in main,
    "settings restore failures show feedback": 'saveMsg.text = "Could not restore: ${it.message ?: "invalid settings file"}"' in main and 'Toast.makeText(this, "Settings restore failed"' in main,
    "app theme choices include system light dark and AMOLED": all(name in main for name in ('"system", "light", "dark", "amoled"', "Follow system", "AMOLED black", "appThemeButton")),
    "collapsed Maps pill displays remaining route distance": 'distanceLeft.takeIf { it.isNotEmpty() }?.let { "$it left" }' in service,
    "Maps navigation defaults enabled": 'getBoolean("mapsInfo", true)' in service and '"mapsInfo", true)' in main,

    "diagnostic sharing uses scoped file access": "shareDiagnosticsFile()" in main and "FileProvider.getUriForFile" in main and 'android:exported="false"' in manifest and "shared_file_paths" in manifest and "Share diagnostics as .txt" in main,
    "diagnostic recovery reports connection state and offers reconnect": all(token in main for token in ("Enabled, not connected", "Next step:", "reconnectServices()", "Open diagnostic details")),
    "activity priorities can be reordered with calls fixed at the top": all(token in main for token in ("activityPriorityButton(shown)", "Move ", "Calls (fixed)")) and "activityPriorityOrder" in service and "fixedPrecedence = listOf(CapsuleActivityKind.CALL, CapsuleActivityKind.STOPWATCH)" in activity_model,
    "collapsed and expanded widths are screen-aware and ordered": all(token in main for token in ("availableWidthDp", "collapsedWidthMax", "expandedWidthMinimum", "expandedWidthControl?.setMinimum")) and all(token in service for token in ("collapsedReferenceWidth(section, screenMax)", "collapsedWidth + dp(16)", "return customSizePx(section, \"ExpandedWidth\", fallback, minUseful, screenMax)")),
    "compact charging ring height comparison uses dp consistently": "collapsedHeightDp = (collapsedTargetHeight() / resources.displayMetrics.density).toInt()" in service and "coerceAtMost(miniRingMaxDp)" in service,
    "charging session summaries are local and bounded": "ChargingSessionHistory.record(" in service and all(token in history for token in ("private const val PREFS", "MAX_SESSIONS = 20", "fun summary(context: Context)", "clear(context: Context)")),
    "stable debug signing is configurable and artifacts identify ephemeral builds": all(token in workflow for token in ("CAPSULE_DEBUG_KEYSTORE_BASE64", "mode=ephemeral", "mode=stable")) and "hasStableDebugSigning" in build,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
    if not ok:
        errors.append(f"Missing source check: {label}")
print(f"XML files parsed: {len(xml_files)}")
for warning in warnings:
    print(f"WARN: {warning}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Static source validation passed (does not prove Android compilation).")

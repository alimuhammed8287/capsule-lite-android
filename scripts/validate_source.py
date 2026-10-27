#!/usr/bin/env python3
"""Fast source/metadata checks; these are not a substitute for Kotlin compilation."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []
xml_files = list(ROOT.rglob("*.xml"))
for path in xml_files:
    try:
        ET.parse(path)
    except Exception as exc:
        errors.append(f"XML parse failed: {path.relative_to(ROOT)}: {exc}")

build = (ROOT / "app/build.gradle.kts").read_text()
service = (ROOT / "app/src/main/java/com/example/capsule/CapsuleService.kt").read_text()
policy_path = ROOT / "app/src/main/java/com/example/capsule/LiveActivityPolicy.kt"
policy = policy_path.read_text() if policy_path.exists() else ""
main = (ROOT / "app/src/main/java/com/example/capsule/MainActivity.kt").read_text()
workflow = (ROOT / ".github/workflows/build.yml").read_text()
charging_icon_match = re.search(r'val icons = listOf\(([^)]*)\)', main)
charging_icon_names = re.findall(r'"([^"]+)"', charging_icon_match.group(1)) if charging_icon_match else []
checks = {
    "versionCode 46": bool(re.search(r"versionCode\s*=\s*46\b", build)),
    "versionName 1.3.8": 'versionName = "1.3.8"' in build,
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
    "CI artifact name includes unique Actions run number": "capsule-lite-v46-debug-apk-run-${{ github.run_number }}" in workflow,
    "secondary charging overlay removed": all(token not in service for token in ("chargeRoot", "chargeWindow", "updateSeparateChargingPill", "separateChargePill")) and "Separate pill gestures" not in main,
    "compact and expanded charging use the same selected icon": "miniChargeRing = ChargeRingView" in service and "chargeRing = ChargeRingView" in service and service.count('getInt("chargeIconShape", 0)') >= 2,
    "charging icon uses a shared interruptible continuity transition": all(token in service for token in ("prepareChargeIconFlight", "startChargeIconFlight", "chargeFlightRect", "ValueAnimator.ofFloat(0f, 1f)", "destination.getLocationOnScreen(targetLoc)", "cancelChargeIconFlight(keepImage = true)")) and "if (chargeFlightPending) return" in service,
    "pill animation selector offers liquid continuity and settling styles": all(token in main for token in ("pillAnimationStyleButton(motion)", "Liquid morph", "Icon continuity", "Settling dock", "pillAnimationStyle")) and all(token in service for token in ('"liquid_morph"', '"continuity"', '"settling_dock"')),
    "charging animation cleanup is safe before overlay initialization": all(token in service for token in ("if (::chargeRing.isInitialized)", "if (::miniChargeRing.isInitialized)", "if (::chargeBox.isInitialized)", "if (::miniRight.isInitialized)")) and "if (::chargeTop.isInitialized && ::chargeBottom.isInitialized)" in service,
    "settling dock unfolds content without the shared icon flight": all(token in service for token in ("settlingDockEntrance", "animateSettlingChargeIcon", ".translationY(0f)", "DecelerateInterpolator(1.3f)")) and 'style == "continuity" && motionEnabled() && prepareChargeIconFlight(e)' in service and 'pillAnimationStyle() == "settling_dock"' in service,
    "charging settings expose main pill and icon selectors": 'Show charging status in the main pill' in main and 'Charging animation:' in main and 'Charging icon:' in main and all(token not in main for token in ("chargePillTap", "chargeMiniW", "chargeMiniH", "chargeMiniYOffset")),
    "compact Maps suppresses inline charging": "!mapsShown() && controller == null && charging && showCharging()" in service,
    "collapsed Maps pill prioritises remaining route distance in leading text": "private fun mapsMiniText(): String = MapsNav.distanceLeft.ifEmpty { MapsNav.turnDistance.ifEmpty { MapsNav.eta.ifEmpty { \"Maps\" } } }" in service,
    "Maps navigation pill enabled by default": 'toggle(shown, "Navigation info in the pill (reads Maps\' navigation notification, and the Maps screen while it is open)", "mapsInfo", true)' in main and 'getBoolean("mapsInfo", true)' in service and 'getBoolean("mapsInfo", false)' not in service,
    "settings switches have readable touch targets": "minHeight = dp(52)" in main and "setTextColor(primaryTextColor())" in main,
    "diagnostic and save status text follows app theme": "repairText = TextView(this).apply { textSize = 13f; setTextColor(secondaryTextColor()) }" in main and "saveMsg = TextView(this).apply { textSize = 13f; setTextColor(primaryTextColor()) }" in main,
    "frosted glass avoids whole-window blur": "flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND" not in service and "translucent, bordered surface" in service,
    "eight charging icon designs are selectable and rendered": len(charging_icon_names) == 8 and all(name in main for name in ("One UI battery", "Pixel battery", "Nothing glyph dots", "Pixel charge ring")) and "selectedShape().coerceIn(0, 7)" in service,
    "app status is inside diagnostics only": 'if (cat == "setup") col.addView(status)' not in main and "val diag = card(\"App status and diagnostics\")" in main and "diag.addView(status)" in main,
    "One UI-inspired home typography and cards": "textSize = 26f" in main and "cornerRadius = dp(24).toFloat()" in main,
    "settings sections have redesigned hierarchy and descriptions": "private fun sectionDescription(title: String)" in main and "text = sectionDescription(title)" in main and "textSize = 15.5f" in main and "bottomMargin = dp(7)" in main,
    "settings redesign has per-section guidance": all(label in main for label in ("Service health, recent events, and troubleshooting details.", "Choose the appearance of the Capsule Lite settings screens.", "Use a translucent layered look with a safe fallback on unsupported devices.")),
    "settings header shows current redesign version": "SETTINGS  ·  PERSONALISE YOUR EXPERIENCE" in main and "V1.3.8" in main,
    "home tiles have accessible spoken descriptions": 'contentDescription = "$title. $subtitle"' in main and "isFocusable = true" in main,
    "home tile navigation arrows keep light-theme contrast": 'setTextColor(if (isLightAppTheme()) 0xFF64748B.toInt() else 0xFF8CA2B8.toInt())' in main,
    "slider accessibility announces its current value": 'contentDescription = "$label: $start"' in main and 'sb.contentDescription = "$label: $value"' in main,
    "editable fields follow selected app theme": main.count("setHintTextColor(secondaryTextColor())") >= 4 and main.count("setTextColor(primaryTextColor())") >= 4,
    "editable fields and charging symbol have explicit accessibility labels": all(label in main for label in ("Live-activity app allow-list, comma-separated package names", "Quick reply messages, comma-separated, up to three", "Pinned app package names, comma-separated, up to four", "Apps whose notifications should be hidden, comma-separated package names", "Choose charging icon design")),
    "package-name fields disable autocorrect suggestions": main.count("TYPE_TEXT_FLAG_NO_SUGGESTIONS") >= 3,
    "custom settings saves show confirmation": all(message in main for message in ("Live-activity app list saved", "Quick replies and pinned apps saved", "Hidden-app list saved")),
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

    "service status only appears in diagnostics": 'if (cat == "setup") col.addView(status)' not in main and "diag.addView(status)" in main,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
    if not ok:
        errors.append(f"Missing source check: {label}")
print(f"XML files parsed: {len(xml_files)}")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Static source validation passed (does not prove Android compilation).")

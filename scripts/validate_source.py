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
checks = {
    "versionCode 41": bool(re.search(r"versionCode\s*=\s*42\b", build)),
    "versionName 1.3.3": 'versionName = "1.3.4"' in build,
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
    "CI artifact v41": "capsule-lite-v42-debug-apk" in workflow,
    "hybrid charging preference": 'getBoolean("separateChargePill", true)' in service,
    "charging handoff considers active activities": "callActive || showingNotif || mapsShown()" in service,
    "compact Maps suppresses inline charging": "!mapsShown() && controller == null && charging && showCharging()" in service,
    "collapsed Maps pill prioritises trip distance left": "private fun mapsMiniText(): String = MapsNav.distanceLeft.ifEmpty { MapsNav.turnDistance }" in service,
    "Maps navigation pill enabled by default": 'toggle(shown, "Navigation info in the pill (reads Maps\' navigation notification, and the Maps screen while it is open)", "mapsInfo", true)' in main and 'getBoolean("mapsInfo", true)' in service and 'getBoolean("mapsInfo", false)' not in service,
    "secondary charging pill screen clamp": "screenH - dp(54)" in service and "secondaryChargeY()" in service,
    "settings switches have readable touch targets": "minHeight = dp(52)" in main and "setTextColor(primaryTextColor())" in main,
    "diagnostic and save status text follows app theme": "repairText = TextView(this).apply { textSize = 13f; setTextColor(secondaryTextColor()) }" in main and "saveMsg = TextView(this).apply { textSize = 13f; setTextColor(primaryTextColor()) }" in main,
    "charging gesture copy matches independent pill": "Expand/collapse charging pill" in main and "Expand/collapse main pill" not in main,
    "frosted glass avoids whole-window blur": "flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND" not in service and "translucent, bordered surface" in service,
    "separate charging pill has independent gesture settings": all(key in main for key in ("chargePillTap", "chargePillDoubleTap", "chargePillLongPress")),
    "charging pill gestures are wired": all(key in service for key in ("performChargePillGesture", "onSingleTapConfirmed", "onDoubleTap", "onLongPress")),
    "distinct charging icon shapes are selectable": "chargeIconShape" in main and "selectedShape()" in service,
    "separate charging pill has independent size controls": all(key in main for key in ("chargeMiniW", "chargeMiniH", "chargeMiniYOffset")),
    "app status is inside diagnostics only": 'if (cat == "setup") col.addView(status)' not in main and "val diag = card(\"App status and diagnostics\")" in main and "diag.addView(status)" in main,
    "charging pill is positioned beside the main pill": "private fun secondaryChargeX(chargeWidth: Int)" in service and "dp(8) + chargeWidth / 2" in service,
    "charging pill has independent width and height": 'getInt("chargeMiniW", 100)' in service and 'getInt("chargeMiniH", 42)' in service,
    "charging pill has independent vertical offset": 'getInt("chargeMiniYOffset", 0)' in service,
    "charging icon supports distinct symbols": 'getInt("chargeIconShape", 0)' in service and "Lightning bolt" in main and "Power plug" in main,
    "secondary pill has independent morph entrance": "OvershootInterpolator(0.8f)" in service and "animate().alpha(1f).scaleX(1f).scaleY(1f)" in service,
    "secondary pill has independent morph exit": "AccelerateInterpolator()" in service and "oldContent.animate()" in service and "scaleX(0.78f).scaleY(0.78f)" in service,
    "One UI-inspired home typography and cards": "textSize = 26f" in main and "cornerRadius = dp(24).toFloat()" in main,
    "settings sections have redesigned hierarchy and descriptions": "private fun sectionDescription(title: String)" in main and "text = sectionDescription(title)" in main and "textSize = 15.5f" in main and "bottomMargin = dp(7)" in main,
    "settings redesign has per-section guidance": all(label in main for label in ("Service health, recent events, and troubleshooting details.", "Choose the appearance of the Capsule Lite settings screens.", "Use a translucent layered look with a safe fallback on unsupported devices.")),
    "settings header shows current redesign version": "SETTINGS  ·  PERSONALISE YOUR EXPERIENCE" in main and "V1.3.4" in main,
    "home tiles have accessible spoken descriptions": 'contentDescription = "$title. $subtitle"' in main and "isFocusable = true" in main,
    "home tile navigation arrows keep light-theme contrast": 'setTextColor(if (isLightAppTheme()) 0xFF64748B.toInt() else 0xFF8CA2B8.toInt())' in main,
    "slider accessibility announces its current value": 'contentDescription = "$label: $start"' in main and 'sb.contentDescription = "$label: $value"' in main,
    "editable fields follow selected app theme": main.count("setHintTextColor(secondaryTextColor())") >= 4 and main.count("setTextColor(primaryTextColor())") >= 4,
    "editable fields and charging symbol have explicit accessibility labels": all(label in main for label in ("Live-activity app allow-list, comma-separated package names", "Quick reply messages, comma-separated, up to three", "Pinned app package names, comma-separated, up to four", "Apps whose notifications should be hidden, comma-separated package names", "Choose charging symbol shape")),
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
    "secondary charging pill expands independently": "chargePillExpanded" in service and "chargeEta" in service and "chargePillExpanded = !chargePillExpanded" in service,
    "secondary charging pill resets expansion when hidden": "if (!shouldFloat) {" in service and "chargePillExpanded = false" in service,
    "secondary charging pill expansion has liquid morph": "OvershootInterpolator(0.75f)" in service and "content.scaleX = 0.88f" in service,
    "expanded charging pill lays out details across expanded surface": "FrameLayout.LayoutParams.MATCH_PARENT" in service and "if (chargePillExpanded) 1f else 0f" in service,
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

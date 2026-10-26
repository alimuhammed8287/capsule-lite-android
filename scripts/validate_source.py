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
    "versionCode 38": bool(re.search(r"versionCode\s*=\s*38\b", build)),
    "versionName 1.3.0": 'versionName = "1.3.0"' in build,
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
    "CI artifact v38": "capsule-lite-v38-debug-apk" in workflow,
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

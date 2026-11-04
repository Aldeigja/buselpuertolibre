#!/bin/zsh
# Install the debug APK on a phone that already has USB debugging allowed.
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
apk="$root/app/build/outputs/apk/debug/app-debug.apk"

sdk=""
if [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  sdk="$ANDROID_HOME"
elif [[ -f "$root/local.properties" ]]; then
  sdk="$(grep '^sdk.dir=' "$root/local.properties" | head -1 | cut -d= -f2-)"
fi

if [[ -z "$sdk" || ! -x "$sdk/platform-tools/adb" ]]; then
  echo "adb was not found. Install Android SDK platform-tools and set ANDROID_HOME." >&2
  exit 1
fi

adb="$sdk/platform-tools/adb"

if [[ ! -f "$apk" ]]; then
  echo "Debug APK is missing. From buselpuertolibre run: ./gradlew assembleDebug" >&2
  exit 1
fi

# A stale adb server often reports an empty list until it is restarted.
"$adb" kill-server >/dev/null 2>&1 || true
"$adb" start-server >/dev/null

devices=""
unauthorized=""
for _ in 1 2 3 4 5 6 7 8; do
  devices="$("$adb" devices | awk 'NR>1 && $2=="device" {print $1}')"
  unauthorized="$("$adb" devices | awk 'NR>1 && $2=="unauthorized" {print $1}')"
  if [[ -n "$devices" || -n "$unauthorized" ]]; then
    break
  fi
  sleep 1
done

if [[ -n "$unauthorized" && -z "$devices" ]]; then
  echo "The Xiaomi is plugged in and waiting for permission." >&2
  echo "Unlock the phone. Tap Allow on Allow USB debugging, and tick Always allow from this computer." >&2
  echo "If no dialog is showing, unplug the cable, plug it back in, and watch the screen." >&2
  exit 1
fi

if [[ -z "$devices" ]]; then
  cat >&2 <<'EOF'
adb still does not see the phone. Developer options on the Xiaomi are not enough if the cable only charges.

1. Unlock the phone.
2. Pull down the notification shade and set USB to File transfer.
3. In Developer options, open the second Install via USB row (the one with an arrow) and confirm it.
4. Unplug the cable and plug it into another USB port, then run this script again.
EOF
  exit 1
fi

"$adb" install -r "$apk"
# Room copies the snapshot on first launch and keeps that copy across installs.
"$adb" shell pm clear es.elpuertobus.app >/dev/null
echo "Installed. Open Bus El Puerto Libre on the phone."

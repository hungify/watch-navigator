#!/usr/bin/env bash
# ==============================================================================
# scripts/check-device.sh
# Automated ADB device pre-flight diagnostics & connection health-check.
#
# Exit codes:
#   0 - At least one Android device is connected and ONLINE (ready for install).
#   1 - No devices connected, or all devices are unauthorized/offline.
#   2 - ADB binary could not be found.
# ==============================================================================

set -eo pipefail

# ANSI color codes (disabled if stdout is not a terminal or NO_COLOR is set)
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  BOLD="\033[1m"
  GREEN="\033[0;32m"
  YELLOW="\033[0;33m"
  RED="\033[0;31m"
  BLUE="\033[0;34m"
  CYAN="\033[0;36m"
  RESET="\033[0m"
else
  BOLD=""
  GREEN=""
  YELLOW=""
  RED=""
  BLUE=""
  CYAN=""
  RESET=""
fi

QUIET_MODE=0
STRICT_MODE=0
PRINT_ADB_PATH=0

# Parse CLI flags
for arg in "$@"; do
  case "$arg" in
    --quiet|-q)
      QUIET_MODE=1
      ;;
    --strict|--preflight)
      STRICT_MODE=1
      ;;
    --print-adb-path)
      PRINT_ADB_PATH=1
      ;;
    --help|-h)
      echo "Usage: $0 [--strict|--preflight] [--quiet|-q] [--print-adb-path]"
      echo ""
      echo "Options:"
      echo "  --strict, --preflight  Require at least 1 online device (exit 1 otherwise)"
      echo "  --quiet, -q            Suppress output; only return exit status"
      echo "  --print-adb-path       Print resolved ADB binary path to stdout and exit"
      echo "  --help, -h             Show this help reference"
      exit 0
      ;;
  esac
done

# ------------------------------------------------------------------------------
# 1. Locate ADB binary
# ------------------------------------------------------------------------------
find_adb() {
  if [ -n "${ADB_BIN:-}" ] && command -v "$ADB_BIN" >/dev/null 2>&1; then
    echo "$ADB_BIN"
    return 0
  fi

  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  local sdk_candidates=(
    "${ANDROID_HOME:-}/platform-tools/adb"
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb"
    "$HOME/Library/Android/sdk/platform-tools/adb"
    "$HOME/Android/Sdk/platform-tools/adb"
    "/opt/android-sdk/platform-tools/adb"
    "/usr/local/share/android-sdk/platform-tools/adb"
  )

  for candidate in "${sdk_candidates[@]}"; do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done

  return 1
}

ADB_PATH="$(find_adb || true)"

if [ -z "$ADB_PATH" ]; then
  if [ "$QUIET_MODE" -eq 0 ]; then
    echo -e "${RED}${BOLD}[ERROR] ADB (Android Debug Bridge) not found!${RESET}"
    echo ""
    echo -e "${BOLD}How to install / configure ADB:${RESET}"
    echo "  - macOS (Homebrew):   brew install android-platform-tools"
    echo "  - Ubuntu/Debian:      sudo apt-get install adb"
    echo "  - Fedora:             sudo dnf install android-tools"
    echo "  - Android Studio:     Export path: export ANDROID_HOME=\$HOME/Library/Android/sdk"
    echo "                        export PATH=\$PATH:\$ANDROID_HOME/platform-tools"
    echo ""
  fi
  exit 2
fi

if [ "$PRINT_ADB_PATH" -eq 1 ]; then
  echo "$ADB_PATH"
  exit 0
fi

# ------------------------------------------------------------------------------
# 2. Query ADB devices
# ------------------------------------------------------------------------------
if ! RAW_DEVICES_OUTPUT="$("$ADB_PATH" devices -l 2>&1)"; then
  if [ "$QUIET_MODE" -eq 0 ]; then
    echo -e "${RED}${BOLD}[ERROR] ADB device query failed.${RESET}"
    printf '%s\n' "$RAW_DEVICES_OUTPUT"
  fi
  exit 1
fi

ONLINE_COUNT=0
UNAUTHORIZED_COUNT=0
OFFLINE_COUNT=0
NO_PERM_COUNT=0
TOTAL_COUNT=0

ONLINE_DEVICES=()
UNAUTHORIZED_DEVICES=()
OFFLINE_DEVICES=()

# Process line by line
while IFS= read -r line; do
  # Trim line
  line="$(echo "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

  # Skip empty lines or header/daemon messages
  if [ -z "$line" ] || [[ "$line" == List\ of\ devices* ]] || [[ "$line" == \*daemon* ]]; then
    continue
  fi

  read -r SERIAL STATE DETAILS <<< "$line"

  case "$STATE" in
    device)
      ONLINE_COUNT=$((ONLINE_COUNT + 1))
      ONLINE_DEVICES+=("$SERIAL ($DETAILS)")
      ;;
    unauthorized)
      UNAUTHORIZED_COUNT=$((UNAUTHORIZED_COUNT + 1))
      UNAUTHORIZED_DEVICES+=("$SERIAL ($DETAILS)")
      ;;
    offline)
      OFFLINE_COUNT=$((OFFLINE_COUNT + 1))
      OFFLINE_DEVICES+=("$SERIAL ($DETAILS)")
      ;;
    no)
      # Usually 'no permissions'
      NO_PERM_COUNT=$((NO_PERM_COUNT + 1))
      ;;
    *)
      OFFLINE_COUNT=$((OFFLINE_COUNT + 1))
      OFFLINE_DEVICES+=("$SERIAL [$STATE] ($DETAILS)")
      ;;
  esac
  TOTAL_COUNT=$((TOTAL_COUNT + 1))
done <<< "$RAW_DEVICES_OUTPUT"

# ------------------------------------------------------------------------------
# 3. Output Diagnostics & Guidance
# ------------------------------------------------------------------------------
if [ "$QUIET_MODE" -eq 0 ]; then
  echo -e "${CYAN}${BOLD}=== Android ADB Device Health-Check ===${RESET}"
  echo -e "ADB binary: ${BLUE}$ADB_PATH${RESET}"
  echo ""

  if [ "$TOTAL_COUNT" -eq 0 ]; then
    echo -e "${RED}${BOLD}[WARN] No Android devices detected via ADB.${RESET}"
    echo ""
    echo -e "${BOLD}Step-by-Step Troubleshooting Checklist:${RESET}"
    echo "  1. Connect your Android phone to this computer via a working USB data cable."
    echo "  2. On your phone, change USB mode from 'Charging only' to 'File Transfer' (MTP)."
    echo "  3. Enable Developer Options:"
    echo "     - Settings > About phone > tap 'Build number' 7 times until unlocked."
    echo "  4. Enable USB Debugging:"
    echo "     - Settings > System / Developer options > toggle 'USB debugging' to ON."
    echo "  5. If prompt appears: check 'Always allow from this computer' and tap 'Allow'."
    echo "  6. If using an emulator: launch an Android Virtual Device from Android Studio."
    echo "  7. Restart ADB daemon if stuck: adb kill-server && adb start-server"
    echo ""
  else
    if [ "$ONLINE_COUNT" -gt 0 ]; then
      echo -e "${GREEN}${BOLD}[OK] Ready / Online Devices ($ONLINE_COUNT):${RESET}"
      for dev in "${ONLINE_DEVICES[@]}"; do
        echo -e "  - ${GREEN}$dev${RESET}"
      done
      echo ""
    fi

    if [ "$UNAUTHORIZED_COUNT" -gt 0 ]; then
      echo -e "${YELLOW}${BOLD}[WARN] Unauthorized Devices ($UNAUTHORIZED_COUNT):${RESET}"
      for dev in "${UNAUTHORIZED_DEVICES[@]}"; do
        echo -e "  - ${YELLOW}$dev${RESET}"
      done
      echo ""
      echo -e "${BOLD}How to authorize USB Debugging on your phone:${RESET}"
      echo "  1. Unlock your phone screen."
      echo "  2. Look for the 'Allow USB debugging?' dialog prompt."
      echo "  3. Check 'Always allow from this computer' and tap 'Allow'."
      echo "  4. If prompt does not appear:"
      echo "     - In Developer Options, tap 'Revoke USB debugging authorizations'."
      echo "     - Unplug and replug the USB cable."
      echo "     - Run: adb kill-server && adb start-server"
      echo ""
    fi

    if [ "$OFFLINE_COUNT" -gt 0 ]; then
      echo -e "${RED}${BOLD}[WARN] Offline Devices ($OFFLINE_COUNT):${RESET}"
      for dev in "${OFFLINE_DEVICES[@]}"; do
        echo -e "  - ${RED}$dev${RESET}"
      done
      echo ""
      echo -e "${BOLD}How to resolve offline state:${RESET}"
      echo "  1. Unplug and replug the USB cable."
      echo "  2. Run: adb kill-server && adb start-server"
      echo "  3. Restart the Android device if the offline state persists."
      echo ""
    fi

    if [ "$NO_PERM_COUNT" -gt 0 ]; then
      echo -e "${RED}${BOLD}[WARN] Device detected without USB permissions (udev rules missing).${RESET}"
      echo "  - On Linux, configure udev rules for your Android device vendor."
      echo ""
    fi
  fi
fi

# ------------------------------------------------------------------------------
# 4. Return exit status
# ------------------------------------------------------------------------------
if [ "$ONLINE_COUNT" -gt 0 ]; then
  exit 0
else
  exit 1
fi

#!/usr/bin/env bash
# ==============================================================================
# scripts/mock-gps-replay.sh
# Automated mock GPS route replay simulation harness for desk testing.
#
# Simulates turn-by-turn navigation movement via ADB, allowing desk testing
# of distance countdown, turn guidance, vibration warnings, and off-route recalculation.
# ==============================================================================

set -eo pipefail

# ANSI color codes (disabled if stdout is not a terminal or NO_COLOR is set)
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  BOLD="\033[1m"
  GREEN="\033[0;32m"
  CYAN="\033[0;36m"
  YELLOW="\033[1;33m"
  RED="\033[0;31m"
  BLUE="\033[0;34m"
  MAGENTA="\033[0;35m"
  RESET="\033[0m"
else
  BOLD=""
  GREEN=""
  CYAN=""
  YELLOW=""
  RED=""
  BLUE=""
  MAGENTA=""
  RESET=""
fi

# ------------------------------------------------------------------------------
# Default Settings
# ------------------------------------------------------------------------------
DEFAULT_SPEED_KMH=40
DEFAULT_INTERVAL_SEC=1.0
DEFAULT_ROUTE="landmark-to-hoankiem"
SPEED_KMH="$DEFAULT_SPEED_KMH"
INTERVAL_SEC="$DEFAULT_INTERVAL_SEC"
ROUTE_NAME="$DEFAULT_ROUTE"
CUSTOM_FILE=""
LOOP_MODE=0
REVERSE_MODE=0
OFF_ROUTE_DEVIATION=0
DEVIATE_AT_PERCENT=50
DEVIATE_OFFSET_METERS=200
DRY_RUN=0
QUIET_MODE=0
TARGET_DEVICE=""
INJECTION_METHOD="auto"
ALTITUDE=15.0

# ------------------------------------------------------------------------------
# CLI Help Banner
# ------------------------------------------------------------------------------
show_help() {
  cat << EOF
${BOLD}Usage:${RESET}
  $0 [options]

${BOLD}Description:${RESET}
  Automated mock GPS route replay simulation harness for WatchNavigator desk testing.
  Feeds route coordinates to a connected Android emulator or physical device via ADB,
  enabling desk validation of navigation countdowns, haptics, and auto-recalculation.

${BOLD}Options:${RESET}
  --route <name>           Built-in route to replay (default: landmark-to-hoankiem)
                           Available: landmark-to-hoankiem, hanoi-short-loop, keangnam-perimeter
  --list-routes            List all available built-in simulation routes
  --file <path>            Load custom coordinate file (CSV/JSON/TXT with lat,lng pairs)
  --speed <km/h>           Simulated movement speed in km/h (default: 40)
  --interval <seconds>     Seconds between GPS location injections (default: 1.0)
  --deviate                Simulate off-route deviation to trigger automatic route recalculation
  --deviate-at <percent>   Route completion percentage where deviation starts (default: 50)
  --loop                   Continuously repeat the route after reaching destination
  --reverse                Replay the route in reverse direction (destination -> origin)
  --dry-run                Print coordinates & ADB commands without sending to device
  --device <serial>, -s    Target specific ADB device serial
  --method <method>        Injection method: 'auto', 'emu', 'provider', or 'broadcast' (default: auto)
  --quiet, -q              Suppress detailed per-point logs
  --help, -h               Show this help message

${BOLD}Examples:${RESET}
  # Desk test default Hanoi route at 40 km/h:
  $0

  # Simulate motorbike ride at 50 km/h with off-route recalculation test:
  $0 --speed 50 --deviate

  # Fast walking simulation on short loop:
  $0 --route hanoi-short-loop --speed 6 --interval 0.5

  # Dry run to inspect generated coordinate sequence:
  $0 --dry-run
EOF
}

# ------------------------------------------------------------------------------
# Built-in Sample Routes (Hanoi Coordinates)
# ------------------------------------------------------------------------------
# Route 1: Keangnam Landmark 72 to Hoan Kiem Lake (~8.5 km, realistic city path)
ROUTE_LANDMARK_HOANKIEM=(
  "21.0168,105.7838"  # Keangnam Landmark 72
  "21.0152,105.7865"  # Pham Hung / Duong Dinh Nghe
  "21.0118,105.7925"  # Tran Duy Hung entrance
  "21.0085,105.7985"  # Tran Duy Hung / Hoang Dao Thuy
  "21.0055,105.8045"  # Tran Duy Hung / Nguyen Chi Thanh
  "21.0125,105.8115"  # Nguyen Chi Thanh / Huynh Thuc Khang
  "21.0195,105.8185"  # Nguyen Chi Thanh / Chua Lang
  "21.0265,105.8245"  # Nguyen Chi Thanh / Lieu Giai / Kim Ma
  "21.0315,105.8315"  # Kim Ma / Giang Vo
  "21.0325,105.8395"  # Nguyen Thai Hoc / Cat Linh
  "21.0305,105.8455"  # Nguyen Thai Hoc / Le Duan
  "21.0275,105.8505"  # Trang Thi / Quan Su
  "21.0285,105.8542"  # Hoan Kiem Lake / Dinh Tien Hoang (Destination)
)

# Route 2: Short Testing Loop around Keangnam (~2.2 km)
ROUTE_SHORT_LOOP=(
  "21.0168,105.7838"  # Keangnam Landmark 72
  "21.0185,105.7865"  # Pham Hung North
  "21.0225,105.7845"  # Duong Dinh Nghe
  "21.0210,105.7805"  # Me Tri Ha
  "21.0168,105.7838"  # Return to Keangnam
)

# Route 3: Keangnam Perimeter Square (~1.0 km)
ROUTE_KEANGNAM_PERIMETER=(
  "21.0168,105.7838"
  "21.0180,105.7850"
  "21.0165,105.7865"
  "21.0150,105.7850"
  "21.0168,105.7838"
)

list_routes() {
  cat << EOF
${BOLD}Available Built-in Simulation Routes:${RESET}
  1. ${CYAN}landmark-to-hoankiem${RESET} (~8.5 km, 13 key waypoints, Keangnam 72 to Hoan Kiem Lake)
  2. ${CYAN}hanoi-short-loop${RESET}     (~2.2 km, 5 key waypoints, quick circular loop in Cau Giay)
  3. ${CYAN}keangnam-perimeter${RESET}   (~1.0 km, 5 key waypoints, perimeter square for rapid testing)
EOF
}

# ------------------------------------------------------------------------------
# Parse CLI Arguments
# ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --route)
      ROUTE_NAME="$2"
      shift 2
      ;;
    --list-routes)
      list_routes
      exit 0
      ;;
    --file)
      CUSTOM_FILE="$2"
      shift 2
      ;;
    --speed)
      SPEED_KMH="$2"
      shift 2
      ;;
    --interval)
      INTERVAL_SEC="$2"
      shift 2
      ;;
    --deviate|--off-route)
      OFF_ROUTE_DEVIATION=1
      shift 1
      ;;
    --deviate-at)
      DEVIATE_AT_PERCENT="$2"
      shift 2
      ;;
    --loop)
      LOOP_MODE=1
      shift 1
      ;;
    --reverse)
      REVERSE_MODE=1
      shift 1
      ;;
    --dry-run)
      DRY_RUN=1
      shift 1
      ;;
    --device|-s)
      TARGET_DEVICE="$2"
      shift 2
      ;;
    --method)
      INJECTION_METHOD="$2"
      shift 2
      ;;
    --quiet|-q)
      QUIET_MODE=1
      shift 1
      ;;
    --help|-h)
      show_help
      exit 0
      ;;
    *)
      echo -e "${RED}[ERROR] Unknown option: $1${RESET}" >&2
      show_help >&2
      exit 1
      ;;
  esac
done

# ------------------------------------------------------------------------------
# 1. Determine Coordinate Waypoints & Validate Route
# ------------------------------------------------------------------------------
RAW_WAYPOINTS=()

if [ -n "$CUSTOM_FILE" ]; then
  if [ ! -f "$CUSTOM_FILE" ]; then
    echo -e "${RED}[ERROR] Custom coordinate file not found: $CUSTOM_FILE${RESET}" >&2
    exit 1
  fi
  # Parse CSV / TXT / JSON lines
  PAT_CSV='^([0-9]+\.[0-9]+)[[:space:]]*,[[:space:]]*([0-9]+\.[0-9]+)'
  PAT_JSON_LAT_LNG='(lat|latitude)["'\'':[:space:]]*([0-9]+\.[0-9]+).+(lng|lon|longitude)["'\'':[:space:]]*([0-9]+\.[0-9]+)'
  PAT_JSON_LNG_LAT='(lng|lon|longitude)["'\'':[:space:]]*([0-9]+\.[0-9]+).+(lat|latitude)["'\'':[:space:]]*([0-9]+\.[0-9]+)'

  while IFS= read -r line; do
    # Strip comments and trim leading/trailing whitespace
    line="$(echo "$line" | sed -e 's/#.*//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' | tr -d '\r')"
    [ -z "$line" ] && continue
    # Extract lat,lng pattern from CSV / TXT
    if [[ "$line" =~ $PAT_CSV ]]; then
      RAW_WAYPOINTS+=("${BASH_REMATCH[1]},${BASH_REMATCH[2]}")
    # Extract lat,lng from JSON format: {"lat": 21.0, "lng": 105.8}
    elif [[ "$line" =~ $PAT_JSON_LAT_LNG ]]; then
      RAW_WAYPOINTS+=("${BASH_REMATCH[2]},${BASH_REMATCH[4]}")
    elif [[ "$line" =~ $PAT_JSON_LNG_LAT ]]; then
      RAW_WAYPOINTS+=("${BASH_REMATCH[4]},${BASH_REMATCH[2]}")
    fi
  done < "$CUSTOM_FILE"
else
  case "$ROUTE_NAME" in
    landmark-to-hoankiem|landmark|hoankiem)
      RAW_WAYPOINTS=("${ROUTE_LANDMARK_HOANKIEM[@]}")
      ;;
    hanoi-short-loop|loop|short)
      RAW_WAYPOINTS=("${ROUTE_SHORT_LOOP[@]}")
      ;;
    keangnam-perimeter|keangnam|perimeter)
      RAW_WAYPOINTS=("${ROUTE_KEANGNAM_PERIMETER[@]}")
      ;;
    *)
      echo -e "${RED}[ERROR] Unknown route name: $ROUTE_NAME${RESET}" >&2
      list_routes >&2
      exit 1
      ;;
  esac
fi

if [ "${#RAW_WAYPOINTS[@]}" -lt 2 ]; then
  echo -e "${RED}[ERROR] Route must contain at least 2 waypoints (found ${#RAW_WAYPOINTS[@]}).${RESET}" >&2
  exit 1
fi

# Reverse route if requested
if [ "$REVERSE_MODE" -eq 1 ]; then
  REVERSED_WAYPOINTS=()
  for (( i=${#RAW_WAYPOINTS[@]}-1; i>=0; i-- )); do
    REVERSED_WAYPOINTS+=("${RAW_WAYPOINTS[i]}")
  done
  RAW_WAYPOINTS=("${REVERSED_WAYPOINTS[@]}")
fi

# ------------------------------------------------------------------------------
# 2. Locate ADB Binary & Device Selection
# ------------------------------------------------------------------------------
find_adb() {
  if [ -n "${ADB_BIN:-}" ] && [ -x "$ADB_BIN" ]; then
    echo "$ADB_BIN"
    return 0
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi
  if [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
    echo "$ANDROID_HOME/platform-tools/adb"
    return 0
  fi
  if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
    echo "$ANDROID_SDK_ROOT/platform-tools/adb"
    return 0
  fi
  if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    echo "$HOME/Library/Android/sdk/platform-tools/adb"
    return 0
  fi
  return 1
}

ADB_PATH="$(find_adb || true)"

if [ "$DRY_RUN" -eq 0 ] && [ -z "$ADB_PATH" ]; then
  echo -e "${RED}[ERROR] ADB binary could not be found!${RESET}" >&2
  echo -e "Please ensure Android SDK platform-tools are installed and 'adb' is on your PATH." >&2
  exit 2
fi

detect_device() {
  if [ "$DRY_RUN" -eq 1 ]; then
    return 0
  fi

  local devices_output
  devices_output="$("$ADB_PATH" devices 2>/dev/null | grep -v 'List of devices' | grep -v '^$' || true)"

  if [ -z "$devices_output" ]; then
    echo -e "${RED}[ERROR] No Android devices or emulators connected via ADB.${RESET}" >&2
    echo -e "Run ${CYAN}make device${RESET} to check connection status and troubleshooting steps." >&2
    exit 1
  fi

  if [ -z "$TARGET_DEVICE" ]; then
    # Grab the first online device
    local first_online
    first_online="$(echo "$devices_output" | grep -w 'device' | head -n1 | awk '{print $1}' || true)"
    if [ -z "$first_online" ]; then
      echo -e "${RED}[ERROR] No online Android devices available.${RESET}" >&2
      echo "$devices_output" >&2
      exit 1
    fi
    TARGET_DEVICE="$first_online"
  fi
}

detect_device

# ------------------------------------------------------------------------------
# Math Helpers for Interpolation & Distance
# ------------------------------------------------------------------------------
calc_distance_meters() {
  local lat1="$1" lon1="$2" lat2="$3" lon2="$4"
  awk -v lat1="$lat1" -v lon1="$lon1" -v lat2="$lat2" -v lon2="$lon2" '
    BEGIN {
      deg2rad = 3.141592653589793 / 180.0;
      dlat = (lat2 - lat1) * deg2rad;
      dlon = (lon2 - lon1) * deg2rad;
      a = sin(dlat/2)^2 + cos(lat1 * deg2rad) * cos(lat2 * deg2rad) * sin(dlon/2)^2;
      c = 2 * atan2(sqrt(a), sqrt(1-a));
      meters = 6371000.0 * c;
      printf "%.2f", meters;
    }
  '
}

# ------------------------------------------------------------------------------
# Interpolate Waypoints into Smooth Sequence Based on Speed & Interval
# ------------------------------------------------------------------------------
STEP_DISTANCE_METERS="$(awk -v speed="$SPEED_KMH" -v interval="$INTERVAL_SEC" 'BEGIN { printf "%.2f", (speed / 3.6) * interval }')"

if (( $(awk -v step="$STEP_DISTANCE_METERS" 'BEGIN { print (step < 1.0) }') )); then
  STEP_DISTANCE_METERS="1.00"
fi

INTERPOLATED_POINTS=()
TOTAL_ROUTE_DISTANCE=0

for (( i=0; i<${#RAW_WAYPOINTS[@]}-1; i++ )); do
  IFS=',' read -r lat1 lon1 <<< "${RAW_WAYPOINTS[i]}"
  IFS=',' read -r lat2 lon2 <<< "${RAW_WAYPOINTS[i+1]}"

  segment_dist="$(calc_distance_meters "$lat1" "$lon1" "$lat2" "$lon2")"
  TOTAL_ROUTE_DISTANCE="$(awk -v tot="$TOTAL_ROUTE_DISTANCE" -v seg="$segment_dist" 'BEGIN { printf "%.2f", tot + seg }')"

  steps_in_segment="$(awk -v dist="$segment_dist" -v step="$STEP_DISTANCE_METERS" '
    BEGIN {
      num = int(dist / step);
      if (num < 1) num = 1;
      print num;
    }
  ')"

  for (( s=0; s<steps_in_segment; s++ )); do
    interp="$(awk -v lat1="$lat1" -v lon1="$lon1" -v lat2="$lat2" -v lon2="$lon2" -v s="$s" -v total="$steps_in_segment" '
      BEGIN {
        frac = s / total;
        cur_lat = lat1 + (lat2 - lat1) * frac;
        cur_lon = lon1 + (lon2 - lon1) * frac;
        printf "%.6f,%.6f", cur_lat, cur_lon;
      }
    ')"
    INTERPOLATED_POINTS+=("$interp")
  done
done

# Append final destination coordinate
INTERPOLATED_POINTS+=("${RAW_WAYPOINTS[${#RAW_WAYPOINTS[@]}-1]}")
TOTAL_POINTS="${#INTERPOLATED_POINTS[@]}"

# ------------------------------------------------------------------------------
# Inject Off-Route Deviation if Requested
# ------------------------------------------------------------------------------
if [ "$OFF_ROUTE_DEVIATION" -eq 1 ]; then
  DEVIATION_START_IDX=$(( (TOTAL_POINTS * DEVIATE_AT_PERCENT) / 100 ))
  DEVIATION_LENGTH=15 # Number of points to sustain deviation (~200-300m)

  DEVIATED_POINTS=()
  for (( i=0; i<TOTAL_POINTS; i++ )); do
    IFS=',' read -r cur_lat cur_lon <<< "${INTERPOLATED_POINTS[i]}"
    if (( i >= DEVIATION_START_IDX && i < DEVIATION_START_IDX + DEVIATION_LENGTH )); then
      # Shift latitude and longitude perpendicular to simulate off-route turning
      deviated_coord="$(awk -v lat="$cur_lat" -v lon="$cur_lon" -v offset="$DEVIATE_OFFSET_METERS" '
        BEGIN {
          lat_shift = (offset / 111000.0);
          lon_shift = (offset / (111000.0 * cos(lat * 3.14159265 / 180.0)));
          printf "%.6f,%.6f", lat + lat_shift, lon + lon_shift;
        }
      ')"
      DEVIATED_POINTS+=("$deviated_coord")
    else
      DEVIATED_POINTS+=("${INTERPOLATED_POINTS[i]}")
    fi
  done
  INTERPOLATED_POINTS=("${DEVIATED_POINTS[@]}")
fi

# ------------------------------------------------------------------------------
# Method Resolution & ADB Injection Logic
# ------------------------------------------------------------------------------
resolve_method() {
  if [ "$INJECTION_METHOD" != "auto" ]; then
    return 0
  fi
  if [[ "$TARGET_DEVICE" =~ ^emulator- ]]; then
    INJECTION_METHOD="emu"
  else
    INJECTION_METHOD="provider"
  fi
}

resolve_method

inject_location() {
  local lat="$1"
  local lon="$2"
  local alt="${3:-$ALTITUDE}"

  local dev_args=()
  if [ -n "$TARGET_DEVICE" ]; then
    dev_args=("-s" "$TARGET_DEVICE")
  fi

  if [ "$DRY_RUN" -eq 1 ]; then
    if [ "$QUIET_MODE" -eq 0 ]; then
      case "$INJECTION_METHOD" in
        emu)
          echo "adb ${dev_args[*]} emu geo fix $lon $lat $alt" | tr -s ' '
          ;;
        provider)
          echo "adb ${dev_args[*]} shell cmd location set-test-provider-location-and-send gps --location $lat,$lon" | tr -s ' '
          ;;
        broadcast)
          echo "adb ${dev_args[*]} shell am broadcast -a com.watchnavigator.MOCK_GPS --ef lat $lat --ef lng $lon" | tr -s ' '
          ;;
      esac
    fi
    return 0
  fi

  case "$INJECTION_METHOD" in
    emu)
      "$ADB_PATH" "${dev_args[@]}" emu geo fix "$lon" "$lat" "$alt" >/dev/null 2>&1 || true
      ;;
    provider)
      "$ADB_PATH" "${dev_args[@]}" shell cmd location set-test-provider-location-and-send gps --location "$lat,$lon" >/dev/null 2>&1 || \
      "$ADB_PATH" "${dev_args[@]}" emu geo fix "$lon" "$lat" "$alt" >/dev/null 2>&1 || true
      ;;
    broadcast)
      "$ADB_PATH" "${dev_args[@]}" shell am broadcast -a com.watchnavigator.MOCK_GPS --ef lat "$lat" --ef lng "$lon" >/dev/null 2>&1 || true
      ;;
  esac
}

# ------------------------------------------------------------------------------
# Print Run Header Summary
# ------------------------------------------------------------------------------
print_banner() {
  if [ "$QUIET_MODE" -eq 1 ]; then
    return 0
  fi
  echo -e "======================================================================"
  echo -e "      ${BOLD}WatchNavigator Mock GPS Route Replay Simulation Harness${RESET}       "
  echo -e "======================================================================"
  echo -e "  ${BOLD}Target Device:${RESET}    ${CYAN}${TARGET_DEVICE:-<Dry-Run Mode>}${RESET}"
  echo -e "  ${BOLD}Route Name:${RESET}       ${GREEN}${ROUTE_NAME}${RESET}"
  echo -e "  ${BOLD}Waypoints:${RESET}        ${BOLD}${#RAW_WAYPOINTS[@]}${RESET} key points (${BOLD}$TOTAL_POINTS${RESET} interpolated GPS fixes)"
  echo -e "  ${BOLD}Total Distance:${RESET}   ${BOLD}${TOTAL_ROUTE_DISTANCE} m${RESET} (~$(awk -v d="$TOTAL_ROUTE_DISTANCE" 'BEGIN { printf "%.2f", d/1000 }') km)"
  echo -e "  ${BOLD}Speed & Interval:${RESET} ${YELLOW}${SPEED_KMH} km/h${RESET} (${STEP_DISTANCE_METERS} m per ${INTERVAL_SEC}s tick)"
  echo -e "  ${BOLD}Injection Method:${RESET} ${BLUE}${INJECTION_METHOD}${RESET}"
  if [ "$OFF_ROUTE_DEVIATION" -eq 1 ]; then
    echo -e "  ${BOLD}Off-Route Test:${RESET}   ${RED}Enabled (deviates ~${DEVIATE_OFFSET_METERS}m at ${DEVIATE_AT_PERCENT}% mark)${RESET}"
  fi
  if [ "$LOOP_MODE" -eq 1 ]; then
    echo -e "  ${BOLD}Loop Mode:${RESET}        ${MAGENTA}Infinite Repeat (Ctrl+C to stop)${RESET}"
  fi
  if [ "$DRY_RUN" -eq 1 ]; then
    echo -e "  ${BOLD}Execution Mode:${RESET}   ${YELLOW}DRY-RUN (Simulating command execution)${RESET}"
  fi
  echo -e "======================================================================"
}

# ------------------------------------------------------------------------------
# Main Simulation Loop
# ------------------------------------------------------------------------------
RUNNING=1
trap 'echo -e "\n${YELLOW}[!] Simulation stopped by user.${RESET}"; exit 0' SIGINT SIGTERM

run_simulation() {
  local round=1
  while [ "$RUNNING" -eq 1 ]; do
    if [ "$QUIET_MODE" -eq 0 ]; then
      if [ "$LOOP_MODE" -eq 1 ]; then
        echo -e "\n${BOLD}--- Starting Route Replay (Round #$round) ---${RESET}"
      else
        echo -e "\n${BOLD}--- Starting Route Replay ---${RESET}"
      fi
    fi

    for (( idx=0; idx<TOTAL_POINTS; idx++ )); do
      IFS=',' read -r lat lon <<< "${INTERPOLATED_POINTS[idx]}"

      # Progress calculation
      pct="$(awk -v cur="$((idx + 1))" -v tot="$TOTAL_POINTS" 'BEGIN { printf "%d", (cur * 100) / tot }')"
      rem_pts=$(( TOTAL_POINTS - idx - 1 ))
      rem_dist="$(awk -v pts="$rem_pts" -v step="$STEP_DISTANCE_METERS" 'BEGIN { printf "%.0f", pts * step }')"

      # Check if this point is in deviation zone
      dev_tag=""
      if [ "$OFF_ROUTE_DEVIATION" -eq 1 ] && (( idx >= DEVIATION_START_IDX && idx < DEVIATION_START_IDX + DEVIATION_LENGTH )); then
        dev_tag=" ${RED}[OFF-ROUTE DEVIATION]${RESET}"
      fi

      if [ "$QUIET_MODE" -eq 0 ]; then
        printf "\r[%3d%%] Step %4d/%4d | Lat: %9.6f, Lng: %10.6f | %5dm rem%s" \
          "$pct" "$((idx + 1))" "$TOTAL_POINTS" "$lat" "$lon" "$rem_dist" "$dev_tag"
      fi

      inject_location "$lat" "$lon"

      if [ "$DRY_RUN" -eq 0 ]; then
        sleep "$INTERVAL_SEC"
      fi
    done

    if [ "$QUIET_MODE" -eq 0 ]; then
      echo -e "\n${GREEN}[OK] Destination reached! Completed ${TOTAL_ROUTE_DISTANCE} m route.${RESET}"
    fi

    if [ "$LOOP_MODE" -eq 0 ]; then
      break
    fi

    round=$(( round + 1 ))
    if [ "$DRY_RUN" -eq 0 ]; then
      sleep 2
    fi
  done
}

print_banner
run_simulation

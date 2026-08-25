#!/usr/bin/env bash
# ==============================================================================
# scripts/test-mock-gps-replay.sh
# Unit & integration test suite for scripts/mock-gps-replay.sh
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPLAY_SCRIPT="$SCRIPT_DIR/mock-gps-replay.sh"
TEMP_DIR="$(mktemp -d)"

trap 'rm -rf "$TEMP_DIR"' EXIT

PASSED=0
FAILED=0

assert_eq() {
  local expected="$1"
  local actual="$2"
  local description="$3"

  if [ "$expected" == "$actual" ]; then
    echo -e "  \033[0;32m[PASS]\033[0m $description"
    PASSED=$((PASSED + 1))
  else
    echo -e "  \033[0;31m[FAIL]\033[0m $description"
    echo -e "         Expected: '$expected'"
    echo -e "         Actual:   '$actual'"
    FAILED=$((FAILED + 1))
  fi
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local description="$3"

  if echo "$haystack" | grep -F -q -- "$needle"; then
    echo -e "  \033[0;32m[PASS]\033[0m $description"
    PASSED=$((PASSED + 1))
  else
    echo -e "  \033[0;31m[FAIL]\033[0m $description"
    echo -e "         Expected to contain: '$needle'"
    echo -e "         Output was: $haystack"
    FAILED=$((FAILED + 1))
  fi
}

echo "======================================================================"
echo "        Running Unit Tests for scripts/mock-gps-replay.sh             "
echo "======================================================================"

# ------------------------------------------------------------------------------
# Test 1: Help and List-Routes Flags
# ------------------------------------------------------------------------------
echo "Test Case 1: Help and List-Routes Flags"
set +e
HELP_OUT="$("$REPLAY_SCRIPT" --help)"
HELP_CODE=$?
LIST_OUT="$("$REPLAY_SCRIPT" --list-routes)"
LIST_CODE=$?
set -e

assert_eq "0" "$HELP_CODE" "--help exits with 0"
assert_contains "$HELP_OUT" "Usage:" "--help shows usage banner"
assert_contains "$HELP_OUT" "--deviate" "--help documents deviation option"

assert_eq "0" "$LIST_CODE" "--list-routes exits with 0"
assert_contains "$LIST_OUT" "landmark-to-hoankiem" "Lists default Landmark route"
assert_contains "$LIST_OUT" "hanoi-short-loop" "Lists short loop route"

# ------------------------------------------------------------------------------
# Test 2: Dry Run with Built-in Route
# ------------------------------------------------------------------------------
echo "Test Case 2: Dry Run with Built-in Route"
set +e
DRY_OUT="$("$REPLAY_SCRIPT" --route keangnam-perimeter --speed 40 --interval 1.0 --dry-run)"
DRY_CODE=$?
set -e

assert_eq "0" "$DRY_CODE" "Dry run exits with 0"
assert_contains "$DRY_OUT" "WatchNavigator Mock GPS Route Replay" "Header banner is printed"
assert_contains "$DRY_OUT" "keangnam-perimeter" "Identifies route name"
assert_contains "$DRY_OUT" "Destination reached!" "Reaches destination successfully"
assert_contains "$DRY_OUT" "21.0168" "Outputs valid latitude waypoint"
assert_contains "$DRY_OUT" "105.7838" "Outputs valid longitude waypoint"

# ------------------------------------------------------------------------------
# Test 3: Custom Coordinates CSV File
# ------------------------------------------------------------------------------
echo "Test Case 3: Custom Coordinates CSV File"
CUSTOM_CSV="$TEMP_DIR/custom_route.csv"
cat << 'EOF' > "$CUSTOM_CSV"
# Custom test route
21.0200,105.8000
21.0250,105.8050
21.0300,105.8100
EOF

set +e
CSV_OUT="$("$REPLAY_SCRIPT" --file "$CUSTOM_CSV" --speed 50 --interval 1.0 --dry-run)"
CSV_CODE=$?
set -e

assert_eq "0" "$CSV_CODE" "Custom CSV file replay succeeds"
assert_contains "$CSV_OUT" "3 key points" "Correctly parses 3 key points"
assert_contains "$CSV_OUT" "21.0200" "Contains starting coordinate"
assert_contains "$CSV_OUT" "21.0300" "Contains ending coordinate"

# ------------------------------------------------------------------------------
# Test 4: Custom Coordinates JSON File
# ------------------------------------------------------------------------------
echo "Test Case 4: Custom Coordinates JSON File"
CUSTOM_JSON="$TEMP_DIR/custom_route.json"
cat << 'EOF' > "$CUSTOM_JSON"
[
  {"lat": 21.0100, "lng": 105.7900},
  {"lat": 21.0150, "lng": 105.7950},
  {"lat": 21.0200, "lng": 105.8000}
]
EOF

set +e
JSON_OUT="$("$REPLAY_SCRIPT" --file "$CUSTOM_JSON" --speed 30 --interval 1.0 --dry-run)"
JSON_CODE=$?
set -e

assert_eq "0" "$JSON_CODE" "Custom JSON file replay succeeds"
assert_contains "$JSON_OUT" "3 key points" "Correctly parses JSON coordinates"
assert_contains "$JSON_OUT" "21.0100" "Contains starting JSON coordinate"

# ------------------------------------------------------------------------------
# Test 5: Off-Route Deviation Injection
# ------------------------------------------------------------------------------
echo "Test Case 5: Off-Route Deviation Injection"
set +e
DEV_OUT="$("$REPLAY_SCRIPT" --route keangnam-perimeter --speed 40 --interval 1.0 --deviate --deviate-at 50 --dry-run)"
DEV_CODE=$?
set -e

assert_eq "0" "$DEV_CODE" "Off-route deviation run succeeds"
assert_contains "$DEV_OUT" "Off-Route Test:" "Header shows deviation enabled"
assert_contains "$DEV_OUT" "OFF-ROUTE DEVIATION" "Step log marks deviation zone"

# ------------------------------------------------------------------------------
# Test 6: Reverse Route Option
# ------------------------------------------------------------------------------
echo "Test Case 6: Reverse Route Option"
set +e
REV_OUT="$("$REPLAY_SCRIPT" --file "$CUSTOM_CSV" --reverse --speed 50 --interval 1.0 --dry-run)"
REV_CODE=$?
set -e

assert_eq "0" "$REV_CODE" "Reverse route run succeeds"
assert_contains "$REV_OUT" "21.030000, Lng: 105.810000" "Starting point in reverse is original end point"

# ------------------------------------------------------------------------------
# Test 7: Emulator vs Provider Injection Method
# ------------------------------------------------------------------------------
echo "Test Case 7: Emulator vs Provider Injection Method"
set +e
EMU_OUT="$("$REPLAY_SCRIPT" --route keangnam-perimeter --device "emulator-5554" --method emu --dry-run)"
PROV_OUT="$("$REPLAY_SCRIPT" --route keangnam-perimeter --device "device_serial_123" --method provider --dry-run)"
set -e

assert_contains "$EMU_OUT" "adb -s emulator-5554 emu geo fix" "Formats emulator geo fix command"
assert_contains "$PROV_OUT" "adb -s device_serial_123 shell cmd location set-test-provider-location-and-send" "Formats Android test provider command"

# ------------------------------------------------------------------------------
# Test 8: Quiet Mode Output
# ------------------------------------------------------------------------------
echo "Test Case 8: Quiet Mode Output"
set +e
QUIET_OUT="$("$REPLAY_SCRIPT" --route keangnam-perimeter --quiet --dry-run)"
set -e

assert_eq "" "$QUIET_OUT" "Quiet dry-run emits no step logs or banner"

# ------------------------------------------------------------------------------
# Test 9: Error Handling - Unknown Route and Missing File
# ------------------------------------------------------------------------------
echo "Test Case 9: Error Handling"
set +e
ERR_ROUTE="$("$REPLAY_SCRIPT" --route non-existent-route 2>&1)"
ERR_ROUTE_CODE=$?
ERR_FILE="$("$REPLAY_SCRIPT" --file "$TEMP_DIR/missing.csv" 2>&1)"
ERR_FILE_CODE=$?
set -e

assert_eq "1" "$ERR_ROUTE_CODE" "Invalid route name returns exit code 1"
assert_contains "$ERR_ROUTE" "Unknown route name" "Shows unknown route error"
assert_eq "1" "$ERR_FILE_CODE" "Missing file returns exit code 1"
assert_contains "$ERR_FILE" "Custom coordinate file not found" "Shows missing file error"

# ------------------------------------------------------------------------------
# Test 10: Missing ADB Binary Error Code
# ------------------------------------------------------------------------------
echo "Test Case 10: Missing ADB Binary"
set +e
ADB_ERR_OUT="$(env -i PATH="/usr/bin:/bin" HOME="$TEMP_DIR" ADB_BIN="$TEMP_DIR/no_adb" "$REPLAY_SCRIPT" 2>&1)"
ADB_ERR_CODE=$?
set -e

assert_eq "2" "$ADB_ERR_CODE" "Missing ADB binary returns exit code 2"
assert_contains "$ADB_ERR_OUT" "ADB binary could not be found" "Shows ADB missing error banner"

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
echo ""
echo "======================================================================"
echo "Test Results: $PASSED passed, $FAILED failed"
echo "======================================================================"

if [ "$FAILED" -gt 0 ]; then
  exit 1
fi

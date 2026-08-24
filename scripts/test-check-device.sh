#!/usr/bin/env bash
# ==============================================================================
# scripts/test-check-device.sh
# Unit & integration test suite for scripts/check-device.sh
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK_DEVICE_SCRIPT="$SCRIPT_DIR/check-device.sh"
TEMP_DIR="$(mktemp -d)"

trap 'rm -rf "$TEMP_DIR"' EXIT

PASSED=0
FAILED=0

assert_eq() {
  local expected="$1"
  local actual="$2"
  local test_name="$3"

  if [ "$expected" = "$actual" ]; then
    echo "  [PASS] $test_name"
    PASSED=$((PASSED + 1))
  else
    echo "  [FAIL] $test_name"
    echo "     Expected: '$expected'"
    echo "     Actual:   '$actual'"
    FAILED=$((FAILED + 1))
  fi
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local test_name="$3"

  if echo "$haystack" | grep -Fq "$needle"; then
    echo "  [PASS] $test_name"
    PASSED=$((PASSED + 1))
  else
    echo "  [FAIL] $test_name"
    echo "     Substring '$needle' not found in output:"
    echo "$haystack"
    FAILED=$((FAILED + 1))
  fi
}

echo "======================================================================"
echo "          Running Unit Tests for scripts/check-device.sh              "
echo "======================================================================"

# ------------------------------------------------------------------------------
# Test 1: Single Online Device
# ------------------------------------------------------------------------------
echo "Test Case 1: Single Online Device"
MOCK_ADB_1="$TEMP_DIR/mock_adb_1.sh"
cat << 'EOF' > "$MOCK_ADB_1"
#!/usr/bin/env bash
echo "List of devices attached"
echo "emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1"
EOF
chmod +x "$MOCK_ADB_1"

set +e
OUT_1="$(ADB_BIN="$MOCK_ADB_1" "$CHECK_DEVICE_SCRIPT")"
CODE_1=$?
set -e

assert_eq "0" "$CODE_1" "Exits with 0 when device is online"
assert_contains "$OUT_1" "[OK] Ready / Online Devices (1)" "Lists online device header"
assert_contains "$OUT_1" "emulator-5554" "Includes serial in output"

# ------------------------------------------------------------------------------
# Test 2: Unauthorized Device
# ------------------------------------------------------------------------------
echo "Test Case 2: Unauthorized Device"
MOCK_ADB_2="$TEMP_DIR/mock_adb_2.sh"
cat << 'EOF' > "$MOCK_ADB_2"
#!/usr/bin/env bash
echo "List of devices attached"
echo "1234567890ABCDEF       unauthorized usb:1-1 transport_id:2"
EOF
chmod +x "$MOCK_ADB_2"

set +e
OUT_2="$(ADB_BIN="$MOCK_ADB_2" "$CHECK_DEVICE_SCRIPT")"
CODE_2=$?
set -e

assert_eq "1" "$CODE_2" "Exits with 1 when device is unauthorized"
assert_contains "$OUT_2" "[WARN] Unauthorized Devices (1)" "Lists unauthorized device header"
assert_contains "$OUT_2" "How to authorize USB Debugging on your phone:" "Prints authorization checklist"
assert_contains "$OUT_2" "1234567890ABCDEF" "Includes unauthorized device serial"

# ------------------------------------------------------------------------------
# Test 3: Offline Device
# ------------------------------------------------------------------------------
echo "Test Case 3: Offline Device"
MOCK_ADB_3="$TEMP_DIR/mock_adb_3.sh"
cat << 'EOF' > "$MOCK_ADB_3"
#!/usr/bin/env bash
echo "List of devices attached"
echo "9876543210FEDCBA       offline transport_id:3"
EOF
chmod +x "$MOCK_ADB_3"

set +e
OUT_3="$(ADB_BIN="$MOCK_ADB_3" "$CHECK_DEVICE_SCRIPT")"
CODE_3=$?
set -e

assert_eq "1" "$CODE_3" "Exits with 1 when device is offline"
assert_contains "$OUT_3" "[WARN] Offline Devices (1)" "Lists offline device header"
assert_contains "$OUT_3" "How to resolve offline state:" "Prints offline troubleshooting steps"

# ------------------------------------------------------------------------------
# Test 4: No Devices Connected
# ------------------------------------------------------------------------------
echo "Test Case 4: No Devices Connected"
MOCK_ADB_4="$TEMP_DIR/mock_adb_4.sh"
cat << 'EOF' > "$MOCK_ADB_4"
#!/usr/bin/env bash
echo "List of devices attached"
EOF
chmod +x "$MOCK_ADB_4"

set +e
OUT_4="$(ADB_BIN="$MOCK_ADB_4" "$CHECK_DEVICE_SCRIPT")"
CODE_4=$?
set -e

assert_eq "1" "$CODE_4" "Exits with 1 when no devices are connected"
assert_contains "$OUT_4" "[WARN] No Android devices detected via ADB." "Shows no devices warning"
assert_contains "$OUT_4" "Step-by-Step Troubleshooting Checklist:" "Shows full troubleshooting checklist"
assert_contains "$OUT_4" "Developer Options" "Mentions developer options in checklist"

# ------------------------------------------------------------------------------
# Test 5: Missing ADB Binary
# ------------------------------------------------------------------------------
echo "Test Case 5: Missing ADB Binary"
set +e
# Point to a non-existent binary and clear search paths
OUT_5="$(env -i PATH="/usr/bin:/bin" HOME="$TEMP_DIR" ADB_BIN="$TEMP_DIR/missing_adb" "$CHECK_DEVICE_SCRIPT" 2>&1)"
CODE_5=$?
set -e

assert_eq "2" "$CODE_5" "Exits with 2 when ADB binary is missing"
assert_contains "$OUT_5" "[ERROR] ADB (Android Debug Bridge) not found!" "Shows ADB missing warning"
assert_contains "$OUT_5" "brew install android-platform-tools" "Mentions Homebrew install command"

# ------------------------------------------------------------------------------
# Test 6: Quiet Mode
# ------------------------------------------------------------------------------
echo "Test Case 6: Quiet Mode"
set +e
OUT_6="$(ADB_BIN="$MOCK_ADB_1" "$CHECK_DEVICE_SCRIPT" --quiet)"
CODE_6=$?
set -e

assert_eq "0" "$CODE_6" "Quiet mode exits with 0 on online device"
assert_eq "" "$OUT_6" "Quiet mode emits no output"

set +e
OUT_6B="$(ADB_BIN="$MOCK_ADB_4" "$CHECK_DEVICE_SCRIPT" -q)"
CODE_6B=$?
set -e

assert_eq "1" "$CODE_6B" "Quiet mode exits with 1 when no device"
assert_eq "" "$OUT_6B" "Quiet mode emits no output on failure"

# ------------------------------------------------------------------------------
# Test 7: Mixed Devices (1 online, 1 unauthorized)
# ------------------------------------------------------------------------------
echo "Test Case 7: Mixed Devices"
MOCK_ADB_7="$TEMP_DIR/mock_adb_7.sh"
cat << 'EOF' > "$MOCK_ADB_7"
#!/usr/bin/env bash
echo "List of devices attached"
echo "online-phone           device product:pixel8 model:Pixel_8 device:shiba transport_id:10"
echo "unauthorized-phone     unauthorized transport_id:11"
EOF
chmod +x "$MOCK_ADB_7"

set +e
OUT_7="$(ADB_BIN="$MOCK_ADB_7" "$CHECK_DEVICE_SCRIPT")"
CODE_7=$?
set -e

assert_eq "0" "$CODE_7" "Exits with 0 because at least 1 device is online"
assert_contains "$OUT_7" "[OK] Ready / Online Devices (1)" "Shows 1 online device"
assert_contains "$OUT_7" "[WARN] Unauthorized Devices (1)" "Shows 1 unauthorized device"

# ------------------------------------------------------------------------------
# Test 8: Help Option
# ------------------------------------------------------------------------------
echo "Test Case 8: Help Option"
set +e
OUT_8="$("$CHECK_DEVICE_SCRIPT" --help)"
CODE_8=$?
set -e

assert_eq "0" "$CODE_8" "Help option exits with 0"
assert_contains "$OUT_8" "Usage:" "Shows usage banner"
# ------------------------------------------------------------------------------
# Test 9: Print ADB Path Option
# ------------------------------------------------------------------------------
echo "Test Case 9: Print ADB Path Option"
set +e
OUT_9="$(ADB_BIN="$MOCK_ADB_1" "$CHECK_DEVICE_SCRIPT" --print-adb-path)"
CODE_9=$?
set -e

assert_eq "0" "$CODE_9" "Print ADB path exits with 0"
assert_eq "$MOCK_ADB_1" "$OUT_9" "Emits exact ADB binary path"

# ------------------------------------------------------------------------------
# Test 10: ADB Command Failure
# ------------------------------------------------------------------------------
echo "Test Case 10: ADB Command Failure"
MOCK_ADB_10="$TEMP_DIR/mock_adb_10.sh"
cat << 'EOF' > "$MOCK_ADB_10"
#!/usr/bin/env bash
echo "error: cannot connect to daemon at tcp:5037: Connection refused" >&2
exit 1
EOF
chmod +x "$MOCK_ADB_10"

set +e
OUT_10="$(ADB_BIN="$MOCK_ADB_10" "$CHECK_DEVICE_SCRIPT" 2>&1)"
CODE_10=$?
set -e

assert_eq "1" "$CODE_10" "Exits with 1 on ADB command failure"
assert_contains "$OUT_10" "[ERROR] ADB device query failed." "Shows query failed error"
assert_contains "$OUT_10" "Connection refused" "Includes raw error message"

# ------------------------------------------------------------------------------
# Test 11: Tab-delimited ADB output
# ------------------------------------------------------------------------------
echo "Test Case 11: Tab-delimited ADB output"
MOCK_ADB_11="$TEMP_DIR/mock_adb_11.sh"
cat << 'EOF' > "$MOCK_ADB_11"
#!/usr/bin/env bash
printf "List of devices attached\n"
printf "tab_serial\tdevice\tproduct:tab_phone model:TabPhone device:tab_dev transport_id:5\n"
EOF
chmod +x "$MOCK_ADB_11"

set +e
OUT_11="$(ADB_BIN="$MOCK_ADB_11" "$CHECK_DEVICE_SCRIPT")"
CODE_11=$?
set -e

assert_eq "0" "$CODE_11" "Tab-delimited device parsed successfully"
assert_contains "$OUT_11" "tab_serial (product:tab_phone model:TabPhone device:tab_dev transport_id:5)" "Details parsed correctly without repeating serial"

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

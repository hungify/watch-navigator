#!/usr/bin/env bash
# ==============================================================================
# Unit Tests for scripts/pre-commit-check.sh
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> Running tests for scripts/pre-commit-check.sh..."

# Test 1: Clean repository passes check
echo "Test 1: Clean repository check"
bash "${REPO_ROOT}/scripts/pre-commit-check.sh" --ci
echo "PASS: Clean repository passes pre-commit checks."
# Test 2: Detect simulated API key leak via pre-commit-check.sh
echo "Test 2: Detecting simulated raw API key leak in repository"
TMP_FILE="${REPO_ROOT}/.test_dummy_leak_$(date +%s%N 2>/dev/null || echo $$).txt"
trap 'git rm -f --cached "${TMP_FILE}" >/dev/null 2>&1 || true; rm -f "${TMP_FILE}"' EXIT

# Construct key-shaped fixture dynamically without embedding a static secret in this test script
KEY_PREFIX="AIzaSy"
KEY_SUFFIX="A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6Q"
echo "MAPS_API_KEY=${KEY_PREFIX}${KEY_SUFFIX}" > "${TMP_FILE}"

# Stage the file so pre-commit-check.sh scans it
git add "${TMP_FILE}"

# pre-commit-check.sh should catch the staged leak and exit with non-zero
if bash "${REPO_ROOT}/scripts/pre-commit-check.sh" >/dev/null 2>&1; then
    echo "FAIL: scripts/pre-commit-check.sh failed to catch staged API key leak."
    exit 1
else
    echo "PASS: scripts/pre-commit-check.sh successfully caught staged API key leak."
fi

# Clean up temporary test file and index entry
git rm -f --cached "${TMP_FILE}" >/dev/null 2>&1 || true
rm -f "${TMP_FILE}"
trap - EXIT

echo "✅ All pre-commit check unit tests passed."
exit 0

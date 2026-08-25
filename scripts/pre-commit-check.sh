#!/usr/bin/env bash
# ==============================================================================
# WatchNavigator Pre-Commit & Secret Safeguards Check
# ==============================================================================
# Scans staged files (or the whole repository when invoked with --ci) for
# accidental secret leaks, forbidden private files, and format compliance.
# ==============================================================================

set -euo pipefail

CI_MODE=false
if [[ "${1:-}" == "--ci" ]]; then
    CI_MODE=true
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

echo "==> Running WatchNavigator Pre-Commit & Secret Safeguards..."

FAILURES=0

# 1. Check for committed sensitive/forbidden files
FORBIDDEN_FILES=(
    "phone-app/local.properties"
    "phone-app/keystore.properties"
    "server/.dev.vars"
    ".env"
    "phone-app/*.jks"
    "phone-app/*.keystore"
)

for pattern in "${FORBIDDEN_FILES[@]}"; do
    # Check if any forbidden file is tracked by git
    if git ls-files --error-unmatch "${pattern}" >/dev/null 2>&1; then
        echo "❌ [SECURITY ERROR] Forbidden secret file is tracked in git: ${pattern}"
        echo "   Please remove it from git tracking via: git rm --cached ${pattern}"
        FAILURES=$((FAILURES + 1))
    fi
done

# 2. Scan files for potential hardcoded live Google Maps API Keys
# Google Maps API keys typically match: AIzaSy[A-Za-z0-9_-]{33}
# We exclude template/mock/documentation occurrences.
scan_stream() {
    if [[ "${CI_MODE}" == "true" ]]; then
        git ls-files -z
    else
        git diff --cached --name-only --diff-filter=ACM -z 2>/dev/null || git ls-files -z
    fi
}

while IFS= read -r -d '' file; do
    if [[ ! -f "${file}" ]]; then
        continue
    fi

    # Skip templates and documentation
    if [[ "${file}" == *".template"* || "${file}" == *".example"* || "${file}" == *"README.md"* || "${file}" == *"docs/"* ]]; then
        continue
    fi

    # Check for live Google Maps API Key pattern
    if grep -En 'AIzaSy[A-Za-z0-9_-]{33}' "${file}" 2>/dev/null | grep -v 'placeholder\|mock\|example\|template' >/dev/null 2>&1; then
        echo "❌ [SECURITY ERROR] Potential raw Google Maps API Key detected in: ${file}"
        grep -En 'AIzaSy[A-Za-z0-9_-]{33}' "${file}" | sed -E 's/(AIzaSy)[A-Za-z0-9_-]{29}([A-Za-z0-9_-]{4})/\1[REDACTED]\2/g' || true
        echo "   Please move secrets to local.properties or environment variables."
        FAILURES=$((FAILURES + 1))
    fi

    # Check for private keys
    if grep -En 'BEGIN (RSA|OPENSSH|EC|DSA|PRIVATE) KEY' "${file}" 2>/dev/null >/dev/null 2>&1; then
        echo "❌ [SECURITY ERROR] Unencrypted Private Key detected in: ${file}"
        FAILURES=$((FAILURES + 1))
    fi
done < <(scan_stream)

if [[ ${FAILURES} -gt 0 ]]; then
    echo ""
    echo "❌ Pre-commit / Secret safeguard checks failed with ${FAILURES} violation(s)."
    exit 1
fi

echo "✅ All secret safeguard and pre-commit checks passed cleanly."
exit 0

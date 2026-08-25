# WatchNavigator Developer Tooling & Simulation Scripts

This directory contains automated developer CLI utilities and simulation harnesses for the WatchNavigator project.

---

## 1. Mock GPS Route Replay Simulation (`scripts/mock-gps-replay.sh`)

Automated turn-by-turn GPS movement simulation harness for desk testing. Injects location updates into a connected Android emulator or physical device via ADB, enabling desktop validation of navigation countdowns, haptic vibration warnings, street name transitions, and automatic off-route recalculation.

### Quick Start

```bash
# Replay default Hanoi route (Keangnam Landmark 72 -> Hoan Kiem Lake) at 40 km/h:
make phone-gps-mock

# Or run the script directly:
bash scripts/mock-gps-replay.sh
```

### Key Options

| Flag | Description | Default |
|------|-------------|---------|
| `--route <name>` | Select built-in route (`landmark-to-hoankiem`, `hanoi-short-loop`, `keangnam-perimeter`) | `landmark-to-hoankiem` |
| `--list-routes` | List all available built-in simulation routes | - |
| `--speed <km/h>` | Simulated movement speed in km/h | `40` |
| `--interval <sec>` | Seconds between GPS location updates | `1.0` |
| `--deviate` | Simulate an off-route deviation to trigger automatic route recalculation | `false` |
| `--deviate-at <%>` | Percentage of route completion where off-route deviation begins | `50` |
| `--loop` | Continuously repeat the route after reaching destination | `false` |
| `--reverse` | Replay the route in reverse direction (destination -> origin) | `false` |
| `--file <path>` | Load custom coordinate file (CSV, JSON, or TXT) | - |
| `--dry-run` | Print coordinates and ADB commands without sending to device | `false` |
| `--device <serial>` | Target specific ADB device serial | auto-detected |
| `--quiet, -q` | Suppress detailed per-step terminal logs | `false` |

### Examples

```bash
# Simulate a motorbike ride at 50 km/h with an automatic off-route recalculation test:
bash scripts/mock-gps-replay.sh --speed 50 --deviate

# Walking navigation test on a short circular loop in Cau Giay:
bash scripts/mock-gps-replay.sh --route hanoi-short-loop --speed 6 --interval 0.5

# Replay a custom coordinate file:
bash scripts/mock-gps-replay.sh --file my-route.csv --speed 30

# Inspect generated coordinate sequence in dry-run mode:
bash scripts/mock-gps-replay.sh --dry-run
```

---

## 2. ADB Device Pre-flight Diagnostics (`scripts/check-device.sh`)

Automated diagnostic tool that queries ADB for connected Android devices, checks authorization state, and provides step-by-step troubleshooting instructions if devices are missing, offline, or unauthorized.

### Quick Start

```bash
# Check connected Android devices:
make device

# Or run directly:
bash scripts/check-device.sh
```

### Exit Codes
- `0`: At least one Android device is connected and online (ready for deployment).
- `1`: No devices connected or device is unauthorized/offline.
- `2`: ADB binary could not be found.

---

## 3. Pre-commit Secret Safeguards (`scripts/pre-commit-check.sh`)

Local and CI safeguard script that scans staged or tracked files for sensitive credentials, Google Maps API key exposure (`AIzaSy...`), private keys, and forbidden environment files (`local.properties`, `.dev.vars`, `.env`).

### Quick Start

```bash
# Run pre-commit checks locally:
make pre-commit

# Install automated git pre-commit hook:
make hooks-install
```

---

## Automated Test Suites

All developer tooling scripts are covered by automated unit and integration tests:

```bash
# Run mock GPS replay tests:
bash scripts/test-mock-gps-replay.sh

# Run device diagnostic tests:
bash scripts/test-check-device.sh

# Run pre-commit safeguard tests:
bash scripts/test-pre-commit.sh
```

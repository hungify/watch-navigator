# Watch Navigator

Real-time turn-by-turn navigation for Huawei Watches (GT-series e.g. GT5) powered by Android and Google Maps Platform via Huawei Wear Engine.

## Monorepo Architecture

```text
watch-navigator/
├── phone-app/               # Android Application (Kotlin, Gradle, AGP 8.x)
│   ├── app/                 # Main phone app module
│   ├── gradle/              # Gradle wrapper and version catalog
│   ├── local.properties.template
│   └── keystore.properties.template
├── server/                  # Directions Proxy Edge Worker (Cloudflare Workers + Hono + Zod, TypeScript)
│   ├── src/                 # Hono app, routes, Zod schemas & proxy logic
│   ├── test/                # Automated test suite
│   ├── wrangler.toml        # Wrangler configuration
│   └── .dev.vars.example
├── watch-app/               # Huawei Lite Wearable Application (JS/HML)
│   └── entry/               # DevEco Studio Lite Wearable module
└── docs/                    # Architecture & PRD documentation
    ├── PRD.md
    └── agents/
```

## Developer Workflow & CLI (`Makefile`)

A root `Makefile` provides unified one-line commands across all subprojects:

```bash
make help          # Show scannable reference of all available developer targets

# Phone App (Android)
make device        # Pre-flight check for ADB connected device & troubleshooting
make phone-build   # Compile Android debug APK
make phone-install # Install debug APK to connected Android device
make phone-run     # Build, install, and launch MainActivity on connected device
make phone-test    # Run Phone App JVM unit test suite
make phone-lint    # Run Android Lint analysis on Phone App
make phone-logs    # Stream Logcat filtered for navigation & Wear Engine tags
make phone-clean   # Clean Phone App Gradle build outputs

# Watch App (Lite Wearable / GT5)
make watch-build   # Compile TypeScript to JS watch bundle
make watch-dev     # Compile TypeScript in watch mode (auto-rebuild)
make watch-test    # Run Watch App unit tests, linter, and validation
make watch-lint    # Run ESLint on Watch App TypeScript sources
make watch-format  # Format Watch App code using Prettier
make watch-validate# Validate config.json, permissions & icon assets
make watch-clean   # Clean compiled Watch App JS bundle

# Directions Proxy Server (Cloudflare Workers)
make server-dev    # Run local Cloudflare Worker development server
make server-test   # Run Server unit tests & typecheck
make server-deploy # Deploy Directions Proxy Worker to Cloudflare

# Monorepo / Combined
make build         # Build both phone APK and watch JS bundle
make test          # Run full test suite across all subprojects
make lint          # Run linters across phone and watch modules
make format        # Format codebase
make clean         # Clean all build outputs and caches across modules
```

## Phone App (`phone-app/`)

### Prerequisites
- JDK 17 (e.g. OpenJDK 17)
- Android SDK (API Level 35, Build Tools 35.0.0+)
- Android Studio Ladybug or later

### Setup & Configuration
1. Navigate to `phone-app/`:
   ```bash
   cd phone-app
   ```
2. Copy the local properties template and insert your Google Maps Platform API key:
   ```bash
   cp local.properties.template local.properties
   ```
   Edit `local.properties`:
   ```properties
   MAPS_API_KEY=your_actual_google_maps_api_key
   ```
3. (Optional) For release builds, copy `keystore.properties.template` to `keystore.properties` and fill in signing credentials.

### Building & Testing
- Build debug APK:
  ```bash
  ./gradlew assembleDebug
  ```
- Run unit tests:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## Directions Proxy Worker (`server/`)

A zero-cold-start, low-latency edge proxy built on **Cloudflare Workers**, **Hono**, and **Zod** (TypeScript) holding the Google Directions API key securely off client devices.

### Setup & Configuration
1. Navigate to `server/`:
   ```bash
   cd server
   ```
2. Install dependencies:
   ```bash
   pnpm install
   ```
3. Copy the development variables template:
   ```bash
   cp .dev.vars.example .dev.vars
   ```
   Configure `.dev.vars`:
   ```properties
   SERVER_AUTH_TOKEN=your_shared_secret_token
   GOOGLE_DIRECTIONS_API_KEY=your_google_directions_api_key
   ```

### Running & Testing
- Start local development worker:
  ```bash
  pnpm dev
  ```
  *(Runs locally on `http://localhost:8787`)*
- Run tests & typecheck:
  ```bash
  pnpm test
  pnpm run typecheck
  ```

### Deploying to Cloudflare ($0/month)
```bash
npx wrangler login
npx wrangler secret put GOOGLE_DIRECTIONS_API_KEY
npx wrangler secret put SERVER_AUTH_TOKEN
pnpm run deploy
```
Copy the resulting Worker URL into `phone-app/local.properties`:
```properties
NAV_SERVER_URL=https://watch-navigator-proxy.<your-subdomain>.workers.dev
NAV_SERVER_TOKEN=your_shared_secret_token
```

---

## Watch App (`watch-app/`)

### Prerequisites
- Huawei DevEco Studio (supporting Lite Wearable development)
- Huawei Developer Account (for personal signing cert)
- Huawei Health app installed on phone with paired Huawei Watch (e.g. GT5)

### Building
1. Open DevEco Studio and import the `watch-app/` folder.
2. Setup signing certificate via **File > Project Structure > Signing Configs**.
3. Build `.hap`: **Build > Build Hap(s) / APP(s) > Build Hap(s)**.
4. Sideload to watch via **DevEco Assistant**.

---

## Shared JSON Contract & Planned P2P Bridge

The communication contract between `phone-app` and `watch-app` is defined using a compact JSON format.

> [!NOTE]
> In Issue #1 (Monorepo skeleton), the shared contract, UI handlers, and lifecycle hooks are scaffolded. The active live runtime P2P bridge transmission (`P2pClient` communication between Android and Watch GT5) is planned and will be implemented in **Ticket #4: Wear Engine P2P communication bridge**.

```json
{
  "turn": "left",
  "distance_m": 150,
  "street": "Nguyen Trai"
}
```

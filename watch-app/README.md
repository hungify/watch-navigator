# Watch Navigator - Watch Application (Huawei Lite Wearable)

This project contains the Huawei Lite Wearable application for Huawei Watch GT-series (e.g. Huawei Watch GT5).

## Tech Stack & Architecture

- **Platform:** Huawei Lite Wearable (JS FA Model)
- **Target Device:** Huawei Watch GT5 / GT-series (466x466 / 454x454 circular display)
- **Language:** **TypeScript** (compiled via `tsc` to Lite Wearable JS bundle)
- **Toolchain:** Huawei DevEco Studio & Node.js / TypeScript / **pnpm**
- **Linting & Formatting:** ESLint (Flat Config v9/v10) + Prettier 3
- **Communication:** Consumes turn-by-turn navigation data formatted according to the shared JSON contract. (Live P2P bridge connection is scheduled for implementation in **Ticket #4**).

## Project Structure

```text
watch-app/
├── package.json                    # Scripts: build, typecheck, lint, format, test
├── pnpm-lock.yaml                  # pnpm lockfile
├── tsconfig.json                   # TypeScript configuration
├── eslint.config.js                # Modern ESLint Flat Config
├── .prettierrc                     # Prettier code style configuration
├── README.md
├── src/                            # TypeScript source files
│   ├── types.ts                    # Strongly-typed models & JSON contracts
│   ├── app.ts                      # Typed application lifecycle
│   └── pages/index/
│       └── index.ts                # Typed page handler & view logic
├── test/
│   └── types.test.ts               # Native Node unit tests
└── entry/                          # Huawei DevEco Studio Lite Wearable module
    └── src/
        └── main/
            ├── config.json         # Lite Wearable app & ability declarations
            ├── resources/          # String & media resources (icon.png)
            └── js/default/         # Compiled JS bundle + HML / CSS markup
                ├── pages/index/
                │   ├── index.hml   # Markup (Arrow, Distance, Street)
                │   └── index.css   # Styling for circular watch face
                └── ...
```

## Development & Build Commands

- **Install dependencies:**
  ```bash
  pnpm install
  ```
- **Build TypeScript to JS bundle:**
  ```bash
  pnpm build
  ```
- **Typecheck without emitting files:**
  ```bash
  pnpm typecheck
  ```
- **Lint code (ESLint Flat Config):**
  ```bash
  pnpm lint
  # or auto-fix:
  pnpm lint:fix
  ```
- **Format code (Prettier):**
  ```bash
  pnpm format
  # or check formatting:
  pnpm format:check
  ```
- **Watch mode (auto-recompile on save):**
  ```bash
  pnpm watch
  ```
- **Run full pipeline (typecheck + lint + format:check + unit tests + validation):**
  ```bash
  pnpm test
  ```

---

## JSON Message Contract

The watch app expects turn instructions from the Android phone app with the following shape:

```json
{
  "turn": "left",
  "distance_m": 150,
  "street": "Nguyen Trai"
}
```

Supported `turn` values:

- `straight` (↑)
- `left` / `turn-left` (←)
- `right` / `turn-right` (→)
- `slight-left` (↖)
- `slight-right` (↗)
- `uturn` (⮌)
- `arrive` (★)

## Building & Sideloading (DevEco Studio)

1. Open Huawei DevEco Studio.
2. Select **Open Project** and choose the `watch-app` folder (or workspace).
3. Ensure the project is recognized as a Lite Wearable project (`deviceType: ["liteWearable"]`).
4. Configure signing certificate under **File > Project Structure > Project > Signing Configs** with your Huawei Developer personal debug certificate.
5. Build the `.hap` package: **Build > Build Hap(s) / APP(s) > Build Hap(s)**.
6. Sideload the generated `.hap` to your Huawei Watch GT5 using **DevEco Assistant** or the Huawei Health developer beta app.

# PRD: Turn-by-Turn Navigation App for Huawei Watches

**Version:** 1.3 (Final — ready for `/to-tickets`, broadened to support multiple Huawei watch lines)
**Date:** August 22, 2026
**Project owner:** Individual (personal/hobby project)
**Status:** Final v1

---

## 1. Overview

Build a two-part system — an Android phone app plus a watch app — that gives the user real-time **turn-by-turn navigation directions** right on the face of a Huawei watch, while the phone stays in a pocket or bag. Route data comes from the Google Maps Platform and is transmitted to the watch via Huawei Wear Engine.

The primary target device is a **GT-series watch** (e.g. GT5), but the architecture and phone-side code should be designed so that support can extend to other Huawei watch lines (e.g. Watch 5 / Watch 4 Pro on HarmonyOS NEXT) without a rewrite — see Section 5.3 for how the watch-side split works.

## 2. Problem Statement

- When riding a motorbike or walking, constantly having to pull out the phone to check directions is distracting and inconvenient.
- Huawei watches have no native navigation app integrated with Google Maps (since Huawei watches cannot run Google Play Services).
- A self-built solution is needed that makes use of existing hardware (an Android phone and a Huawei watch) without buying additional devices.
- Huawei's watch lineup is split across two different platforms/toolchains (see Section 5.3), so the solution needs a design that doesn't lock the project to one watch model.

## 3. Goals

| #   | Goal                                                                                       | Priority    |
| --- | ------------------------------------------------------------------------------------------ | ----------- |
| G1  | Receive turn directions (left/right/straight) on the watch face, with vibration alerts     | Must-have   |
| G2  | Display remaining distance to the next turn                                                | Must-have   |
| G3  | Search for a destination by place name (via Places API)                                    | Should-have |
| G4  | Display the name of the street about to be turned onto                                     | Should-have |
| G5  | Operating cost = $0/month (within the free tier)                                           | Must-have   |
| G6  | No need to unlock/open the app on the phone while en route (runs in the background)        | Should-have |
| G7  | Support both travel modes — walking and motorbike/driving — from v1                        | Must-have   |
| G8  | Allow the user to configure the vibration-trigger distance threshold via a settings screen | Should-have |

### Out of Scope (v1)

- No visual map view on the watch face — Lite Wearable devices have no canvas map engine (HarmonyOS NEXT watches could technically support one in a future version, but it's out of scope for v1).
- No offline navigation support.
- No public release on AppGallery — sideload only, for personal use.
- No multi-user / multi-account support.
- No simultaneous multi-watch support in v1 (i.e. the phone app targets one paired watch at a time — see Section 5.3 for which single platform ships first).

## 4. Target User

A single user: the project owner — riding a motorbike/car in the Hanoi area, who needs hands-free navigation, and who may own or switch between different Huawei watch models over time.

## 5. System Architecture

```
[Google Maps Platform API]
         │  (Directions API / Places API)
         ▼
[Android Phone App – Kotlin]  ← FusedLocationProviderClient (GPS)
         │
         │  Wear Engine P2P (compact JSON — shared contract)
         ▼
[Watch App — one of two platform tracks, see 5.3]
         │
         ▼
   Displays arrow + meters + vibration
```

The phone app and the JSON message contract (Section 5.2) are **watch-platform-agnostic** by design — they don't change based on which Huawei watch is paired. Only the watch-app implementation (Section 5.3) differs, because Huawei's watch lineup is split across two separate platforms/toolchains.

### 5.1. Component A — Phone App (Android/Kotlin)

- Calls the **Directions API** to fetch the route (polyline + list of turn steps), passing `mode=driving` or `mode=walking` depending on the user's chosen travel mode.
- Calls the **Places API (Autocomplete)** to look up destinations by name.
- Uses `FusedLocationProviderClient` to get real-time GPS location, with a polling frequency that **adapts to the travel mode** (see Section 8 — Non-functional Requirements) — modeled on how popular navigation apps (Google Maps, Waze) operate: using `PRIORITY_HIGH_ACCURACY` during active navigation, with different frequencies for driving vs. walking.
- Calculates the current position against the already-loaded route itself (matching coordinates to each "step" in the Directions response), **without repeatedly calling the Directions API** — it only calls again when the user deviates from the route beyond a threshold (e.g. >50m).
- Sends a small data packet over Wear Engine whenever there's a meaningful change (X meters remaining to the next turn, a change in turn direction, etc.). This packet format is identical regardless of which watch platform is paired (see 5.2) — the phone app never needs to know or care which Huawei watch model it's talking to.
- Includes a simple **Settings** screen: choose the travel mode (driving/walking), adjust the vibration-trigger distance threshold (default 150m/50m, user-editable).

### 5.2. Component B — Bridge (Huawei Wear Engine)

- Requirement: the Huawei Health app must be installed, with the target watch paired through it.
- Dependency: `com.huawei.hms:wearengine`.
- Permissions required: `DEVICE_MANAGER`, `SENSOR`, via `HiWear.getAuthClient()`.
- Transport channel: `P2pClient`, JSON-format packets, for example:

```json
{ "turn": "left", "distance_m": 120, "street": "Nguyen Trai" }
```

- This JSON contract is the stable interface between phone-app and watch-app. It stays fixed across watch platforms — a Watch 5 build and a GT5 build both consume the same message shape. Any breaking change to this contract must follow the PR convention in Section 14.

### 5.3. Component C — Watch App (multi-platform)

Huawei's watch lineup currently splits into **two distinct platforms with different toolchains**, and a watch app must be built separately for each:

|                          | **Lite Wearable (GT-series)**                                                       | **HarmonyOS NEXT (Watch-series)**                                                 |
| ------------------------ | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Example devices          | Watch GT5, GT6, GT Runner, and other sports watches                                 | Watch 5, Watch 4 Pro, and other smart watches                                     |
| Language                 | JS + **HML** (HarmonyOS Markup Language) + CSS                                      | ArkTS                                                                             |
| Project template         | "Lite Empty Ability" in DevEco Studio                                               | Standard ArkTS ability, uses `module.json5`                                       |
| Build tool               | Gradle (Huawei's Lite Wearable plugin)                                              | Hvigor (Huawei's Node.js-based build tool)                                        |
| UI capability            | Basic components only (text, image, progress, list, etc.) — no canvas map engine    | Richer UI framework; still no full map rendering assumed for this project's scope |
| Output                   | `.hap`                                                                              | `.hap` / `.har` / `.hsp`                                                          |
| Wear Engine bridge       | JS file downloaded manually from Huawei's docs, placed under a `wearengine/` folder | Native ArkTS Wear Engine SDK integration                                          |
| Install for personal use | DevEco Assistant sideload                                                           | DevEco Assistant sideload / DevEco Studio direct deploy over Wi-Fi debugging      |

**v1 scope decision:** ship the **Lite Wearable track first**, targeting GT-series watches (starting with GT5), since that's the device on hand. The phone app, JSON contract, and PR/ticket structure are kept platform-neutral from the start specifically so that adding a second `watch-app-harmonyos-next/` module later (for Watch 5-class devices) is an additive change — a new watch-app target consuming the same contract — rather than a redesign of the phone app or the Wear Engine message format.

- Each watch-app module is custom-drawn UI showing: a large arrow (left/right/straight), remaining distance in meters, street name — using whatever native components that platform provides (Lite Wearable: `div`/`stack`/`list`, `text`, `image`, `progress`; HarmonyOS NEXT: ArkTS/ArkUI equivalents).
- Vibrates when a new turn step is approaching, using each platform's native vibration API.
- Makes no internet calls of its own on either platform — only receives data via Wear Engine P2P from the phone.
- Builds to a `.hap`, signed with a personal certificate via AppGallery Connect, installed via sideload (DevEco Assistant) for personal use — no public AppGallery publishing.

## 6. Main User Flow

1. The user opens the phone app → selects a travel mode (walking/motorbike-driving) → enters or picks a destination (Places Autocomplete).
2. The app calls the Directions API with the corresponding mode → receives the route + list of turn steps.
3. The app sends the first turn step to the watch via Wear Engine.
4. The user starts moving and puts the phone away.
5. The phone app tracks GPS in the background, calculating the remaining distance to the next turn.
6. When roughly 150m/50m remain to the turn → sends an update and triggers vibration on the watch.
7. The watch displays the arrow and distance, and vibrates as a reminder.
8. Steps 5–7 repeat until the destination is reached → an "arrived" signal is sent → the watch shows an end-of-trip notification.

## 7. Functional Requirements

| ID   | Requirement                     | Description                                                                                                                                                                                                   |
| ---- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR1  | Destination search              | Text input, place suggestions via Places Autocomplete                                                                                                                                                         |
| FR2  | Route calculation               | Call the Directions API, parse the list of "steps" (turn direction, distance, street name)                                                                                                                    |
| FR3  | Background location tracking    | Poll GPS at a frequency adapted to travel mode (see Section 8), including while the app runs in the background                                                                                                |
| FR4  | Remaining distance calculation  | Match the current position against the nearest step on the route                                                                                                                                              |
| FR5  | Send data to the watch          | Via Wear Engine P2P, lightweight JSON format                                                                                                                                                                  |
| FR6  | Display directions on the watch | Arrow, distance in meters, street name                                                                                                                                                                        |
| FR7  | Vibration alerts                | Vibrate when a new turn step appears or the turn is near, based on the configured distance threshold                                                                                                          |
| FR8  | Automatic route recalculation   | Call the Directions API again when the user deviates from the route beyond the threshold                                                                                                                      |
| FR9  | Trip-completion notification    | Display "arrived" on the watch                                                                                                                                                                                |
| FR10 | Travel mode selection           | Toggle driving/walking before starting a trip, affecting the mode passed to the Directions API and the GPS polling frequency                                                                                  |
| FR11 | Settings screen                 | Allows adjusting the vibration-trigger distance threshold (default 150m/50m), persisted between app sessions                                                                                                  |
| FR12 | Watch-platform independence     | Phone app and Wear Engine JSON contract must not contain GT5-specific or Lite-Wearable-specific assumptions, so a second watch-app target (HarmonyOS NEXT) can be added later without changing phone-app code |

## 8. Non-functional Requirements

| Category                                                                     | Requirement                                                                                                                                                                                                                                                                                                         |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Cost                                                                         | $0/month — within the Google Maps Platform free tier (10,000 requests per API per month); a billing alert is set up in case of bugs causing repeated API calls                                                                                                                                                      |
| Battery                                                                      | Optimize GPS/API polling frequency so the phone's battery doesn't drain too fast during long background sessions                                                                                                                                                                                                    |
| GPS frequency (finalized based on real-world practice from Google Maps/Waze) | **Driving**: `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY`, ~1-second interval during active navigation. **Walking**: 3–5 second interval (slower movement speed, no need for rapid updates) — accurate enough while saving battery. Uses `setSmallestDisplacement` to skip updates while stationary. |
| Latency                                                                      | From GPS update to watch display: under ~2 seconds                                                                                                                                                                                                                                                                  |
| Connection reliability                                                       | Handles temporary Bluetooth disconnects between phone and watch (retry, no crashes)                                                                                                                                                                                                                                 |
| Security                                                                     | Google Maps API key restricted by package name/SHA-1 cert, never hard-coded or exposed                                                                                                                                                                                                                              |

## 9. Dependencies & Technical Constraints

- A **Google Cloud Billing Account** is required (even to use the free tier) → a credit card must be entered.
- Lite Wearable devices (GT-series) have **no canvas map engine**, and can only render basic UI (text, lists, simple drawings), not detailed maps. This constraint may not fully apply to HarmonyOS NEXT devices, but detailed map rendering stays out of scope for this project regardless of platform (see Section 3).
- Huawei watches have no independent SIM/WiFi in most configurations → all internet requests must go through the phone.
- The Huawei Health app must be installed on the phone, with the target watch paired through it (a hard requirement of Wear Engine, regardless of platform).
- Watch apps can only be installed via personal sideload (DevEco Assistant, or DevEco Studio direct deploy) — not easily distributed to other people, on either platform.
- **Toolchain note:** the GT-series (Lite Wearable) track uses standard Gradle with Huawei's Lite Wearable plugin; a future HarmonyOS NEXT track would use Hvigor instead. These are separate `watch-app-*/` modules within the monorepo, not a shared build — confirm exact tooling against the generated project before scaffolding each, since Huawei's tooling has shifted over time.

## 10. Estimated Costs

| Item                                               | Cost                                                                 |
| -------------------------------------------------- | -------------------------------------------------------------------- |
| Google Maps Directions/Places API                  | $0 (within the free tier of 10,000 requests/month/API, personal use) |
| Huawei Developer account (personal)                | $0                                                                   |
| DevEco Studio, AppGallery Connect, Wear Engine SDK | $0                                                                   |
| **Total**                                          | **$0/month**, as long as the API isn't polled continuously           |

## 11. Risks & Mitigation

| Risk                                                                                    | Mitigation                                                                                                       |
| --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| A bug causes the app to call the Directions API in an infinite loop → incurring charges | Set a budget alert in Google Cloud Console (e.g. $5)                                                             |
| Wear Engine loses the P2P connection mid-trip                                           | Cache the most recent instruction on the watch, auto-retry the connection                                        |
| Poor GPS accuracy in dense high-rise areas (Hanoi)                                      | Apply a reasonable route-deviation threshold before triggering a recalculation, to avoid repeated recalculations |
| Android background execution restrictions (battery optimization)                        | Use a Foreground Service + Notification while navigation is active                                               |

## 12. Proposed Roadmap / Milestones

| Phase                 | Content                                                                                                                     |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| M1                    | Android app: call the Directions API (driving + walking), display a simple text route on the phone                          |
| M2                    | Wear Engine integration: successfully send a simple JSON message from the phone to the GT5 (test via notification)          |
| M3                    | Build the Lite Wearable app: display arrow + distance UI from data received via P2P                                         |
| M4                    | Assemble the full pipeline: real-time GPS (frequency by mode) → compute current step → continuously send to the watch       |
| M5                    | Add vibration, route recalculation, trip-completion notification                                                            |
| M6                    | Settings screen: choose travel mode, customize vibration thresholds                                                         |
| M7                    | Field testing (real walking + motorbike trips in Hanoi), tune distance/latency thresholds per mode                          |
| M8 (stretch, post-v1) | Add a second watch-app module for HarmonyOS NEXT (ArkTS/Hvigor), reusing the existing phone app and JSON contract unchanged |

## 13. Decisions Finalized (v1.1)

| Question                                        | Decision                                                                                                                |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| GPS frequency                                   | Adaptive by mode: driving ~1s (`PRIORITY_HIGH_ACCURACY`), walking 3–5s — matching how Google Maps/Waze actually operate |
| Support walking in v1?                          | Yes, from v1 (not deferred)                                                                                             |
| Should the vibration threshold be configurable? | Yes — a Settings screen was added (FR11), default 150m/50m                                                              |

No open questions remain — this PRD is ready to be handed off to `/to-tickets`.

## 14. Pull Request Conventions

Applies to every PR generated from `/implement`, shared by both `phone-app/` and `watch-app/`.

### Should be included in the PR description

| Item                                                                                 | Why                                                                                                                                                                                                      |
| ------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Link to the ticket (from `/to-tickets`)                                              | Lets the reviewer know which milestone/ticket the PR addresses, without guessing                                                                                                                         |
| 1–3 sentence summary: what and why                                                   | Don't repeat the ticket description — only note differences or new decisions made while coding                                                                                                           |
| How it was tested/verified                                                           | Most important for this project, since it's hard to automate testing against real hardware — be specific (e.g. "walked A→B in person, watch vibrated correctly at 50m") rather than just saying "tested" |
| Trade-offs intentionally accepted                                                    | e.g. "Bluetooth disconnects longer than 30s aren't handled yet; deferred to a later ticket" — prevents the reviewer from thinking something was missed                                                   |
| Breaking changes to the JSON contract (message format between phone-app ↔ watch-app) | Format changes affect both codebases in the monorepo and must be called out clearly so the right people update accordingly                                                                               |

### Should NOT be included in the PR description

- Copying the entire PRD/spec into the PR — just link to `docs/PRD_GT5_Navigation.md`, don't paste the whole thing.
- Line-by-line code explanations — the diff already shows "what," the description only needs to cover "why" at the decision level.
- API keys, `.hap` certificates, or signing-cert information — must never leak into a PR/commit.
- Future plans outside the scope of the ticket under review.
- A narration of the agent's thought process (if letting `/implement` auto-generate the description) — keep only the conclusions, strip out "I considered 3 approaches...".

### Standard Template

```markdown
## Ticket

Closes #<ticket number> (e.g. M2 — Wear Engine P2P integration)

## Summary

Connected P2pClient to send turn-instruction JSON from phone-app to watch-app.
Chose 1s polling for driving / 3-5s for walking per the finalized PRD.

## Tested

- Manual test: walked 200m in real conditions, confirmed JSON logs sent at the correct turn point.
- Not yet tested: route recalculation when GPS is lost among high-rise buildings (deferred to a later ticket).

## Notes for reviewer

Changed the JSON message field `distance_m` → `distanceMeters` to match
Kotlin conventions — watch-app (ticket #12) needs to be updated accordingly.
```

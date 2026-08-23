# Directions Proxy Server (Cloudflare Workers + Hono)

A high-performance, zero-cold-start Edge proxy for Google Directions API built on **Cloudflare Workers**, **Hono**, and **Zod** with TypeScript.

## Why Cloudflare Workers + Hono?
- **Ultra-low latency**: Runs on Cloudflare's edge network (edge locations in Vietnam / Hanoi ~5-10ms response).
- **Hono Framework**: Modern, lightweight (<15KB) web framework designed natively for Cloudflare Workers.
- **Zod Schema Validation**: Declarative, type-safe validation for query parameters and environment variables.
- **Zero Cold Starts**: Never sleeps, no 30-second wake-up lag like traditional free-tier container hosting.
- **Generous Free Tier**: 100,000 free requests per day ($0/month).
- **Secure**: Google Directions API key remains protected on Cloudflare Edge with secret management and fail-closed authentication.

## API Endpoints

### 1. Health Check
- **Endpoint**: `GET /health` (or `GET /`)
- **Response**:
  ```json
  {
    "status": "ok",
    "service": "watch-navigator-proxy",
    "framework": "hono",
    "runtime": "cloudflare-workers",
    "timestamp": "2026-08-23T04:00:00.000Z"
  }
  ```

### 2. Directions Proxy
- **Endpoint**: `GET /api/v1/directions` (or `GET /directions`)
- **Headers**:
  - `Authorization: Bearer <SERVER_AUTH_TOKEN>` (or `X-API-Key: <SERVER_AUTH_TOKEN>`)
- **Query Parameters**:
  - `origin`: Coordinates (`lat,lng` e.g. `21.0285,105.8542`)
  - `destination`: Coordinates or Place ID (`place_id:<PLACE_ID>`)
  - `mode`: Travel mode (`driving` | `walking` | `bicycling` | `transit`)
- **Response**: Proxied JSON payload from Google Maps Directions API.

---

## Local Development & Testing

1. **Install dependencies**:
   ```bash
   pnpm install
   ```

2. **Setup local variables**:
   ```bash
   cp .dev.vars.example .dev.vars
   ```
   Edit `.dev.vars`:
   ```properties
   SERVER_AUTH_TOKEN=your_secret_token
   GOOGLE_DIRECTIONS_API_KEY=your_google_directions_api_key
   ```

3. **Start local worker**:
   ```bash
   pnpm dev
   ```
   *Runs locally on `http://localhost:8787`*

4. **Run test suite & typecheck**:
   ```bash
   pnpm test
   ```

---

## Deploying to Cloudflare ($0/month Free Tier)

1. **Login to Cloudflare**:
   ```bash
   npx wrangler login
   ```

2. **Set your secrets**:
   ```bash
   npx wrangler secret put GOOGLE_DIRECTIONS_API_KEY
   npx wrangler secret put SERVER_AUTH_TOKEN
   ```

3. **Deploy worker**:
   ```bash
   pnpm run deploy
   ```
   *Wrangler will output your public worker URL, e.g., `https://watch-navigator-proxy.<your-subdomain>.workers.dev`*

4. **Connect to Phone App**:
   In `phone-app/local.properties`:
   ```properties
   NAV_SERVER_URL=https://watch-navigator-proxy.<your-subdomain>.workers.dev
   NAV_SERVER_TOKEN=your_secret_token
   ```

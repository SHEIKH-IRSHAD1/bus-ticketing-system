# Smart Bus Ticketing & Dispatch System

A decoupled, microservice-based system: passengers buy QR tickets, conductors validate them, and waiting passengers can request a bus stop that alerts the driver in real time when the bus gets close.

Java 21 / Spring Boot backend, web (React/Vite) clients, Postgres + Redis + MQTT. Every tool used is free.

```
 Passenger PWA   Kiosk    Conductor App          Driver Dashboard
      │            │            │                       ▲
      └────────────┴────────────┘                       │ MQTT over WebSocket (:9001)
                   │ REST                               │
            ┌──────▼───────┐                     ┌──────┴───────┐
            │ API Gateway  │ :8080               │  Mosquitto   │ :1883 / :9001
            └───┬──────┬───┘                     └──▲────────┬──┘
   /api/tickets │      │ /api/stops,routes          │ alerts │ bus/+/gps
        ┌───────▼─┐  ┌─▼─────────────┐              │        │
        │Ticketing│  │ Dispatch &    ├──────────────┘        │
        │ Service │  │ Location Eng. │◄──────────────────────┘
        │  :8082  │  │    :8081      │        ▲
        └────┬────┘  └───┬───────┬───┘        │ GPS pings
             │           │       │       GPS simulator / real buses
        ┌────▼───────────▼┐  ┌───▼────┐
        │   PostgreSQL    │  │ Redis  │  live positions + pending requests
        └─────────────────┘  └────────┘
```

## Repository layout

| Path | What it is |
|---|---|
| `infra/` | `docker-compose.yml` (Postgres, Redis, Mosquitto + the 3 services), Mosquitto config |
| `services/dispatch/` | GPS ingestion over MQTT, geofencing, stop requests (Spring Boot, port 8081) |
| `services/ticketing/` | Stripe payments, HMAC-signed QR tickets, validation (Spring Boot, port 8082) |
| `gateway/` | Spring Cloud Gateway: routing, rate limiting, CORS (port 8080) |
| `clients/passenger-pwa/` | Scan bus QR → pay → show ticket QR (installable PWA, :5173) |
| `clients/kiosk/` | Stop-request screen (:5174) |
| `clients/driver-dashboard/` | Live stop alerts over MQTT-WebSocket (:5175) |
| `clients/conductor-app/` | Scan + validate tickets (installable PWA, :5176) |
| `tools/gps-simulator/` | Node script that drives a fake bus around the seeded stops |
| `docs/asyncapi.yaml` | MQTT topic/payload contract |

## ⚠️ Verification status — please read

This was built in a sandbox that could not reach Maven Central or run Docker, so **not everything was executed**. Being specific:

**Verified (actually run):**
- All 4 frontends: `npm install` + `npm run build` succeed (PWA service workers generated for Passenger and Conductor).
- GPS simulator: published real messages through a live MQTT broker and they were received and parsed correctly.
- All YAML files parse; all 40 Java files are brace-balanced and every internal import resolves to a real file.

**NOT verified — you'll be the first to run these:**
- **Java has never been compiled or tested.** I reviewed it by hand and already caught and fixed several real bugs that way (a nonexistent `Metrics.METERS`, MQTT subscriptions lost on reconnect, missing CORS, Stripe deserialization fallback), but expect the odd compile error on first build. Run `mvn -B verify` inside each of `services/dispatch`, `services/ticketing`, `gateway` first; fix anything it reports before going further.
- **Pinned versions are unchecked:** Spring Boot `3.3.4`, Spring Cloud `2023.0.3`, `stripe-java 26.7.0`, Paho `1.2.5`. If Maven can't resolve one, bump it to the current release.
- `docker compose up`, the Stripe flow, and camera scanning in real browsers/devices.

## Prerequisites

Docker Desktop, Java 21 (Temurin), Maven 3.9+, Node 20+, and a free [Stripe](https://stripe.com) account (test mode) plus the [Stripe CLI](https://stripe.com/docs/stripe-cli).

## Quick start

### 1. Configure secrets
```bash
cd infra
cp .env.example .env      # then edit: add your Stripe test keys
```

### 2. Run the backend
```bash
cd infra
docker compose up --build          # everything: infra + 3 services
# or, to develop a service from your IDE, run just the infra:
docker compose up postgres redis mosquitto
```
Flyway creates the tables and seeds one route with four stops on first start.
Health check: `curl localhost:8080/actuator/health`, `curl localhost:8081/actuator/health`.

### 3. Forward Stripe webhooks to your machine
Stripe can't reach `localhost`, so the CLI tunnels events to the gateway:
```bash
stripe login
stripe listen --forward-to localhost:8080/api/webhooks/stripe
```
It prints a signing secret (`whsec_...`). Put it in `infra/.env` as `STRIPE_WEBHOOK_SECRET` and restart the `ticketing` container.

### 4. Run the clients (one terminal each)
```bash
cd clients/passenger-pwa    && npm install && npm run dev   # :5173
cd clients/kiosk            && npm install && npm run dev   # :5174
cd clients/driver-dashboard && npm install && npm run dev   # :5175
cd clients/conductor-app    && npm install && npm run dev   # :5176
```
(Each has a `.env.example`; the defaults work for local development.)

## End-to-end demo

1. **Driver Dashboard** (`localhost:5175`): keep Bus ID as `bus-1`. It should show "● Live".
2. **Kiosk** (`localhost:5174`): choose *You are at* **City Hall**, *Going to* **Museum Road**, press **Request pickup**.
3. **GPS simulator**:
   ```bash
   cd tools/gps-simulator && npm install && npm start
   ```
   The bus leaves Central Station toward City Hall. When it gets within 300 m (~10 pings, ~20 s), the dashboard shows **"Stop at City Hall"** — exactly once, even as the bus keeps approaching.
4. **Passenger PWA** (`localhost:5173`): it scans a QR containing just `1` (the route id) — generate one with any free QR generator and show it on another screen. Then **Pay now** and use Stripe test card `4242 4242 4242 4242` (any future expiry, any CVC). You return to a screen that flips from *Pending* to *Active* and shows your ticket QR.
5. **Conductor App** (`localhost:5176`): scan the ticket QR → green **Valid ticket**. Scan it again → red **"This ticket was already scanned."**

> Browsers only allow camera access on `localhost` or HTTPS. To test on a real phone over your network, serve the clients over HTTPS (e.g. a free `cloudflared` or `ngrok` tunnel).

## Run the tests
```bash
cd services/dispatch  && mvn -B test   # geofence logic (Mockito) - incl. "alert fires exactly once"
cd services/ticketing && mvn -B test   # HMAC tokens + "ticket can only be validated once" (H2)
```

## API reference (via the gateway, `localhost:8080`)

| Method & path | Purpose | Backend |
|---|---|---|
| `POST /api/tickets/purchase` `{routeId, passengerId}` | Create ticket + Stripe Checkout → `{ticketId, checkoutUrl}` | Ticketing |
| `GET /api/tickets/{ticketId}/status` | Poll until `ACTIVE`; returns `qrToken` when active | Ticketing |
| `GET /api/tickets/{token}/validate` | Conductor validation; consumes the ticket | Ticketing |
| `POST /api/webhooks/stripe` | Stripe → payment confirmation (signature-verified) | Ticketing |
| `GET /api/stops`, `GET /api/routes` | Reference data for the Kiosk | Dispatch |
| `POST /api/stops/request` `{stopId, routeId, destination}` | Register a pickup request | Dispatch |

MQTT topics are documented in [`docs/asyncapi.yaml`](docs/asyncapi.yaml).

## Key design decisions

- **One Redis geo set for buses *and* stops** (`geo:index`, members `bus:{id}` / `stop:{id}`), so `GEODIST` gives bus↔stop distance directly. Pending requests live in a hash per route (`pending:{routeId}`).
- **Alert-exactly-once:** the moment an alert fires, the stop is deleted from `pending:{routeId}`. This is what stops a bus idling near a stop from re-alerting on every GPS ping. It's covered by a unit test.
- **Payment safety:** a `UNIQUE` constraint on `payments.provider_ref` makes Stripe's webhook retries harmless; ticket validation is `@Transactional` so two simultaneous scans can't both succeed.
- **QR tickets are HMAC-SHA256 signed** (`ticketId.expiry.signature`), verified in constant time without a DB lookup.
- The GPS ping carries `routeId` so the Dispatch Engine knows which route's requests to check.

## Where this deviates from the earlier plan

- **Spring MVC, not WebFlux, in the Dispatch Engine.** The service is driven by an MQTT callback thread rather than HTTP traffic, so reactive gave no real benefit here but added a lot of risk in code I couldn't run. (The Gateway is necessarily reactive — Spring Cloud Gateway requires it — but that's all configuration.) Easy to revisit later.
- **Gateway auth is scaffolded but not enforced.** The design never included a login/signup flow to *issue* JWTs, so every route is currently public. `SecurityConfig` has the decoder wired and a commented block showing how to enforce it once you have a token issuer (Keycloak, Auth0 and Cognito all have free tiers). **Do not deploy publicly before this is done** — anyone could call the validate endpoint.
- **Tests use Mockito/H2, not Testcontainers**, so they run without Docker. Adding a Testcontainers-backed Redis test would be the best next test to add, since `GEODIST` behavior itself is only mocked today.

## Known gaps / next steps

- Auth (above); per-route pricing (price is a fixed `TICKET_PRICE_CENTS`); no ticket-expiry sweeper (expiry is enforced at scan time via the token).
- Ticketing and Dispatch share one Postgres database but own separate tables; split into separate DBs if you want strict isolation.
- Real buses would need a GPS publisher (a small device/app posting to `bus/{busId}/gps`) — the simulator is a stand-in.
- Mosquitto runs with `allow_anonymous true` for local dev; add a password file before exposing it.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Browser shows CORS errors | Client origin not covered by `GATEWAY_CORS_ORIGINS` (default allows any `localhost` port) |
| Ticket stays "Pending" after paying | `stripe listen` not running, or `STRIPE_WEBHOOK_SECRET` doesn't match the one it printed |
| Dashboard stuck on "Reconnecting" | Mosquitto not running, or port 9001 blocked |
| No alert fires | No pending request for that stop/route yet — submit the Kiosk request *before* the bus passes; check `docker logs bts-dispatch` |
| `mvn` can't resolve a dependency | Bump the pinned version in that service's `pom.xml` |

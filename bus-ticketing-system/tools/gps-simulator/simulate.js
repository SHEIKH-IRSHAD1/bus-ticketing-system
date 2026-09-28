// GPS simulator for the Bus Ticketing & Dispatch System.
//
// Publishes fake GPS pings to MQTT topic "bus/{busId}/gps", moving a bus
// along a fixed loop of stops (matching the seed data in the Dispatch
// service's Flyway migration V1__init_schema.sql). This lets you fully
// exercise the Dispatch Engine's geofencing logic without the Passenger PWA,
// Kiosk, or any other component running.
//
// Usage:
//   MQTT_URL=mqtt://localhost:1883 BUS_ID=bus-1 ROUTE_ID=1 node simulate.js
//
// Env vars (all optional, sensible defaults below):
//   MQTT_URL        default: mqtt://localhost:1883
//   BUS_ID          default: bus-1
//   ROUTE_ID        default: 1
//   STEP_SECONDS    default: 2   (seconds between GPS pings)
//   STEPS_PER_LEG   default: 20  (pings between one stop and the next)

import mqtt from "mqtt";

const MQTT_URL = process.env.MQTT_URL || "mqtt://localhost:1883";
const BUS_ID = process.env.BUS_ID || "bus-1";
const ROUTE_ID = Number(process.env.ROUTE_ID || 1);
const STEP_SECONDS = Number(process.env.STEP_SECONDS || 2);
const STEPS_PER_LEG = Number(process.env.STEPS_PER_LEG || 20);

// Matches the seed data in services/dispatch/src/main/resources/db/migration/V1__init_schema.sql
const STOPS = [
  { id: 1, name: "Central Station", lat: 12.9716, lon: 77.5946 },
  { id: 2, name: "City Hall", lat: 12.975, lon: 77.599 },
  { id: 3, name: "Museum Road", lat: 12.9789, lon: 77.604 },
  { id: 4, name: "Tech Park Gate 2", lat: 12.9825, lon: 77.609 },
];

function haversineMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function interpolate(a, b, t) {
  return { lat: a.lat + (b.lat - a.lat) * t, lon: a.lon + (b.lon - a.lon) * t };
}

async function main() {
  console.log(`Connecting to ${MQTT_URL} ...`);
  const client = await mqtt.connectAsync(MQTT_URL);
  console.log(`Connected. Simulating bus="${BUS_ID}" on route=${ROUTE_ID}.`);
  console.log("Press Ctrl+C to stop.\n");

  const topic = `bus/${BUS_ID}/gps`;

  let legIndex = 0;
  while (true) {
    const from = STOPS[legIndex % STOPS.length];
    const to = STOPS[(legIndex + 1) % STOPS.length];
    console.log(`--- Leg: ${from.name} -> ${to.name} ---`);

    for (let step = 0; step <= STEPS_PER_LEG; step++) {
      const t = step / STEPS_PER_LEG;
      const pos = interpolate(from, to, t);
      const distToTarget = haversineMeters(pos.lat, pos.lon, to.lat, to.lon);

      const payload = JSON.stringify({
        busId: BUS_ID,
        routeId: ROUTE_ID,
        lat: pos.lat,
        lon: pos.lon,
        timestamp: Date.now(),
      });

      await client.publishAsync(topic, payload, { qos: 1 });
      console.log(
        `  -> lat=${pos.lat.toFixed(5)} lon=${pos.lon.toFixed(5)}  ` +
          `(${Math.round(distToTarget)}m from ${to.name})`
      );

      await sleep(STEP_SECONDS * 1000);
    }

    legIndex++;
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((err) => {
  console.error("Simulator failed:", err);
  process.exit(1);
});

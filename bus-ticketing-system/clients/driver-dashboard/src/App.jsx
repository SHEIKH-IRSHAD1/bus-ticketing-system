import { useEffect, useRef, useState } from "react";
import mqtt from "mqtt";

const MQTT_WS_URL = import.meta.env.VITE_MQTT_WS_URL || "ws://localhost:9001";
const MAX_ALERTS = 20;

export default function App() {
  // Each tablet is assigned to one bus. Stored locally so a reboot keeps the assignment.
  const [busId, setBusId] = useState(() => localStorage.getItem("busId") || "bus-1");
  const [busInput, setBusInput] = useState(busId);
  const [connection, setConnection] = useState("offline"); // connected | reconnecting | offline
  const [alerts, setAlerts] = useState([]);
  const clientRef = useRef(null);

  useEffect(() => {
    const topic = `alerts/${busId}/stop`;
    const client = mqtt.connect(MQTT_WS_URL, {
      reconnectPeriod: 2000, // retry every 2s if the tablet loses WiFi
      connectTimeout: 10_000,
      clientId: `driver-${busId}-${Math.random().toString(16).slice(2, 8)}`
    });
    clientRef.current = client;

    client.on("connect", () => {
      setConnection("connected");
      client.subscribe(topic, { qos: 1 });
    });
    client.on("reconnect", () => setConnection("reconnecting"));
    client.on("close", () => setConnection((c) => (c === "connected" ? "reconnecting" : c)));
    client.on("offline", () => setConnection("offline"));

    client.on("message", (_topic, payload) => {
      try {
        const alert = JSON.parse(payload.toString());
        setAlerts((prev) => [{ ...alert, receivedAt: new Date() }, ...prev].slice(0, MAX_ALERTS));
      } catch (e) {
        console.warn("Ignoring malformed alert payload", e);
      }
    });

    return () => {
      client.end(true);
    };
  }, [busId]);

  function applyBusId() {
    const trimmed = busInput.trim();
    if (trimmed) {
      localStorage.setItem("busId", trimmed);
      setAlerts([]);
      setBusId(trimmed);
    }
  }

  return (
    <div className="dash">
      <div className="topbar">
        <h1>Driver Dashboard</h1>
        <span className={`conn ${connection}`}>
          {connection === "connected" ? "● Live" : connection === "reconnecting" ? "● Reconnecting..." : "● Offline"}
        </span>
      </div>

      <div className="busrow">
        <label htmlFor="bus">Bus ID</label>
        <input id="bus" value={busInput} onChange={(e) => setBusInput(e.target.value)} />
        <button onClick={applyBusId}>Set</button>
      </div>

      {alerts.length === 0 ? (
        <div className="empty">No stop requests yet. You'll be alerted here as you approach a requested stop.</div>
      ) : (
        alerts.map((a, i) => (
          <div key={`${a.stopId}-${a.receivedAt.getTime()}`} className={`alert ${i === 0 ? "latest" : ""}`}>
            <div className="stop">Stop at {a.stopName}</div>
            <div className="meta">
              Passenger going to {a.destination} · {Math.round(a.distanceMeters)} m ahead
            </div>
            <div className="time">{a.receivedAt.toLocaleTimeString()}</div>
          </div>
        ))
      )}
    </div>
  );
}

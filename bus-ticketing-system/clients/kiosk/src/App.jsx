import { useEffect, useMemo, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

export default function App() {
  const [stops, setStops] = useState([]);
  const [stopId, setStopId] = useState("");
  const [destinationId, setDestinationId] = useState("");
  const [status, setStatus] = useState(null); // { kind: "ok" | "err", text }
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetch(`${API_BASE_URL}/stops`)
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then(setStops)
      .catch((e) => setStatus({ kind: "err", text: `Could not load stops: ${e.message}` }));
  }, []);

  const currentStop = useMemo(() => stops.find((s) => String(s.id) === stopId), [stops, stopId]);
  const destinations = useMemo(
    () => stops.filter((s) => currentStop && s.routeId === currentStop.routeId && s.id !== currentStop.id),
    [stops, currentStop]
  );

  async function submit() {
    const destination = stops.find((s) => String(s.id) === destinationId);
    setSubmitting(true);
    setStatus(null);
    try {
      const res = await fetch(`${API_BASE_URL}/stops/request`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          stopId: currentStop.id,
          routeId: currentStop.routeId,
          destination: destination.name
        })
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setStatus({ kind: "ok", text: "Request sent! The next bus will stop for you." });
      setStopId("");
      setDestinationId("");
    } catch (e) {
      setStatus({ kind: "err", text: `Request failed: ${e.message}` });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="kiosk">
      <h1>Request a bus stop</h1>
      <div className="sub">Tell the next bus to stop for you.</div>

      <label htmlFor="stop">You are at</label>
      <select
        id="stop"
        value={stopId}
        onChange={(e) => { setStopId(e.target.value); setDestinationId(""); }}
      >
        <option value="">Select your stop...</option>
        {stops.map((s) => (
          <option key={s.id} value={s.id}>{s.name}</option>
        ))}
      </select>

      <label htmlFor="dest">Going to</label>
      <select
        id="dest"
        value={destinationId}
        onChange={(e) => setDestinationId(e.target.value)}
        disabled={!currentStop}
      >
        <option value="">Select destination...</option>
        {destinations.map((s) => (
          <option key={s.id} value={s.id}>{s.name}</option>
        ))}
      </select>

      <button onClick={submit} disabled={!stopId || !destinationId || submitting}>
        {submitting ? "Sending..." : "Request pickup"}
      </button>

      {status && <div className={`msg ${status.kind}`}>{status.text}</div>}
    </div>
  );
}

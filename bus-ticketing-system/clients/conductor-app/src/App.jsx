import { useCallback, useEffect, useRef, useState } from "react";
import { Html5Qrcode } from "html5-qrcode";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

// Human-friendly wording for the reason codes the Ticketing Service returns.
const REASON_TEXT = {
  ALREADY_USED: "This ticket was already scanned.",
  EXPIRED: "This ticket has expired.",
  INVALID_SIGNATURE: "Invalid ticket - possible forgery.",
  MALFORMED_TOKEN: "This QR code is not a valid ticket.",
  TICKET_NOT_FOUND: "Ticket not found.",
  NOT_ACTIVE: "This ticket has not been paid for."
};

export default function App() {
  const [result, setResult] = useState(null); // { valid, reason } | null
  const [error, setError] = useState(null);
  const scannerRef = useRef(null);
  const busyRef = useRef(false); // guards against the same QR firing many times in a row

  const validate = useCallback(async (token) => {
    if (busyRef.current) return;
    busyRef.current = true;
    try {
      // Pause the camera while we show the result.
      await scannerRef.current?.pause(true);
    } catch { /* already paused */ }

    try {
      const res = await fetch(`${API_BASE_URL}/tickets/${encodeURIComponent(token)}/validate`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setResult(await res.json());
    } catch (e) {
      setError(`Could not reach the ticket service: ${e.message}`);
      busyRef.current = false;
      try { scannerRef.current?.resume(); } catch { /* ignore */ }
    }
  }, []);

  useEffect(() => {
    const scanner = new Html5Qrcode("conductor-reader");
    scannerRef.current = scanner;

    scanner
      .start(
        { facingMode: "environment" }, // rear camera
        { fps: 10, qrbox: { width: 260, height: 260 } },
        (decodedText) => validate(decodedText),
        () => {}
      )
      .catch((e) => setError(`Camera error: ${e}`));

    return () => {
      scanner.isScanning && scanner.stop().catch(() => {});
    };
  }, [validate]);

  function scanNext() {
    setResult(null);
    setError(null);
    busyRef.current = false;
    try { scannerRef.current?.resume(); } catch { /* ignore */ }
  }

  return (
    <div className="app">
      <h1>Ticket Scanner</h1>

      {/* Camera stays mounted so it can be paused/resumed; result overlays it. */}
      <div className="scanner-box" style={{ display: result ? "none" : "block" }}>
        <div id="conductor-reader" />
      </div>
      {!result && <div className="hint">Point the camera at the passenger's QR ticket.</div>}

      {result && (
        <>
          <div className={`result ${result.valid ? "pass" : "fail"}`}>
            <div className="icon">{result.valid ? "✓" : "✗"}</div>
            <div className="title">{result.valid ? "Valid ticket" : "Rejected"}</div>
            {!result.valid && (
              <div className="reason">{REASON_TEXT[result.reason] || result.reason}</div>
            )}
            {result.valid && <div className="reason">Ticket #{result.ticketId}</div>}
          </div>
          <button className="next" onClick={scanNext}>Scan next ticket</button>
        </>
      )}

      {error && <div className="error">{error}</div>}
    </div>
  );
}

import { useEffect, useRef } from "react";
import { Html5QrcodeScanner } from "html5-qrcode";

/**
 * Opens the camera and scans the QR code printed inside the bus.
 * Expected QR content: either a plain route id ("1") or JSON like
 * {"routeId":1,"busId":"bus-1"}.
 */
export default function ScanScreen({ onScanned }) {
  const scannerRef = useRef(null);

  useEffect(() => {
    const scanner = new Html5QrcodeScanner(
      "bus-qr-reader",
      { fps: 10, qrbox: { width: 240, height: 240 } },
      false
    );
    scannerRef.current = scanner;

    scanner.render(
      (decodedText) => {
        const routeId = parseRouteId(decodedText);
        if (routeId == null) {
          return; // keep scanning - this wasn't one of our bus QR codes
        }
        scanner.clear().catch(() => {});
        onScanned(routeId);
      },
      () => {} // per-frame decode failures are normal; ignore them
    );

    return () => {
      scanner.clear().catch(() => {});
    };
  }, [onScanned]);

  return (
    <div className="card">
      <h2>Scan the QR code on the bus</h2>
      <p className="muted">Point your camera at the QR code near the door.</p>
      <div id="bus-qr-reader" />
    </div>
  );
}

function parseRouteId(text) {
  try {
    const parsed = JSON.parse(text);
    if (parsed && parsed.routeId != null) return Number(parsed.routeId);
  } catch {
    // not JSON - fall through
  }
  const n = Number(text);
  return Number.isFinite(n) && n > 0 ? n : null;
}

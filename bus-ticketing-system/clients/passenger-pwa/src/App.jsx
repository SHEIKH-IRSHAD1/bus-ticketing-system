import { useCallback, useState } from "react";
import ScanScreen from "./screens/ScanScreen.jsx";
import PaymentScreen from "./screens/PaymentScreen.jsx";
import TicketScreen from "./screens/TicketScreen.jsx";

/**
 * Tiny hand-rolled "router" - this app only has a few screens, so a routing
 * library would be more weight than value.
 *
 *   /ticket?ticketId=123  -> TicketScreen (Stripe redirects here after payment)
 *   /cancelled            -> payment-cancelled message
 *   anything else         -> Scan -> Pay flow
 */
export default function App() {
  const params = new URLSearchParams(window.location.search);
  const path = window.location.pathname;
  const ticketId = params.get("ticketId");

  const [routeId, setRouteId] = useState(null);
  const handleScanned = useCallback((id) => setRouteId(id), []);

  let content;
  if (path === "/ticket" && ticketId) {
    content = <TicketScreen ticketId={ticketId} />;
  } else if (path === "/cancelled") {
    content = (
      <div className="card">
        <h2>Payment cancelled</h2>
        <p className="muted">No charge was made.</p>
        <a href="/">
          <button className="btn">Start over</button>
        </a>
      </div>
    );
  } else if (routeId == null) {
    content = <ScanScreen onScanned={handleScanned} />;
  } else {
    content = <PaymentScreen routeId={routeId} onCancel={() => setRouteId(null)} />;
  }

  return (
    <div className="app">
      <div className="header">
        <h1>Bus Ticket</h1>
      </div>
      {content}
    </div>
  );
}

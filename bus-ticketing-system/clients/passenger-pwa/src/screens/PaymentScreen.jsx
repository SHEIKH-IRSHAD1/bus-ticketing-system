import { useState } from "react";
import { purchaseTicket } from "../api.js";

// A random anonymous id per device - there is no login in this MVP.
function getPassengerId() {
  let id = localStorage.getItem("passengerId");
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem("passengerId", id);
  }
  return id;
}

export default function PaymentScreen({ routeId, onCancel }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  async function handlePay() {
    setLoading(true);
    setError(null);
    try {
      const { checkoutUrl } = await purchaseTicket({
        routeId,
        passengerId: getPassengerId()
      });
      // Hand off to Stripe's hosted checkout page. Stripe redirects back to
      // /ticket?ticketId=... when payment completes.
      window.location.href = checkoutUrl;
    } catch (e) {
      setError(e.message);
      setLoading(false);
    }
  }

  return (
    <div className="card">
      <h2>Buy a ticket</h2>
      <p>
        Route <strong>{routeId}</strong>
      </p>
      <button className="btn" onClick={handlePay} disabled={loading}>
        {loading ? "Redirecting to payment..." : "Pay now"}
      </button>
      <button className="btn secondary" onClick={onCancel} disabled={loading}>
        Scan a different bus
      </button>
      {error && <div className="error">{error}</div>}
      <p className="muted">Payments are processed by Stripe (test mode).</p>
    </div>
  );
}

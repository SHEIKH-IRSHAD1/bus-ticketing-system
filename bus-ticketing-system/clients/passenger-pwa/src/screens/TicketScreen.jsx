import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import { getTicketStatus } from "../api.js";

/**
 * Shown after Stripe redirects back. Polls the Ticketing Service until the
 * webhook has activated the ticket, then renders the signed token as a QR.
 */
export default function TicketScreen({ ticketId }) {
  const [ticket, setTicket] = useState(null);
  const [error, setError] = useState(null);
  const canvasRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    let timer;

    async function poll() {
      try {
        const t = await getTicketStatus(ticketId);
        if (cancelled) return;
        setTicket(t);
        // Keep polling only while payment is still being confirmed.
        if (t.status === "PENDING_PAYMENT") {
          timer = setTimeout(poll, 1500);
        }
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
    }
    poll();

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [ticketId]);

  useEffect(() => {
    if (ticket?.qrToken && canvasRef.current) {
      QRCode.toCanvas(canvasRef.current, ticket.qrToken, { width: 280, margin: 1 });
    }
  }, [ticket]);

  if (error) {
    return (
      <div className="card">
        <div className="error">{error}</div>
      </div>
    );
  }
  if (!ticket) {
    return <div className="card">Loading your ticket...</div>;
  }

  const pillClass =
    ticket.status === "ACTIVE" ? "active" : ticket.status === "USED" ? "used" : "";

  return (
    <div className="card">
      <h2>Your ticket #{ticket.ticketId}</h2>
      <span className={`status-pill ${pillClass}`}>{ticket.status.replace("_", " ")}</span>

      {ticket.status === "PENDING_PAYMENT" && (
        <p className="muted">Confirming your payment... this usually takes a few seconds.</p>
      )}
      {ticket.qrToken && (
        <>
          <div className="qr-wrap">
            <canvas ref={canvasRef} />
          </div>
          <p className="muted">Show this QR code to the conductor.</p>
        </>
      )}
      {ticket.status === "USED" && <p className="error">This ticket has already been used.</p>}
    </div>
  );
}

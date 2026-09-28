const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

async function handleResponse(res) {
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`Request failed (${res.status}): ${text}`);
  }
  return res.json();
}

export async function purchaseTicket({ routeId, passengerId }) {
  const res = await fetch(`${API_BASE_URL}/tickets/purchase`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ routeId, passengerId })
  });
  return handleResponse(res);
}

export async function getTicketStatus(ticketId) {
  const res = await fetch(`${API_BASE_URL}/tickets/${ticketId}/status`);
  return handleResponse(res);
}

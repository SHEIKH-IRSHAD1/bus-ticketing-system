CREATE TABLE tickets (
    id              BIGSERIAL PRIMARY KEY,
    passenger_id    VARCHAR(120) NOT NULL,
    route_id        BIGINT NOT NULL,
    qr_token        TEXT,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    used_at         TIMESTAMPTZ
);

CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       BIGINT NOT NULL REFERENCES tickets(id),
    provider_ref    VARCHAR(200) NOT NULL,
    amount_cents    BIGINT NOT NULL,
    currency        VARCHAR(10) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- The single most important line in this migration: this constraint is
    -- what makes Stripe webhook retries safe. Without it, a retried webhook
    -- could create a second payment row for the same event.
    CONSTRAINT uq_payments_provider_ref UNIQUE (provider_ref)
);

CREATE INDEX idx_payments_ticket_id ON payments(ticket_id);
CREATE INDEX idx_tickets_status ON tickets(status);

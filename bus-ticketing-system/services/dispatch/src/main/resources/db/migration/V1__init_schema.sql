-- Dispatch service owns route/stop reference data: the static geography
-- that the geofencing logic checks live GPS against.

CREATE TABLE routes (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stops (
    id          BIGSERIAL PRIMARY KEY,
    route_id    BIGINT NOT NULL REFERENCES routes(id),
    name        VARCHAR(120) NOT NULL,
    lat         DOUBLE PRECISION NOT NULL,
    lon         DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stops_route_id ON stops(route_id);

-- Seed data for local development / the GPS simulator.
-- Coordinates are a short stretch of route in downtown-style spacing (~400-600m apart)
-- so the default 300m geofence threshold is easy to demo.
INSERT INTO routes (id, name) VALUES (1, 'Route 42 - Downtown Loop');

INSERT INTO stops (id, route_id, name, lat, lon) VALUES
    (1, 1, 'Central Station',      12.9716, 77.5946),
    (2, 1, 'City Hall',            12.9750, 77.5990),
    (3, 1, 'Museum Road',          12.9789, 77.6040),
    (4, 1, 'Tech Park Gate 2',     12.9825, 77.6090);

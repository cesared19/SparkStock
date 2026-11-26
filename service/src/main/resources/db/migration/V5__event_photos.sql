-- Photos taken when returning a device (e.g. the damage of one going to repair), kept with that event of its history.
CREATE TABLE event_photos (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES device_events (id),
    content_type TEXT NOT NULL,
    data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX event_photos_event ON event_photos (event_id);

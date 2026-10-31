-- ============================================================================
-- Phase 2 (Notification)
--   S2  inbox_events   : "idempotent consumer" table. One row per event id this service has
--                        ever accepted. The PRIMARY KEY is what rejects the second copy,
--                        even when two consumer instances race (database decides, not Java).
--   S1  dead_letters   : copy of every message that exhausted its retries (or was bad data),
--                        so an operator can look at it and send it back after a fix.
-- ============================================================================
CREATE TABLE inbox_events (
    event_id    UUID         PRIMARY KEY,
    event_type  VARCHAR(60)  NOT NULL,
    booking_id  UUID,
    received_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE dead_letters (
    id           UUID         PRIMARY KEY,
    topic        VARCHAR(200) NOT NULL,
    kafka_key    VARCHAR(200),
    payload      TEXT         NOT NULL,
    error        VARCHAR(1000),
    status       VARCHAR(20)  NOT NULL,   -- PARKED -> REPLAYED
    created_at   TIMESTAMPTZ  NOT NULL,
    replayed_at  TIMESTAMPTZ
);
CREATE INDEX idx_dead_letters_status ON dead_letters (status, created_at);

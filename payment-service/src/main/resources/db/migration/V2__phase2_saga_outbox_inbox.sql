-- ============================================================================
-- Phase 2 (Payment)
--   S3/S4  outbox_events : PaymentSucceeded / PaymentFailed / PaymentRefunded are written here in the
--                          SAME transaction as the payment state change; a relay publishes them to Kafka.
--   S2/S5  inbox_events  : ids of Booking events already handled (BookingRejected / BookingCancelled),
--                          so a duplicate delivery can never refund twice.
--   S5     payments.refund_reference          : provider's refund id.
--   S6     payments.last_booking_event_version: newest Booking event version applied (stale ones ignored).
--   S9     status UNKNOWN : provider call timed out, real outcome not known yet (see reconciliation).
-- ============================================================================
CREATE TABLE outbox_events (
    id           UUID         PRIMARY KEY,
    seq          BIGINT GENERATED ALWAYS AS IDENTITY,
    aggregate_id UUID         NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    kafka_key    VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (seq) WHERE published_at IS NULL;

CREATE TABLE inbox_events (
    event_id    UUID        PRIMARY KEY,
    event_type  VARCHAR(60) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE payments ADD COLUMN refund_reference           VARCHAR(100);
ALTER TABLE payments ADD COLUMN last_booking_event_version BIGINT NOT NULL DEFAULT 0;

-- A booking may have only ONE live payment. UNKNOWN is live too (money may have moved).
DROP INDEX uq_payment_active_booking;
CREATE UNIQUE INDEX uq_payment_active_booking ON payments (booking_id)
    WHERE status IN ('INITIATED', 'PENDING', 'UNKNOWN', 'SUCCESS');
CREATE INDEX idx_payments_status_updated ON payments (status, updated_at);

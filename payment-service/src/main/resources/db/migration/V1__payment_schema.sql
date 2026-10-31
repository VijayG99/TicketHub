CREATE TABLE payments (
    id                 UUID PRIMARY KEY,
    booking_id         UUID           NOT NULL,
    customer_id        VARCHAR(100)   NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    provider           VARCHAR(30)    NOT NULL,
    provider_reference VARCHAR(100),
    failure_reason     VARCHAR(300),
    booking_notified   BOOLEAN        NOT NULL DEFAULT FALSE,
    sync_attempts      INT            NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0
);
-- ST-24: repeated initiation can never create a second live payment for the same booking.
CREATE UNIQUE INDEX uq_payment_active_booking ON payments (booking_id) WHERE status IN ('INITIATED', 'PENDING', 'SUCCESS');
CREATE UNIQUE INDEX uq_payment_provider_ref ON payments (provider_reference) WHERE provider_reference IS NOT NULL;
CREATE INDEX idx_payments_customer ON payments (customer_id);
CREATE INDEX idx_payments_unsynced ON payments (booking_notified, status);

-- ST-24 / SC-10: every provider event id is processed at most once.
CREATE TABLE processed_webhook_events (
    event_id     VARCHAR(100) PRIMARY KEY,
    payment_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

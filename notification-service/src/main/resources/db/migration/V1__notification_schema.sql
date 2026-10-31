CREATE TABLE notifications (
    id                 UUID PRIMARY KEY,
    source_event_id    UUID         NOT NULL,
    booking_id         UUID         NOT NULL,
    type               VARCHAR(30)  NOT NULL,
    channel            VARCHAR(20)  NOT NULL,
    recipient          VARCHAR(200),
    subject            VARCHAR(300) NOT NULL,
    body               TEXT         NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    provider_reference VARCHAR(200),
    failure_reason     VARCHAR(500),
    attempts           INT          NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    sent_at            TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0,
    -- ST-58 / SC-08: the same BookingConfirmed event (or booking) can only ever produce one notification
    CONSTRAINT uq_notification_source_event UNIQUE (source_event_id),
    CONSTRAINT uq_notification_booking_type UNIQUE (booking_id, type)
);
CREATE INDEX idx_notifications_status ON notifications (status, attempts);

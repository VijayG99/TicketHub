-- ST-79: a booking can now produce one notification PER CHANNEL (EMAIL and PUSH).
-- The old constraints allowed only one row per event / per booking, so they are widened to include the channel.
-- Still true: the same event can never create two notifications on the SAME channel.
ALTER TABLE notifications DROP CONSTRAINT uq_notification_source_event;
ALTER TABLE notifications DROP CONSTRAINT uq_notification_booking_type;
ALTER TABLE notifications ADD CONSTRAINT uq_notification_source_event_channel UNIQUE (source_event_id, channel);
ALTER TABLE notifications ADD CONSTRAINT uq_notification_booking_type_channel UNIQUE (booking_id, type, channel);

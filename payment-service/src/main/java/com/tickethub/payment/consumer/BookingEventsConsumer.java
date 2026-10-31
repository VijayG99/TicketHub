package com.tickethub.payment.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.payment.event.BookingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * S5: Payment listens to "booking-events" (key = bookingId, so one booking's events stay in order).
 * Same failure handling as the other consumers: non-blocking retry topics, then a dead-letter topic.
 * A dead-lettered refund request is found later by the reconciliation job (S8): a SUCCESS payment
 * whose booking is expired/cancelled gets refunded there.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventsConsumer {

    private final ObjectMapper objectMapper;
    private final BookingEventHandler handler;

    @RetryableTopic(
            attempts = "${payment.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${payment.retry.delay-ms:2000}",
                    multiplierExpression = "${payment.retry.multiplier:2.0}"),
            exclude = {JsonProcessingException.class, IllegalArgumentException.class},
            traversingCauses = "true",
            numPartitions = "3",
            replicationFactor = "1")
    @KafkaListener(topics = "${tickethub.kafka.booking-events-topic:booking-events}")
    public void onMessage(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.info("Received booking event topic={} key={} partition={} offset={}",
                record.topic(), record.key(), record.partition(), record.offset());
        handler.handle(objectMapper.readValue(record.value(), BookingEvent.class));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record,
                             @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String error) {
        log.error("DEAD LETTER booking event key={} offset={} error={} payload={}",
                record.key(), record.offset(), error, record.value());
    }
}

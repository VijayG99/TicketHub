package com.tickethub.booking.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.booking.event.PaymentEvent;
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
 * S4: Booking listens to the Payment saga topic "payment-events" (key = bookingId).
 * Payment no longer calls Booking over REST. If Booking is down, the events simply wait in Kafka
 * and are applied when Booking is back: a Booking outage DELAYS confirmation but never loses it.
 *
 * Failures use the same non-blocking retry topics + dead-letter topic as Notification (S1).
 * A dead-lettered payment event is exactly the kind of drift the Payment reconciliation job (S8) repairs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventsConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentEventHandler handler;

    @RetryableTopic(
            attempts = "${booking.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${booking.retry.delay-ms:2000}",
                    multiplierExpression = "${booking.retry.multiplier:2.0}"),
            exclude = {JsonProcessingException.class, IllegalArgumentException.class},
            traversingCauses = "true",
            numPartitions = "3",
            replicationFactor = "1")
    @KafkaListener(topics = "${tickethub.kafka.payment-events-topic:payment-events}")
    public void onMessage(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.info("Received payment event topic={} key={} partition={} offset={}",
                record.topic(), record.key(), record.partition(), record.offset());
        handler.handle(objectMapper.readValue(record.value(), PaymentEvent.class));
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record,
                             @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String error) {
        log.error("DEAD LETTER payment event key={} offset={} error={} payload={}",
                record.key(), record.offset(), error, record.value());
    }
}

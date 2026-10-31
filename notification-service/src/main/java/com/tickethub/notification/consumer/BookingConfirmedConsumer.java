package com.tickethub.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.service.DeadLetterService;
import com.tickethub.notification.service.NotificationRecorder;
import com.tickethub.notification.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes BookingConfirmed in this service's OWN consumer group (ST-20, ST-60).
 *
 * <h3>What happens when processing fails (S1 + S10)</h3>
 * <pre>
 *  booking-confirmed            main topic
 *      | fails (e.g. SMTP down)
 *      v
 *  booking-confirmed-retry-0    wait  delay      (other messages keep flowing: NON-blocking retry)
 *      | fails
 *      v
 *  booking-confirmed-retry-1    wait  delay x 2
 *      | fails
 *      v
 *  booking-confirmed-retry-2    wait  delay x 4
 *      | fails
 *      v
 *  booking-confirmed-dlt        dead-letter topic -> onDeadLetter() saves it to the dead_letters table
 *
 *  BAD DATA (bad JSON, missing email -> IllegalArgumentException / JsonProcessingException)
 *  is excluded from retries and goes straight to -dlt, because waiting can never fix it.
 * </pre>
 * Topic names: Spring appends -retry-N and -dlt to the main topic name (the guide writes
 * booking.confirmed.DLT; here the main topic is booking-confirmed so the DLT is booking-confirmed-dlt).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingConfirmedConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final NotificationRecorder recorder;
    private final DeadLetterService deadLetterService;

    @RetryableTopic(
            attempts = "${notification.retry.attempts:4}",
            backoff = @Backoff(delayExpression = "${notification.retry.delay-ms:3000}",
                    multiplierExpression = "${notification.retry.multiplier:2.0}"),
            exclude = {JsonProcessingException.class, IllegalArgumentException.class},
            traversingCauses = "true",
            numPartitions = "3",
            replicationFactor = "1",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "${tickethub.kafka.booking-confirmed-topic}")
    public void onMessage(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.info("Received BookingConfirmed topic={} key={} partition={} offset={}",
                record.topic(), record.key(), record.partition(), record.offset());
        BookingConfirmedEvent event = objectMapper.readValue(record.value(), BookingConfirmedEvent.class);
        notificationService.handle(event);
    }

    /** Last stop: called for every message that ended up in the dead-letter topic. */
    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record,
                             @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String error) {
        log.error("DEAD LETTER key={} partition={} offset={} error={}",
                record.key(), record.partition(), record.offset(), error);
        deadLetterService.park(record.topic(), record.key(), record.value(), error);
        // best effort: if the payload is readable, mark its notification DEAD so operators can see it
        try {
            BookingConfirmedEvent event = objectMapper.readValue(record.value(), BookingConfirmedEvent.class);
            UUID eventId = event.getEventId();
            if (eventId != null) {
                recorder.markDeadBySourceEvent(eventId, error);
            }
        } catch (Exception ignore) {
            // unreadable payload: nothing to mark, the dead_letters row is enough
        }
    }
}

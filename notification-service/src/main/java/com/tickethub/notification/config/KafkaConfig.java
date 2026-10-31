package com.tickethub.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka setup for Notification.
 *
 * The old blocking DefaultErrorHandler (retry 3 times, then skip) was REMOVED in Phase 2.
 * Failure handling now lives on the listener (@RetryableTopic): retries happen on separate
 * retry topics without blocking the partition, and the final stop is a dead-letter topic.
 */
@Configuration
@EnableKafkaRetryTopic
public class KafkaConfig {

    /** Same definition as Booking's: whichever service starts first creates the topic (idempotent). */
    @Bean
    NewTopic bookingConfirmedTopic(@Value("${tickethub.kafka.booking-confirmed-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}

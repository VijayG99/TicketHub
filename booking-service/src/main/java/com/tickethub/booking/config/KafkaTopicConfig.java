package com.tickethub.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics used by Booking. 3 partitions each so consumer groups can be demonstrated (S11) and so
 * messages with different booking ids spread out; all messages of ONE booking use the same key
 * (the booking id) and therefore the same partition, which keeps them in order (S6).
 *
 * <pre>
 *   booking-confirmed : Booking -> Notification   BookingConfirmed
 *   booking-events    : Booking -> Payment        BookingRejected, BookingCancelled
 *   payment-events    : Payment -> Booking        PaymentSucceeded, PaymentFailed, PaymentRefunded
 * </pre>
 * Creating a topic that already exists is a no-op, so every service may declare the topics it uses.
 */
@Configuration
@EnableKafkaRetryTopic
public class KafkaTopicConfig {

    @Bean
    NewTopic bookingConfirmedTopic(@Value("${tickethub.kafka.booking-confirmed-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic bookingEventsTopic(@Value("${tickethub.kafka.booking-events-topic:booking-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEventsTopic(@Value("${tickethub.kafka.payment-events-topic:payment-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}

package com.tickethub.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.config.TopicBuilder;

/** Topics Payment reads/writes (same definitions as Booking's; creating an existing topic is a no-op). */
@Configuration
@EnableKafkaRetryTopic
public class KafkaTopicConfig {

    @Bean
    NewTopic paymentEventsTopic(@Value("${tickethub.kafka.payment-events-topic:payment-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic bookingEventsTopic(@Value("${tickethub.kafka.booking-events-topic:booking-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}

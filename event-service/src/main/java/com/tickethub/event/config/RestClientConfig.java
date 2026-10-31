package com.tickethub.event.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** Load-balanced builder: callers use service names (resolved through Eureka), never host:port. */
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(ObservationRegistry observationRegistry) {
        // the registry makes outgoing calls part of the trace (traceparent header, ST-28)
        return RestClient.builder().observationRegistry(observationRegistry);
    }
}

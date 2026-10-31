package com.tickethub.booking.client;

import com.tickethub.booking.common.exception.BusinessRuleException;
import com.tickethub.booking.common.exception.DependencyTimeoutException;
import com.tickethub.booking.common.exception.DependencyUnavailableException;
import com.tickethub.booking.common.exception.ResourceNotFoundException;
import com.tickethub.booking.common.web.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.SocketTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Booking -> Event/Show REST client (ST-10). The service is addressed by name (Eureka), every call
 * has explicit timeouts (ST-16), the idempotent GET is retried with bounded backoff (ST-17) and a
 * circuit breaker fails fast when Event/Show keeps failing (ST-18).
 */
@Slf4j
@Component
public class ShowClient {

    private final RestClient restClient;

    public ShowClient(@LoadBalanced RestClient.Builder builder,
                      @Value("${tickethub.clients.event-service.base-url}") String baseUrl,
                      @Value("${tickethub.clients.event-service.connect-timeout-ms}") int connectTimeoutMs,
                      @Value("${tickethub.clients.event-service.read-timeout-ms}") int readTimeoutMs,
                         @Value("${tickethub.security.internal-api-key:}") String internalApiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    if (internalApiKey != null && !internalApiKey.isBlank()) {
                        request.getHeaders().set("X-Internal-Key", internalApiKey); // ST-76 service-to-service auth
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Retry(name = "eventService")
    @CircuitBreaker(name = "eventService", fallbackMethod = "showCircuitOpen")
    public ShowInfo getShow(Long showId) {
        try {
            ShowInfo show = restClient.get()
                    .uri("/internal/shows/{id}", showId)
                    .retrieve()
                    .body(ShowInfo.class);
            if (show == null) {
                throw new DependencyUnavailableException("Event service returned an empty response");
            }
            return show;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                throw new ResourceNotFoundException("Show " + showId + " not found");
            }
            if (status >= 400 && status < 500) {
                throw new BusinessRuleException("Event service rejected the show lookup");
            }
            log.warn("Event service returned {} for show {}", status, showId);
            throw new DependencyUnavailableException("Event service failed to respond correctly");
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new DependencyTimeoutException("Event service timed out");
            }
            throw new DependencyUnavailableException("Event service is unavailable");
        } catch (IllegalStateException e) {
            // e.g. "No instances available for event-service"
            throw new DependencyUnavailableException("Event service is unavailable");
        }
    }

    /** Circuit open: fail fast with a controlled error. There is no safe default for show data. */
    public ShowInfo showCircuitOpen(Long showId, CallNotPermittedException e) {
        log.warn("Circuit 'eventService' is open; failing fast for show {}", showId);
        throw new DependencyUnavailableException(
                "Show information is temporarily unavailable. Please try again shortly.");
    }
}

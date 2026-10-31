package com.tickethub.event.client;

import com.tickethub.event.common.exception.BusinessRuleException;
import com.tickethub.event.common.exception.DependencyTimeoutException;
import com.tickethub.event.common.exception.DependencyUnavailableException;
import com.tickethub.event.common.exception.ResourceNotFoundException;
import com.tickethub.event.common.web.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.SocketTimeoutException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Event/Show -> Booking REST client. Reads reservation state through Booking's API only.
 * Timeout (ST-16), bounded retry for this idempotent GET (ST-17) and circuit breaker (ST-18).
 */
@Slf4j
@Component
public class BookingClient {

    private final RestClient restClient;

    public BookingClient(@LoadBalanced RestClient.Builder builder,
                         @Value("${tickethub.clients.booking-service.base-url}") String baseUrl,
                         @Value("${tickethub.clients.booking-service.connect-timeout-ms}") int connectTimeoutMs,
                         @Value("${tickethub.clients.booking-service.read-timeout-ms}") int readTimeoutMs,
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

    @Retry(name = "bookingService")
    @CircuitBreaker(name = "bookingService", fallbackMethod = "seatStatesCircuitOpen")
    public List<SeatReservationDto> getSeatStates(Long showId) {
        try {
            List<SeatReservationDto> body = restClient.get()
                    .uri("/internal/bookings/shows/{showId}/seat-states", showId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<SeatReservationDto>>() { });
            return body != null ? body : List.of();
        } catch (RestClientResponseException e) {
            throw mapStatus(e);
        } catch (ResourceAccessException e) {
            throw mapIo(e);
        } catch (IllegalStateException e) {
            // e.g. "No instances available for booking-service"
            throw new DependencyUnavailableException("Booking service is unavailable");
        }
    }

    /** Fail-fast path when the circuit is open; there is no meaningful default for seat state. */
    public List<SeatReservationDto> seatStatesCircuitOpen(Long showId, CallNotPermittedException e) {
        log.warn("Circuit 'bookingService' is open; failing fast for show {}", showId);
        throw new DependencyUnavailableException(
                "Seat availability is temporarily unavailable. Please try again shortly.");
    }

    private RuntimeException mapStatus(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 404) {
            return new ResourceNotFoundException("Requested resource not found in Booking service");
        }
        if (status >= 400 && status < 500) {
            return new BusinessRuleException("Booking service rejected the request");
        }
        log.warn("Booking service returned {}", status);
        return new DependencyUnavailableException("Booking service failed to respond correctly");
    }

    private RuntimeException mapIo(ResourceAccessException e) {
        if (e.getCause() instanceof SocketTimeoutException) {
            return new DependencyTimeoutException("Booking service timed out");
        }
        return new DependencyUnavailableException("Booking service is unavailable");
    }
}

package com.tickethub.payment.client;

import com.tickethub.payment.common.exception.BusinessRuleException;
import com.tickethub.payment.common.exception.DependencyTimeoutException;
import com.tickethub.payment.common.exception.DependencyUnavailableException;
import com.tickethub.payment.common.exception.ResourceNotFoundException;
import com.tickethub.payment.common.web.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.SocketTimeoutException;
import java.util.UUID;
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
 * Payment -> Booking REST client. Both calls are safe to retry: the GET is read-only and the
 * payment-result callback is idempotent on the Booking side (ST-17). Timeouts (ST-16) and a
 * circuit breaker (ST-18) protect Payment from a slow or failing Booking.
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
    @CircuitBreaker(name = "bookingService", fallbackMethod = "getBookingCircuitOpen")
    public BookingSnapshot getBooking(UUID bookingId) {
        try {
            BookingSnapshot snapshot = restClient.get()
                    .uri("/internal/bookings/{id}", bookingId)
                    .retrieve()
                    .body(BookingSnapshot.class);
            if (snapshot == null) {
                throw new DependencyUnavailableException("Booking service returned an empty response");
            }
            return snapshot;
        } catch (RestClientResponseException e) {
            throw map(e, "Booking " + bookingId + " not found");
        } catch (ResourceAccessException e) {
            throw mapIo(e);
        } catch (IllegalStateException e) {
            throw new DependencyUnavailableException("Booking service is unavailable");
        }
    }

    @Retry(name = "bookingService")
    @CircuitBreaker(name = "bookingService", fallbackMethod = "sendResultCircuitOpen")
    public PaymentResultAck sendPaymentResult(UUID bookingId, PaymentResultRequest request) {
        try {
            PaymentResultAck ack = restClient.post()
                    .uri("/internal/bookings/{id}/payment-result", bookingId)
                    .body(request)
                    .retrieve()
                    .body(PaymentResultAck.class);
            if (ack == null) {
                throw new DependencyUnavailableException("Booking service returned an empty response");
            }
            return ack;
        } catch (RestClientResponseException e) {
            throw map(e, "Booking " + bookingId + " not found");
        } catch (ResourceAccessException e) {
            throw mapIo(e);
        } catch (IllegalStateException e) {
            throw new DependencyUnavailableException("Booking service is unavailable");
        }
    }

    /** S8: ids of bookings Booking confirmed in the last N minutes (for reconciliation). Read-only. */
    public java.util.List<UUID> listConfirmedBookingIds(int sinceMinutes) {
        try {
            UUID[] ids = restClient.get()
                    .uri("/internal/bookings/confirmed-ids?sinceMinutes={m}", sinceMinutes)
                    .retrieve()
                    .body(UUID[].class);
            return ids == null ? java.util.List.of() : java.util.List.of(ids);
        } catch (RestClientResponseException e) {
            throw map(e, "Booking list not found");
        } catch (ResourceAccessException e) {
            throw mapIo(e);
        } catch (IllegalStateException e) {
            throw new DependencyUnavailableException("Booking service is unavailable");
        }
    }

    public BookingSnapshot getBookingCircuitOpen(UUID bookingId, CallNotPermittedException e) {
        log.warn("Circuit 'bookingService' is open; failing fast getBooking({})", bookingId);
        throw new DependencyUnavailableException("Booking service is temporarily unavailable. Please try again shortly.");
    }

    public PaymentResultAck sendResultCircuitOpen(UUID bookingId, PaymentResultRequest request, CallNotPermittedException e) {
        log.warn("Circuit 'bookingService' is open; payment result for booking {} will be re-synced later", bookingId);
        throw new DependencyUnavailableException("Booking service is temporarily unavailable");
    }

    private RuntimeException map(RestClientResponseException e, String notFoundMessage) {
        int status = e.getStatusCode().value();
        if (status == 404) {
            return new ResourceNotFoundException(notFoundMessage);
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

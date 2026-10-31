package com.tickethub.payment.provider;

import com.tickethub.payment.common.exception.DependencyTimeoutException;
import com.tickethub.payment.common.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Wraps every provider call with an explicit timeout (ST-16, SC-05) and a circuit breaker
 * (ST-18, SC-16). Payment initiation is NOT retried: it is not safe to repeat blindly (ST-17).
 */
@Slf4j
@Component
public class ProviderInvoker {

    private final PaymentProviderGateway gateway;
    private final long timeoutMs;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ProviderInvoker(PaymentProviderGateway gateway, @Value("${payment.provider-timeout-ms}") long timeoutMs) {
        this.gateway = gateway;
        this.timeoutMs = timeoutMs;
    }

    public PaymentProviderGateway gateway() {
        return gateway;
    }

    @CircuitBreaker(name = "paymentProvider", fallbackMethod = "circuitOpen")
    public ProviderResult initiate(ProviderRequest request) {
        Future<ProviderResult> future = executor.submit(() -> gateway.initiate(request));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Payment provider timed out after {} ms for payment {}", timeoutMs, request.getPaymentId());
            throw new DependencyTimeoutException("Payment provider did not respond in time");
        } catch (ExecutionException e) {
            log.warn("Payment provider call failed for payment {}: {}", request.getPaymentId(), e.getCause());
            throw new DependencyUnavailableException("Payment provider call failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("Payment provider call was interrupted");
        }
    }

    /** S9: ask the provider what happened to a payment (timeout-bounded, not circuit-broken: it is read-only). */
    public ProviderResult lookup(java.util.UUID paymentId) {
        return callWithTimeout(() -> gateway.lookup(paymentId), "lookup " + paymentId);
    }

    /** S5: idempotent refund at the provider (timeout-bounded). Safe to call again after a crash. */
    public String refund(java.util.UUID paymentId, String providerReference, java.math.BigDecimal amount) {
        return callWithTimeout(() -> gateway.refund(paymentId, providerReference, amount), "refund " + paymentId);
    }

    private <T> T callWithTimeout(java.util.concurrent.Callable<T> call, String label) {
        Future<T> future = executor.submit(call);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new DependencyTimeoutException("Payment provider did not respond in time (" + label + ")");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new DependencyUnavailableException("Payment provider call failed (" + label + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("Payment provider call was interrupted");
        }
    }

    /** Circuit open: fail fast. A payment cannot be faked, so there is no fallback value. */
    public ProviderResult circuitOpen(ProviderRequest request, CallNotPermittedException e) {
        log.warn("Circuit 'paymentProvider' is open; failing fast for payment {}", request.getPaymentId());
        throw new DependencyUnavailableException("Payment provider is temporarily unavailable. Please try again shortly.");
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}

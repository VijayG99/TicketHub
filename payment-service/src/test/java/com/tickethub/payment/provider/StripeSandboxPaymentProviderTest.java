package com.tickethub.payment.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tickethub.payment.common.enums.CurrencyCode;
import com.tickethub.payment.enums.ProviderStatus;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Runs the adapter against a local fake of Stripe's API: no network and no real key. */
class StripeSandboxPaymentProviderTest {

    private HttpServer server;
    private final AtomicReference<String> idempotencyKey = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private String startFakeStripe(int status, String json) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payment_intents", exchange -> {
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private StripeSandboxPaymentProvider provider(String baseUrl) {
        return new StripeSandboxPaymentProvider(new ObjectMapper(), "sk_test_dummy", baseUrl, "pm_card_visa");
    }

    @Test
    void liveKeysAreRefused() {
        assertThrows(IllegalStateException.class, () ->
                new StripeSandboxPaymentProvider(new ObjectMapper(), "sk_live_abc", "https://api.stripe.com", "pm_card_visa"));
        assertThrows(IllegalStateException.class, () ->
                new StripeSandboxPaymentProvider(new ObjectMapper(), "", "https://api.stripe.com", "pm_card_visa"));
    }

    @Test
    void succeededIntentMapsToSucceededAndSendsIdempotencyKey() throws Exception {
        String url = startFakeStripe(200, "{\"id\":\"pi_123\",\"status\":\"succeeded\"}");
        UUID paymentId = UUID.randomUUID();
        ProviderResult result = provider(url).initiate(
                new ProviderRequest(paymentId, new BigDecimal("250.00"), CurrencyCode.INR, null));
        assertEquals(ProviderStatus.SUCCEEDED, result.getStatus());
        assertEquals("pi_123", result.getProviderReference());
        assertEquals(paymentId.toString(), idempotencyKey.get());
        assertTrue(requestBody.get().contains("amount=25000"));
        assertTrue(requestBody.get().contains("currency=inr"));
    }

    @Test
    void cardErrorMapsToFailed() throws Exception {
        String url = startFakeStripe(402, "{\"error\":{\"message\":\"Your card was declined.\"}}");
        ProviderResult result = provider(url).initiate(
                new ProviderRequest(UUID.randomUUID(), new BigDecimal("10.00"), CurrencyCode.USD, null));
        assertEquals(ProviderStatus.FAILED, result.getStatus());
        assertTrue(result.getFailureReason().contains("declined"));
    }

    @Test
    void requiresActionMapsToPending() throws Exception {
        String url = startFakeStripe(200, "{\"id\":\"pi_9\",\"status\":\"requires_action\"}");
        ProviderResult result = provider(url).initiate(
                new ProviderRequest(UUID.randomUUID(), new BigDecimal("10.00"), CurrencyCode.EUR, null));
        assertEquals(ProviderStatus.PENDING, result.getStatus());
    }
}

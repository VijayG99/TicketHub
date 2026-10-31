package com.tickethub.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.payment.common.exception.DependencyUnavailableException;
import com.tickethub.payment.enums.PaymentProviderName;
import com.tickethub.payment.enums.ProviderStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real provider sandbox (ST-22): Stripe in TEST mode. Active only when payment.provider=stripe
 * (start the stack with TICKETHUB_PROFILES=docker,stripe and STRIPE_SECRET_KEY=sk_test_...).
 * A PaymentIntent is created and confirmed with Stripe's test card token, so no card data ever
 * touches TicketHub. The payment id is sent as the Idempotency-Key, so a repeated call can never
 * create a second charge.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "stripe")
public class StripeSandboxPaymentProvider implements PaymentProviderGateway {

    private final ObjectMapper objectMapper;
    private final String secretKey;
    private final String baseUrl;
    private final String paymentMethod;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public StripeSandboxPaymentProvider(ObjectMapper objectMapper,
                                        @Value("${payment.stripe.secret-key:}") String secretKey,
                                        @Value("${payment.stripe.base-url:https://api.stripe.com}") String baseUrl,
                                        @Value("${payment.stripe.payment-method:pm_card_visa}") String paymentMethod) {
        if (secretKey == null || !secretKey.startsWith("sk_test_")) {
            throw new IllegalStateException(
                    "payment.provider=stripe needs STRIPE_SECRET_KEY set to a Stripe TEST key (sk_test_...)");
        }
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
        this.paymentMethod = paymentMethod;
    }

    @Override
    public PaymentProviderName name() {
        return PaymentProviderName.STRIPE;
    }

    @Override
    public ProviderResult initiate(ProviderRequest request) {
        // Stripe amounts are integers in the smallest currency unit (paise, cents)
        long minorUnits = request.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        String form = "amount=" + minorUnits
                + "&currency=" + enc(request.getCurrency().name().toLowerCase())
                + "&payment_method=" + enc(paymentMethod)
                + "&confirm=true"
                + "&automatic_payment_methods[enabled]=true"
                + "&automatic_payment_methods[allow_redirects]=never"
                + "&metadata[paymentId]=" + enc(request.getPaymentId().toString());
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/payment_intents"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", request.getPaymentId().toString())
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            int status = response.statusCode();
            if (status == 200) {
                return fromIntent(body);
            }
            if (status == 402) { // card error: a definite failure, nothing was charged
                String reason = body.path("error").path("message").asText("Card declined");
                return new ProviderResult(ProviderStatus.FAILED, body.path("error").path("payment_intent").path("id").asText(null),
                        truncate("STRIPE: " + reason));
            }
            log.warn("Stripe returned HTTP {} for payment {}: {}", status, request.getPaymentId(),
                    body.path("error").path("message").asText("(no message)"));
            throw new DependencyUnavailableException("Payment provider rejected the request");
        } catch (IOException e) {
            throw new DependencyUnavailableException("Payment provider could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("Payment provider call was interrupted");
        }
    }

    /**
     * S9: find the PaymentIntent we created for this payment id (we stored it in metadata) and report its state.
     * Uses Stripe's search API. Not exercised by the automated tests (needs a real sk_test_ key).
     */
    @Override
    public ProviderResult lookup(UUID paymentId) {
        String query = "metadata['paymentId']:'" + paymentId + "'";
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/payment_intents/search?query=" + enc(query)))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + secretKey)
                .GET().build();
        try {
            HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new DependencyUnavailableException("Payment provider lookup failed");
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            return data.isArray() && data.size() > 0 ? fromIntent(data.get(0)) : null;
        } catch (IOException e) {
            throw new DependencyUnavailableException("Payment provider could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("Payment provider call was interrupted");
        }
    }

    /** S5: Stripe Refunds API. The Idempotency-Key makes a repeated call return the same refund. */
    @Override
    public String refund(UUID paymentId, String providerReference, BigDecimal amount) {
        String form = "payment_intent=" + enc(providerReference) + "&metadata[paymentId]=" + enc(paymentId.toString());
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/refunds"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Idempotency-Key", "refund-" + paymentId)
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Stripe refund for payment {} returned HTTP {}", paymentId, response.statusCode());
                throw new DependencyUnavailableException("Payment provider rejected the refund");
            }
            return objectMapper.readTree(response.body()).path("id").asText();
        } catch (IOException e) {
            throw new DependencyUnavailableException("Payment provider could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("Payment provider call was interrupted");
        }
    }

    private ProviderResult fromIntent(JsonNode intent) {
        String id = intent.path("id").asText();
        switch (intent.path("status").asText()) {
            case "succeeded":
                return new ProviderResult(ProviderStatus.SUCCEEDED, id, null);
            case "processing":
            case "requires_action":
            case "requires_confirmation":
                return new ProviderResult(ProviderStatus.PENDING, id, null);
            default:
                return new ProviderResult(ProviderStatus.FAILED, id, "STRIPE: payment not completed");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String s) {
        return s.length() > 290 ? s.substring(0, 290) : s;
    }
}

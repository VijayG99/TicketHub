package com.tickethub.notification.sender;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationChannel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Phase 2 (S10 / ST-74, ST-79): protection placed AROUND the real senders, one set per channel.
 *
 * <ul>
 *   <li><b>Circuit breaker</b>: after several failures the circuit OPENS and calls fail instantly
 *       (no waiting on a dead SMTP server). After a pause it lets a few calls through to test recovery.</li>
 *   <li><b>Rate limiter</b> (email): caps sends per second so the provider does not throttle us.</li>
 * </ul>
 * Email and push have SEPARATE breakers ("emailProvider", "pushProvider"), so an SMTP outage never
 * blocks push messages. Any failure is treated like a delivery failure: the notification becomes
 * RETRYING and the Kafka message moves to the next retry topic. Limits: config-repo/notification-service.yml.
 * IMPORTANT: callers must call sendEmail()/sendPush() directly. A method of this class calling another
 * of its own methods would skip the Spring proxy, and the breaker would silently not apply.
 * (Kept under its old class name to avoid churn; it now guards every channel.)
 */
@Component
@RequiredArgsConstructor
public class EmailDeliveryGuard {

    private final List<NotificationSender> senders;

    @CircuitBreaker(name = "emailProvider")
    @RateLimiter(name = "emailProvider")
    public String sendEmail(Notification notification) {
        return find(NotificationChannel.EMAIL).send(notification);
    }

    @CircuitBreaker(name = "pushProvider")
    public String sendPush(Notification notification) {
        return find(NotificationChannel.PUSH).send(notification);
    }

    private NotificationSender find(NotificationChannel channel) {
        return senders.stream().filter(s -> s.channel() == channel).findFirst()
                .orElseThrow(() -> new NotificationDeliveryException("No sender configured for channel " + channel));
    }
}

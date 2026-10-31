package com.tickethub.notification.sender;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationChannel;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends through SMTP. Locally this points at MailHog (a controlled test mailbox, UI on :8025);
 * a real provider only needs different spring.mail.* settings supplied from secrets (ST-21).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.sender", havingValue = "email")
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationSender(JavaMailSender mailSender, @Value("${notification.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public String send(Notification n) {
        if (n.getRecipient() == null || n.getRecipient().isBlank()) {
            throw new NotificationDeliveryException("No recipient available for booking " + n.getBookingId());
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(n.getRecipient());
            helper.setSubject(n.getSubject());
            helper.setText(n.getBody(), false);
            mailSender.send(message);
            String messageId = message.getMessageID();
            log.info("Email sent to={} bookingId={} messageId={}", n.getRecipient(), n.getBookingId(), messageId);
            return messageId != null ? messageId : "SMTP-OK";
        } catch (Exception e) {
            throw new NotificationDeliveryException("SMTP delivery failed: " + e.getMessage(), e);
        }
    }
}

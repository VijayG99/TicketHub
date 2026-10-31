package com.tickethub.notification.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tickethub.notification.entity.Notification;
import com.tickethub.notification.enums.NotificationStatus;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.sender.EmailDeliveryGuard;
import com.tickethub.notification.sender.NotificationDeliveryException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S1 / S2 / S10 decisions of NotificationService, with the database and the mail server mocked. */
class NotificationServiceTest {

    private NotificationRecorder recorder;
    private NotificationRepositoryAccess access;
    private EmailDeliveryGuard guard;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        recorder = mock(NotificationRecorder.class);
        access = mock(NotificationRepositoryAccess.class);
        guard = mock(EmailDeliveryGuard.class);
        service = new NotificationService(recorder, access, guard);
    }

    private BookingConfirmedEvent event(String email) {
        BookingConfirmedEvent e = new BookingConfirmedEvent();
        e.setEventId(UUID.randomUUID());
        e.setBookingId(UUID.randomUUID());
        e.setCustomerEmail(email);
        return e;
    }

    private Notification notification(NotificationStatus status) {
        Notification n = new Notification();
        n.setChannel(com.tickethub.notification.enums.NotificationChannel.EMAIL);
        n.setId(UUID.randomUUID());
        n.setBookingId(UUID.randomUUID());
        n.setStatus(status);
        return n;
    }

    @Test
    void missingEmailIsBadDataAndIsNotRetried() {
        // IllegalArgumentException is on the retry topic's exclude list => straight to the dead-letter topic (S1)
        assertThrows(IllegalArgumentException.class, () -> service.handle(event("")));
        verify(recorder, never()).accept(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void duplicateOfAnAlreadySentNotificationSendsNothing() {
        Notification sent = notification(NotificationStatus.SENT);
        when(recorder.accept(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new NotificationRecorder.Accepted(java.util.List.of(sent), true));
        service.handle(event("a@b.c"));
        verify(guard, never()).sendEmail(any());
        verify(guard, never()).sendPush(any());
    }

    @Test
    void duplicateWhoseFirstAttemptNeverFinishedIsDeliveredAgain() {
        Notification pending = notification(NotificationStatus.RETRYING);
        when(recorder.accept(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new NotificationRecorder.Accepted(java.util.List.of(pending), true));
        when(access.find(pending.getId())).thenReturn(pending);
        when(guard.sendEmail(pending)).thenReturn("MSG-1");
        service.handle(event("a@b.c"));
        verify(recorder).markSent(pending.getId(), "MSG-1");
    }

    @Test
    void sendFailureIsRecordedAsRetryingAndRethrownSoKafkaRetries() {
        Notification fresh = notification(NotificationStatus.PENDING);
        when(recorder.accept(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new NotificationRecorder.Accepted(java.util.List.of(fresh), false));
        when(access.find(fresh.getId())).thenReturn(fresh);
        when(guard.sendEmail(fresh)).thenThrow(new NotificationDeliveryException("SMTP down"));
        assertThrows(NotificationDeliveryException.class, () -> service.handle(event("a@b.c")));
        verify(recorder).markRetrying(fresh.getId(), "SMTP down");
        verify(recorder, never()).markSent(any(), any());
    }

    @Test
    void anSmtpFailureDoesNotStopThePushChannel() {
        Notification email = notification(NotificationStatus.PENDING);
        Notification push = notification(NotificationStatus.PENDING);
        push.setChannel(com.tickethub.notification.enums.NotificationChannel.PUSH);
        when(recorder.accept(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new NotificationRecorder.Accepted(java.util.List.of(email, push), false));
        when(access.find(email.getId())).thenReturn(email);
        when(access.find(push.getId())).thenReturn(push);
        when(guard.sendEmail(email)).thenThrow(new NotificationDeliveryException("SMTP down"));
        when(guard.sendPush(push)).thenReturn("PUSH-1");
        // the email failure is rethrown (so Kafka retries) ...
        assertThrows(NotificationDeliveryException.class, () -> service.handle(event("a@b.c")));
        // ... but the push was still delivered, independently
        verify(recorder).markSent(push.getId(), "PUSH-1");
        verify(recorder).markRetrying(email.getId(), "SMTP down");
    }
}

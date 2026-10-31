package com.tickethub.booking.job;

import com.tickethub.booking.enums.BookingStatus;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.service.BookingService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases seats of PENDING_PAYMENT bookings past their expiry (ST-09). The interval is configurable. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {

    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Scheduled(fixedDelayString = "${booking.expiry-sweep-interval-ms:15000}")
    public void sweep() {
        List<UUID> due = bookingRepository.findDueIds(BookingStatus.PENDING_PAYMENT, Instant.now(), PageRequest.of(0, BATCH_SIZE));
        int expired = 0;
        for (UUID id : due) {
            try {
                if (bookingService.expireById(id)) {
                    expired++;
                }
            } catch (Exception e) {
                // e.g. optimistic-lock clash with a concurrent payment result; it is retried on the next sweep
                log.warn("Could not expire booking {}: {}", id, e.toString());
            }
        }
        if (expired > 0) {
            log.info("Expiry sweep released {} booking(s)", expired);
        }
    }
}

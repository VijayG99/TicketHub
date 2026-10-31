package com.tickethub.booking.dto;

import com.tickethub.booking.enums.BookingStatus;
import com.tickethub.booking.enums.SagaState;
import com.tickethub.booking.saga.SagaLog;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/** S7: answer to "where is booking X and why?". */
@Getter
@Builder
public class SagaView {
    private final UUID bookingId;
    private final BookingStatus status;
    private final SagaState sagaState;
    private final String explanation;
    private final List<SagaLog.Entry> timeline;
}

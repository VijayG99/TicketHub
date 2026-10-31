package com.tickethub.booking.saga;

import com.tickethub.booking.enums.SagaState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * S7: an append-only diary of every saga step of a booking. It answers "what happened to
 * booking X, in what order, and why?" with one query. Written in the same transaction as the step.
 */
@Component
@RequiredArgsConstructor
public class SagaLog {

    public record Entry(SagaState state, String step, String detail, Instant at) {
    }

    private final JdbcTemplate jdbc;

    public void record(UUID bookingId, SagaState state, String step, String detail) {
        String d = detail != null && detail.length() > 300 ? detail.substring(0, 300) : detail;
        jdbc.update("INSERT INTO saga_log (booking_id, saga_state, step, detail, created_at) VALUES (?, ?, ?, ?, ?)",
                bookingId, state.name(), step, d, Timestamp.from(Instant.now()));
    }

    public List<Entry> timeline(UUID bookingId) {
        return jdbc.query("SELECT saga_state, step, detail, created_at FROM saga_log WHERE booking_id = ? ORDER BY id",
                (rs, i) -> new Entry(SagaState.valueOf(rs.getString("saga_state")), rs.getString("step"),
                        rs.getString("detail"), rs.getTimestamp("created_at").toInstant()),
                bookingId);
    }
}

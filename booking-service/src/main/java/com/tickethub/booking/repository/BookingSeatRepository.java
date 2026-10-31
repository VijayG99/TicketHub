package com.tickethub.booking.repository;

import com.tickethub.booking.entity.BookingSeat;
import com.tickethub.booking.enums.SeatReservationState;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    List<BookingSeat> findByShowIdAndSeatIdInAndStateNot(Long showId, Collection<Long> seatIds, SeatReservationState state);

    List<BookingSeat> findByShowIdAndStateNot(Long showId, SeatReservationState state);
}

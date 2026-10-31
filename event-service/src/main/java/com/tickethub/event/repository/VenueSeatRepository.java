package com.tickethub.event.repository;

import com.tickethub.event.entity.VenueSeat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueSeatRepository extends JpaRepository<VenueSeat, Long> {

    List<VenueSeat> findByVenueIdOrderByRowLabelAscSeatNumberAsc(Long venueId);

    boolean existsByVenueId(Long venueId);

    boolean existsByVenueIdAndRowLabel(Long venueId, String rowLabel);
}

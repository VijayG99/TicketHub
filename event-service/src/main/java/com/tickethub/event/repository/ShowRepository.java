package com.tickethub.event.repository;

import com.tickethub.event.entity.Show;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShowRepository extends JpaRepository<Show, Long>, JpaSpecificationExecutor<Show> {

    List<Show> findByEventId(Long eventId);
}

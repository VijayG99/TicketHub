package com.tickethub.notification.repository;

import com.tickethub.notification.entity.DeadLetter;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetter, UUID> {
    Page<DeadLetter> findByStatus(String status, Pageable pageable);
}

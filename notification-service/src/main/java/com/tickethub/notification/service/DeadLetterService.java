package com.tickethub.notification.service;

import com.tickethub.notification.common.exception.BusinessRuleException;
import com.tickethub.notification.common.exception.DependencyUnavailableException;
import com.tickethub.notification.common.exception.ResourceNotFoundException;
import com.tickethub.notification.common.web.PageResponse;
import com.tickethub.notification.dto.DeadLetterResponse;
import com.tickethub.notification.entity.DeadLetter;
import com.tickethub.notification.repository.DeadLetterRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S1: parks failed messages in the database and lets an operator send them back after a fix.
 *
 * Replay = publish the (optionally corrected) payload to the MAIN topic again with the same key.
 * Because of the inbox (S2), replaying a message that was in fact processed is harmless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final io.micrometer.core.instrument.MeterRegistry registry;

    @Value("${tickethub.kafka.booking-confirmed-topic}")
    private String mainTopic;

    @Transactional
    public void park(String topic, String key, String payload, String error) {
        DeadLetter d = new DeadLetter();
        d.setId(UUID.randomUUID());
        d.setTopic(topic);
        d.setKafkaKey(key);
        d.setPayload(payload == null ? "" : payload);
        d.setError(error != null && error.length() > 1000 ? error.substring(0, 1000) : error);
        d.setStatus("PARKED");
        d.setCreatedAt(Instant.now());
        repository.save(d);
        registry.counter("tickethub_dead_letters_parked_total").increment(); // ST-77
        log.warn("Message parked in dead_letters id={} key={} error={}", d.getId(), key, error);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeadLetterResponse> list(String status, Pageable pageable) {
        var page = (status == null || status.isBlank())
                ? repository.findAll(pageable)
                : repository.findByStatus(status.toUpperCase(), pageable);
        return PageResponse.of(page, DeadLetterService::toResponse);
    }

    /**
     * @param correctedPayload if not null, this JSON is sent instead of the stored one
     *                         (the "fix" in "send one back after a fix").
     */
    @Transactional
    public DeadLetterResponse replay(UUID id, String correctedPayload) {
        DeadLetter d = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dead letter " + id + " not found"));
        if (!"PARKED".equals(d.getStatus())) {
            throw new BusinessRuleException("Dead letter " + id + " was already replayed");
        }
        String payload = (correctedPayload != null && !correctedPayload.isBlank()) ? correctedPayload : d.getPayload();
        try {
            kafkaTemplate.send(mainTopic, d.getKafkaKey(), payload).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new DependencyUnavailableException("Could not publish to Kafka: " + e.getMessage());
        }
        d.setStatus("REPLAYED");
        d.setReplayedAt(Instant.now());
        repository.save(d);
        log.info("Dead letter {} replayed to {}", id, mainTopic);
        return toResponse(d);
    }

    private static DeadLetterResponse toResponse(DeadLetter d) {
        return DeadLetterResponse.builder()
                .id(d.getId()).topic(d.getTopic()).key(d.getKafkaKey()).payload(d.getPayload())
                .error(d.getError()).status(d.getStatus())
                .createdAt(d.getCreatedAt()).replayedAt(d.getReplayedAt()).build();
    }
}

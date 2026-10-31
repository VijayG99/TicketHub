package com.tickethub.event.service;

import com.tickethub.event.common.exception.BusinessRuleException;
import com.tickethub.event.common.exception.InvalidStateTransitionException;
import com.tickethub.event.common.exception.ResourceNotFoundException;
import com.tickethub.event.common.security.CurrentUser;
import com.tickethub.event.common.web.PageResponse;
import com.tickethub.event.dto.EventRequest;
import com.tickethub.event.dto.EventResponse;
import com.tickethub.event.entity.Event;
import com.tickethub.event.entity.Show;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.EventType;
import com.tickethub.event.enums.ShowStatus;
import com.tickethub.event.mapper.CatalogMapper;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.ShowRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final ShowRepository showRepository;

    @Transactional
    public EventResponse create(EventRequest request) {
        Event event = new Event();
        apply(event, request);
        event.setStatus(EventStatus.DRAFT);
        Event saved = eventRepository.save(event);
        log.info("Event created id={} title={}", saved.getId(), saved.getTitle());
        return CatalogMapper.toResponse(saved);
    }

    @Transactional
    public EventResponse update(Long id, EventRequest request) {
        Event event = find(id);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled event cannot be edited");
        }
        apply(event, request);
        return CatalogMapper.toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse changeStatus(Long id, EventStatus target) {
        Event event = find(id);
        if (!event.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Event cannot move from " + event.getStatus() + " to " + target);
        }
        event.setStatus(target);
        eventRepository.save(event);
        if (target == EventStatus.CANCELLED) {
            cancelOpenShows(event);
        }
        log.info("Event id={} moved to {}", id, target);
        return CatalogMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long id, CurrentUser user) {
        Event event = find(id);
        if (!user.isAdmin() && event.getStatus() != EventStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Event " + id + " not found");
        }
        return CatalogMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> list(EventStatus status, EventType type, CurrentUser user, Pageable pageable) {
        // Customers only ever see PUBLISHED events; admins may filter by any status.
        final EventStatus effectiveStatus = user.isAdmin() ? status : EventStatus.PUBLISHED;
        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (effectiveStatus != null) {
                predicates.add(cb.equal(root.get("status"), effectiveStatus));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Event> page = eventRepository.findAll(spec, pageable);
        return PageResponse.of(page, CatalogMapper::toResponse);
    }

    private void cancelOpenShows(Event event) {
        for (Show show : showRepository.findByEventId(event.getId())) {
            if (show.getStatus().canTransitionTo(ShowStatus.CANCELLED)) {
                show.setStatus(ShowStatus.CANCELLED);
                showRepository.save(show);
            }
        }
    }

    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        find(id);
    }

    @Transactional
    public EventResponse updatePoster(Long id, String posterUrl) {
        Event event = find(id);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled event cannot be edited");
        }
        event.setPosterUrl(posterUrl);
        return CatalogMapper.toResponse(eventRepository.save(event));
    }

    private Event find(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event " + id + " not found"));
    }

    private void apply(Event event, EventRequest request) {
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setType(request.getType());
        event.setPosterUrl(request.getPosterUrl());
    }
}

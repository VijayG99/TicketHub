package com.tickethub.event.service;

import com.tickethub.event.client.BookingClient;
import com.tickethub.event.client.SeatReservationDto;
import com.tickethub.event.common.exception.BusinessRuleException;
import com.tickethub.event.common.exception.InvalidStateTransitionException;
import com.tickethub.event.common.exception.ResourceNotFoundException;
import com.tickethub.event.common.security.CurrentUser;
import com.tickethub.event.common.web.PageResponse;
import com.tickethub.event.dto.SeatAvailabilityResponse;
import com.tickethub.event.dto.SeatInfo;
import com.tickethub.event.dto.SeatStateResponse;
import com.tickethub.event.dto.ShowInternalResponse;
import com.tickethub.event.dto.ShowRequest;
import com.tickethub.event.dto.ShowResponse;
import com.tickethub.event.entity.Event;
import com.tickethub.event.entity.Show;
import com.tickethub.event.entity.Venue;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.SeatReservationState;
import com.tickethub.event.enums.ShowStatus;
import com.tickethub.event.enums.VenueStatus;
import com.tickethub.event.mapper.CatalogMapper;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.ShowRepository;
import com.tickethub.event.repository.VenueRepository;
import com.tickethub.event.repository.VenueSeatRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class ShowService {

    /** Customers can see these show states (never CANCELLED/COMPLETED). */
    private static final Set<ShowStatus> CUSTOMER_VISIBLE =
            EnumSet.of(ShowStatus.SCHEDULED, ShowStatus.OPEN, ShowStatus.SOLD_OUT);

    private final ShowRepository showRepository;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final VenueSeatRepository seatRepository;
    private final BookingClient bookingClient;
    private final TransactionTemplate readOnlyTx;

    public ShowService(ShowRepository showRepository, EventRepository eventRepository,
                       VenueRepository venueRepository, VenueSeatRepository seatRepository,
                       BookingClient bookingClient, PlatformTransactionManager txManager) {
        this.showRepository = showRepository;
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.seatRepository = seatRepository;
        this.bookingClient = bookingClient;
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
    }

    @Transactional
    public ShowResponse create(ShowRequest request) {
        validateTimes(request);
        if (!request.getStartTime().isAfter(Instant.now())) {
            throw new BusinessRuleException("A show must start in the future");
        }
        Show show = new Show();
        bindReferences(show, request);
        show.setStatus(ShowStatus.SCHEDULED);
        Show saved = showRepository.save(show);
        log.info("Show created id={} event={} venue={}", saved.getId(), request.getEventId(), request.getVenueId());
        return CatalogMapper.toResponse(saved);
    }

    @Transactional
    public ShowResponse update(Long id, ShowRequest request) {
        Show show = findShow(id);
        if (show.getStatus().isTerminal()) {
            throw new BusinessRuleException("A " + show.getStatus() + " show cannot be edited");
        }
        validateTimes(request);
        bindReferences(show, request);
        return CatalogMapper.toResponse(showRepository.save(show));
    }

    @Transactional
    public ShowResponse changeStatus(Long id, ShowStatus target) {
        Show show = findShow(id);
        if (!show.getStatus().canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Show cannot move from " + show.getStatus() + " to " + target);
        }
        if (target == ShowStatus.OPEN && show.getStatus() == ShowStatus.SCHEDULED) {
            if (show.getEvent().getStatus() != EventStatus.PUBLISHED) {
                throw new BusinessRuleException("A show can only be opened when its event is PUBLISHED");
            }
            if (!seatRepository.existsByVenueId(show.getVenue().getId())) {
                throw new BusinessRuleException("A show cannot be opened before its venue has a seat layout");
            }
        }
        show.setStatus(target);
        log.info("Show id={} moved to {}", id, target);
        return CatalogMapper.toResponse(showRepository.save(show));
    }

    @Transactional(readOnly = true)
    public ShowResponse get(Long id, CurrentUser user) {
        return CatalogMapper.toResponse(loadVisible(id, user));
    }

    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> list(Long eventId, ShowStatus status, CurrentUser user, Pageable pageable) {
        final boolean admin = user.isAdmin();
        Specification<Show> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventId != null) {
                predicates.add(cb.equal(root.get("event").get("id"), eventId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (!admin) {
                predicates.add(cb.equal(root.get("event").get("status"), EventStatus.PUBLISHED));
                predicates.add(root.get("status").in(CUSTOMER_VISIBLE));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Show> page = showRepository.findAll(spec, pageable);
        return PageResponse.of(page, CatalogMapper::toResponse);
    }

    /**
     * Combines the seat layout (owned here) with reservation state (owned by Booking, fetched
     * through its API). The database transaction is closed before the remote call is made.
     */
    public SeatAvailabilityResponse seatAvailability(Long showId, CurrentUser user) {
        List<SeatInfo> layout = readOnlyTx.execute(status -> {
            Show show = loadVisible(showId, user);
            return seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(show.getVenue().getId())
                    .stream().map(CatalogMapper::toSeatInfo).toList();
        });

        Map<Long, SeatReservationState> states = bookingClient.getSeatStates(showId).stream()
                .collect(Collectors.toMap(SeatReservationDto::getSeatId, SeatReservationDto::getState, (a, b) -> a));

        List<SeatStateResponse> seats = layout.stream()
                .map(s -> SeatStateResponse.builder()
                        .seatId(s.getSeatId())
                        .rowLabel(s.getRowLabel())
                        .seatNumber(s.getSeatNumber())
                        .category(s.getCategory())
                        .state(states.getOrDefault(s.getSeatId(), SeatReservationState.AVAILABLE))
                        .build())
                .toList();
        return SeatAvailabilityResponse.builder().showId(showId).seats(seats).build();
    }

    /** Internal view used by Booking. Not exposed through the gateway. */
    @Transactional(readOnly = true)
    public ShowInternalResponse internalView(Long showId) {
        Show show = findShow(showId);
        List<SeatInfo> seats = seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(show.getVenue().getId())
                .stream().map(CatalogMapper::toSeatInfo).toList();
        return ShowInternalResponse.builder()
                .showId(show.getId())
                .eventId(show.getEvent().getId())
                .eventTitle(show.getEvent().getTitle())
                .eventStatus(show.getEvent().getStatus())
                .showStatus(show.getStatus())
                .venueId(show.getVenue().getId())
                .venueName(show.getVenue().getName())
                .startTime(show.getStartTime())
                .endTime(show.getEndTime())
                .ticketPrice(show.getTicketPrice())
                .currency(show.getCurrency())
                .seats(seats)
                .build();
    }

    private Show loadVisible(Long id, CurrentUser user) {
        Show show = findShow(id);
        boolean visible = user.isAdmin()
                || (show.getEvent().getStatus() == EventStatus.PUBLISHED && CUSTOMER_VISIBLE.contains(show.getStatus()));
        if (!visible) {
            throw new ResourceNotFoundException("Show " + id + " not found");
        }
        return show;
    }

    private Show findShow(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show " + id + " not found"));
    }

    private void validateTimes(ShowRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessRuleException("endTime must be after startTime");
        }
    }

    private void bindReferences(Show show, ShowRequest request) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event " + request.getEventId() + " not found"));
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Shows cannot be created for a cancelled event");
        }
        Venue venue = venueRepository.findById(request.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue " + request.getVenueId() + " not found"));
        if (venue.getStatus() != VenueStatus.ACTIVE) {
            throw new BusinessRuleException("Venue " + venue.getId() + " is not ACTIVE");
        }
        show.setEvent(event);
        show.setVenue(venue);
        show.setStartTime(request.getStartTime());
        show.setEndTime(request.getEndTime());
        show.setTicketPrice(request.getTicketPrice());
        show.setCurrency(request.getCurrency());
    }
}

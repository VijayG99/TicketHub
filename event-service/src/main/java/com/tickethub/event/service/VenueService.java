package com.tickethub.event.service;

import com.tickethub.event.common.exception.ConflictException;
import com.tickethub.event.common.exception.ResourceNotFoundException;
import com.tickethub.event.common.web.PageResponse;
import com.tickethub.event.dto.SeatLayoutRequest;
import com.tickethub.event.dto.SeatRowRequest;
import com.tickethub.event.dto.VenueRequest;
import com.tickethub.event.dto.VenueResponse;
import com.tickethub.event.dto.VenueSeatResponse;
import com.tickethub.event.entity.Venue;
import com.tickethub.event.entity.VenueSeat;
import com.tickethub.event.enums.VenueStatus;
import com.tickethub.event.mapper.CatalogMapper;
import com.tickethub.event.repository.VenueRepository;
import com.tickethub.event.repository.VenueSeatRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueSeatRepository seatRepository;

    @Transactional
    public VenueResponse create(VenueRequest request) {
        Venue venue = new Venue();
        apply(venue, request);
        venue.setStatus(request.getStatus() != null ? request.getStatus() : VenueStatus.ACTIVE);
        Venue saved = venueRepository.save(venue);
        log.info("Venue created id={} name={}", saved.getId(), saved.getName());
        return CatalogMapper.toResponse(saved);
    }

    @Transactional
    public VenueResponse update(Long id, VenueRequest request) {
        Venue venue = find(id);
        apply(venue, request);
        if (request.getStatus() != null) {
            venue.setStatus(request.getStatus());
        }
        return CatalogMapper.toResponse(venueRepository.save(venue));
    }

    @Transactional(readOnly = true)
    public VenueResponse get(Long id) {
        return CatalogMapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<VenueResponse> list(Pageable pageable) {
        return PageResponse.of(venueRepository.findAll(pageable), CatalogMapper::toResponse);
    }

    /** Adds rows of seats to the venue layout. A row label may only be defined once per venue. */
    @Transactional
    public List<VenueSeatResponse> addSeatRows(Long venueId, SeatLayoutRequest request) {
        Venue venue = find(venueId);
        Set<String> seenInRequest = new HashSet<>();
        List<VenueSeat> toSave = new ArrayList<>();
        for (SeatRowRequest row : request.getRows()) {
            if (!seenInRequest.add(row.getRowLabel())) {
                throw new ConflictException("Row " + row.getRowLabel() + " is listed more than once in the request");
            }
            if (seatRepository.existsByVenueIdAndRowLabel(venueId, row.getRowLabel())) {
                throw new ConflictException("Row " + row.getRowLabel() + " already exists in venue " + venueId);
            }
            for (int n = 1; n <= row.getSeatCount(); n++) {
                VenueSeat seat = new VenueSeat();
                seat.setVenue(venue);
                seat.setRowLabel(row.getRowLabel());
                seat.setSeatNumber(n);
                seat.setCategory(row.getCategory());
                toSave.add(seat);
            }
        }
        List<VenueSeat> saved = seatRepository.saveAll(toSave);
        log.info("Added {} seats to venue {}", saved.size(), venueId);
        return saved.stream().map(CatalogMapper::toSeatResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<VenueSeatResponse> getSeats(Long venueId) {
        find(venueId);
        return seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(venueId).stream()
                .map(CatalogMapper::toSeatResponse).toList();
    }

    private Venue find(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue " + id + " not found"));
    }

    private void apply(Venue venue, VenueRequest request) {
        venue.setName(request.getName().trim());
        venue.setCity(request.getCity().trim());
        venue.setAddress(request.getAddress());
    }
}

package com.tickethub.event.service;

import com.tickethub.event.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Uploads a poster to object storage, then records its URL on the event. Not transactional on purpose:
 * the remote upload never runs inside a database transaction. */
@Service
@RequiredArgsConstructor
public class PosterService {

    private final EventService eventService;
    private final MediaStorageService mediaStorage;

    public EventResponse upload(Long eventId, MultipartFile file) {
        eventService.requireExists(eventId);
        String url = mediaStorage.storePoster(eventId, file);
        return eventService.updatePoster(eventId, url);
    }
}

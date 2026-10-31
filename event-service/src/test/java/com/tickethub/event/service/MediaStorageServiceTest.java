package com.tickethub.event.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tickethub.event.common.exception.BusinessRuleException;
import com.tickethub.event.config.MediaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Upload rules are checked before any call to object storage, so no storage is needed here. */
class MediaStorageServiceTest {

    private MediaStorageService service(long maxBytes) {
        MediaProperties p = new MediaProperties();
        p.setMaxSizeBytes(maxBytes);
        return new MediaStorageService(p);
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", new byte[0]);
        assertThrows(BusinessRuleException.class, () -> service(1024).storePoster(1L, file));
    }

    @Test
    void nonImageIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "p.txt", "text/plain", "hello".getBytes());
        assertThrows(BusinessRuleException.class, () -> service(1024).storePoster(1L, file));
    }

    @Test
    void oversizedImageIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "p.png", "image/png", new byte[20]);
        assertThrows(BusinessRuleException.class, () -> service(10).storePoster(1L, file));
    }
}

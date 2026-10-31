package com.tickethub.event.enums;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusTransitionTest {

    @Test
    void eventFollowsDraftPublishedCancelled() {
        assertTrue(EventStatus.DRAFT.canTransitionTo(EventStatus.PUBLISHED));
        assertTrue(EventStatus.PUBLISHED.canTransitionTo(EventStatus.CANCELLED));
        assertFalse(EventStatus.DRAFT.canTransitionTo(EventStatus.CANCELLED));
        assertFalse(EventStatus.PUBLISHED.canTransitionTo(EventStatus.DRAFT));
        assertFalse(EventStatus.CANCELLED.canTransitionTo(EventStatus.PUBLISHED));
    }

    @Test
    void showFollowsLifecycle() {
        assertTrue(ShowStatus.SCHEDULED.canTransitionTo(ShowStatus.OPEN));
        assertTrue(ShowStatus.OPEN.canTransitionTo(ShowStatus.SOLD_OUT));
        assertTrue(ShowStatus.OPEN.canTransitionTo(ShowStatus.COMPLETED));
        assertTrue(ShowStatus.OPEN.canTransitionTo(ShowStatus.CANCELLED));
        assertFalse(ShowStatus.SCHEDULED.canTransitionTo(ShowStatus.COMPLETED));
        assertFalse(ShowStatus.CANCELLED.canTransitionTo(ShowStatus.OPEN));
        assertFalse(ShowStatus.COMPLETED.canTransitionTo(ShowStatus.OPEN));
    }
}

package com.tickethub.event.enums;

/** DRAFT -> PUBLISHED -> CANCELLED (ST-48). */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED;

    public boolean canTransitionTo(EventStatus target) {
        switch (this) {
            case DRAFT:
                return target == PUBLISHED;
            case PUBLISHED:
                return target == CANCELLED;
            default:
                return false;
        }
    }
}

package com.tickethub.event.enums;

/** SCHEDULED -> OPEN -> SOLD_OUT / CANCELLED / COMPLETED (ST-48). */
public enum ShowStatus {
    SCHEDULED,
    OPEN,
    SOLD_OUT,
    CANCELLED,
    COMPLETED;

    public boolean canTransitionTo(ShowStatus target) {
        switch (this) {
            case SCHEDULED:
                return target == OPEN || target == CANCELLED;
            case OPEN:
                return target == SOLD_OUT || target == CANCELLED || target == COMPLETED;
            case SOLD_OUT:
                // seats can be released again by expiry, so a sold-out show may re-open
                return target == OPEN || target == CANCELLED || target == COMPLETED;
            default:
                return false;
        }
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == COMPLETED;
    }
}

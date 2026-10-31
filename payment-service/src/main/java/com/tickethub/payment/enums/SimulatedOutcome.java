package com.tickethub.payment.enums;

/** Controls the simulated provider for tests and demos (ST-54, ST-16). */
public enum SimulatedOutcome {
    SUCCESS,
    FAILURE,
    PENDING_WEBHOOK,
    TIMEOUT
}

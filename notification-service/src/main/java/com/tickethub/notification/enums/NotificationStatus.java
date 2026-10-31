package com.tickethub.notification.enums;

/**
 * Life of one notification row.
 *
 * <pre>
 *   PENDING --send ok--------------------------> SENT
 *      |
 *      +--send fails (provider down)-----------> RETRYING  (Kafka retry topics will redeliver, S10)
 *                                                    |
 *                                                    +--retries exhausted--> DEAD  (also parked in dead_letters)
 *
 *   REPLAYED : created by a replay consumer (S12). The record was rebuilt from history,
 *              but NO email was sent.
 *   FAILED   : legacy Phase-1 value, kept so old rows still load.
 * </pre>
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    RETRYING,
    DEAD,
    REPLAYED,
    FAILED
}

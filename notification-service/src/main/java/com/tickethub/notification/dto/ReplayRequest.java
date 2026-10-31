package com.tickethub.notification.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Optional body for POST /notifications/dead-letters/{id}/replay : the corrected JSON to send. */
@Data
@NoArgsConstructor
public class ReplayRequest {
    private String payload;
}

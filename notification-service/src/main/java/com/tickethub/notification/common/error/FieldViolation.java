package com.tickethub.notification.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class FieldViolation {
    private final String field;
    private final String message;
}

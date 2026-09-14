package cz.swi.parking.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param rule    which business rule rejected the request, or {@code VALIDATION} / {@code NOT_FOUND}
 * @param details field-level messages, empty unless bean validation failed
 */
public record ErrorResponse(String rule, String message, List<String> details, OffsetDateTime timestamp) {

    public static ErrorResponse of(String rule, String message) {
        return new ErrorResponse(rule, message, List.of(), OffsetDateTime.now());
    }

    public static ErrorResponse of(String rule, String message, List<String> details) {
        return new ErrorResponse(rule, message, details, OffsetDateTime.now());
    }
}

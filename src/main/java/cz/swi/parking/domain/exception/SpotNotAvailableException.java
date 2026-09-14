package cz.swi.parking.domain.exception;

import java.util.UUID;

/** The common overlap rule was violated: the spot is already held for part of the window. */
public class SpotNotAvailableException extends BusinessRuleViolationException {

    public SpotNotAvailableException(UUID spotId, String spotCode) {
        super("NO_OVERLAP",
                "Spot %s (%s) already has a confirmed reservation overlapping the requested window"
                        .formatted(spotCode, spotId));
    }
}

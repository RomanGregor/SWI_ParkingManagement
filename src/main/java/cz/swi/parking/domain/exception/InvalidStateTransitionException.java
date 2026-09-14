package cz.swi.parking.domain.exception;

import cz.swi.parking.domain.ReservationStatus;
import java.util.UUID;

/** Someone tried an impossible move in the reservation lifecycle, e.g. confirming a cancelled one. */
public class InvalidStateTransitionException extends BusinessRuleViolationException {

    public InvalidStateTransitionException(UUID reservationId, ReservationStatus from, ReservationStatus to) {
        super("STATE_TRANSITION",
                "Reservation %s cannot move from %s to %s".formatted(reservationId, from, to));
    }
}

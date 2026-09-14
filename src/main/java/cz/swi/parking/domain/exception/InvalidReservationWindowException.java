package cz.swi.parking.domain.exception;

/** The requested time window is not usable (backwards, in the past, or too long). */
public class InvalidReservationWindowException extends BusinessRuleViolationException {

    public InvalidReservationWindowException(String message) {
        super("RESERVATION_WINDOW", message);
    }
}

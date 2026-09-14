package cz.swi.parking.domain.exception;

/** The domain-specific rule was violated: vehicle or driver does not fit the spot type. */
public class IncompatibleSpotException extends BusinessRuleViolationException {

    public IncompatibleSpotException(String message) {
        super("SPOT_VEHICLE_COMPATIBILITY", message);
    }
}

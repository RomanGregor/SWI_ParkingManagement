package cz.swi.parking.domain.exception;

/** A request was well-formed but breaks a rule of the car park domain. Maps to HTTP 409. */
public class BusinessRuleViolationException extends RuntimeException {

    private final String rule;

    public BusinessRuleViolationException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }
}

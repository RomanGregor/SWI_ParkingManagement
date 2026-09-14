package cz.swi.parking.domain.exception;

/** A referenced entity does not exist. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super("%s %s not found".formatted(entity, id));
    }
}

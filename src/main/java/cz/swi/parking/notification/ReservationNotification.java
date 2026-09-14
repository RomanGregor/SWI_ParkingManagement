package cz.swi.parking.notification;

import cz.swi.parking.domain.Reservation;
import java.time.OffsetDateTime;
import java.util.UUID;

/** The payload we hand to the Notification Service. Kept separate from the JPA entity on purpose. */
public record ReservationNotification(
        String event,
        UUID reservationId,
        String recipientEmail,
        String spotCode,
        String vehiclePlate,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt) {

    public static ReservationNotification of(String event, Reservation reservation) {
        return new ReservationNotification(
                event,
                reservation.getId(),
                reservation.getUser().getEmail(),
                reservation.getSpot().getCode(),
                reservation.getVehiclePlate(),
                reservation.getStartsAt(),
                reservation.getEndsAt());
    }
}

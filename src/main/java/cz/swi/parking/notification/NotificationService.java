package cz.swi.parking.notification;

import cz.swi.parking.domain.Reservation;

/**
 * Our one external/system boundary: the Notification Service that tells drivers what
 * happened to their reservation.
 *
 * <p>Implementations must be treated as unreliable. The reservation is the source of truth;
 * a failed notification must never roll back a successful state change.
 */
public interface NotificationService {

    void reservationConfirmed(Reservation reservation);

    void reservationCancelled(Reservation reservation);
}

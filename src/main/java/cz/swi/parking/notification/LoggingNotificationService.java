package cz.swi.parking.notification;

import cz.swi.parking.domain.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default stub of the Notification Service: writes the message to the log instead of sending it.
 * Lets the whole system run on a laptop with nothing but a database.
 */
@Service
@ConditionalOnProperty(prefix = "parking.notifications", name = "mode", havingValue = "log", matchIfMissing = true)
public class LoggingNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

    @Override
    public void reservationConfirmed(Reservation reservation) {
        log.info("NOTIFY {}", ReservationNotification.of("RESERVATION_CONFIRMED", reservation));
    }

    @Override
    public void reservationCancelled(Reservation reservation) {
        log.info("NOTIFY {}", ReservationNotification.of("RESERVATION_CANCELLED", reservation));
    }
}

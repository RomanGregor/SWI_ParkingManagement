package cz.swi.parking.notification;

import cz.swi.parking.domain.Reservation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Real Notification Service client. Every call is bounded by an explicit connect and read
 * timeout, because an unresponsive notification service must not hold a database transaction open.
 */
@Service
@ConditionalOnProperty(prefix = "parking.notifications", name = "mode", havingValue = "http")
public class HttpNotificationService implements NotificationService {

    private final RestClient restClient;

    public HttpNotificationService(NotificationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public void reservationConfirmed(Reservation reservation) {
        send(ReservationNotification.of("RESERVATION_CONFIRMED", reservation));
    }

    @Override
    public void reservationCancelled(Reservation reservation) {
        send(ReservationNotification.of("RESERVATION_CANCELLED", reservation));
    }

    private void send(ReservationNotification notification) {
        restClient.post()
                .uri("/notifications")
                .body(notification)
                .retrieve()
                .toBodilessEntity();
    }
}

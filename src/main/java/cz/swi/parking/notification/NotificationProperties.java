package cz.swi.parking.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the Notification Service boundary.
 *
 * @param mode           {@code log} (local stub, the default) or {@code http} (real service)
 * @param baseUrl        base URL of the real service, used when mode is {@code http}
 * @param connectTimeout how long we wait for the TCP connection
 * @param readTimeout    how long we wait for the response
 */
@ConfigurationProperties(prefix = "parking.notifications")
public record NotificationProperties(
        String mode,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public NotificationProperties {
        mode = (mode == null || mode.isBlank()) ? "log" : mode;
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:9090" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
    }
}

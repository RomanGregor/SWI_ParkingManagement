package cz.swi.parking.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The whole domain reads "now" from an injected Clock so that time-dependent rules
 * (no reservations in the past, permit validity) can be tested deterministically.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

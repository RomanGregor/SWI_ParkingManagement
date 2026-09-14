package cz.swi.parking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ParkReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkReservationApplication.class, args);
    }
}

package cz.swi.parking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.SpotType;
import cz.swi.parking.domain.VehicleType;
import cz.swi.parking.domain.exception.InvalidReservationWindowException;
import cz.swi.parking.repo.ParkingSpotRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final OffsetDateTime FROM = OffsetDateTime.of(2026, 3, 2, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime TO = FROM.plusHours(2);

    @Mock
    private ParkingSpotRepository spots;

    @Test
    void hidesSpotsTheVehicleIsNotAllowedToUse() {
        ParkingSpot standard = ParkingSpot.create("P1-A01", "P1", SpotType.STANDARD);
        ParkingSpot charging = ParkingSpot.create("P1-E01", "P1", SpotType.EV_CHARGING);
        ParkingSpot motorcycle = ParkingSpot.create("P1-M01", "P1", SpotType.MOTORCYCLE);
        when(spots.findFreeSpots(FROM, TO)).thenReturn(List.of(standard, charging, motorcycle));

        AvailabilityService service = new AvailabilityService(spots);

        assertThat(service.findFree(FROM, TO, null, VehicleType.COMBUSTION))
                .containsExactly(standard);
        assertThat(service.findFree(FROM, TO, null, VehicleType.ELECTRIC))
                .containsExactly(standard, charging);
        assertThat(service.findFree(FROM, TO, null, VehicleType.MOTORCYCLE))
                .containsExactly(motorcycle);
        assertThat(service.findFree(FROM, TO, null, null))
                .containsExactly(standard, charging, motorcycle);
    }

    @Test
    void rejectsABackwardsWindow() {
        AvailabilityService service = new AvailabilityService(spots);

        assertThatThrownBy(() -> service.findFree(TO, FROM, null, null))
                .isInstanceOf(InvalidReservationWindowException.class);
    }

    @Test
    void delegatesTheTypeFilterToTheDatabase() {
        ParkingSpot charging = ParkingSpot.create("P1-E01", "P1", SpotType.EV_CHARGING);
        when(spots.findFreeSpotsOfType(any(), any(), any())).thenReturn(List.of(charging));

        AvailabilityService service = new AvailabilityService(spots);

        assertThat(service.findFree(FROM, TO, SpotType.EV_CHARGING, null)).containsExactly(charging);
    }
}

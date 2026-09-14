package cz.swi.parking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cz.swi.parking.domain.AppUser;
import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.SpotType;
import cz.swi.parking.domain.VehicleType;
import cz.swi.parking.domain.exception.InvalidReservationWindowException;
import cz.swi.parking.repo.ParkingSpotRepository;
import java.time.LocalDate;
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

        assertThat(service.findFree(FROM, TO, null, VehicleType.COMBUSTION, null))
                .containsExactly(standard);
        assertThat(service.findFree(FROM, TO, null, VehicleType.ELECTRIC, null))
                .containsExactly(standard, charging);
        assertThat(service.findFree(FROM, TO, null, VehicleType.MOTORCYCLE, null))
                .containsExactly(motorcycle);
        assertThat(service.findFree(FROM, TO, null, null, null))
                .containsExactly(standard, charging, motorcycle);
    }

    @Test
    void rejectsABackwardsWindow() {
        AvailabilityService service = new AvailabilityService(spots);

        assertThatThrownBy(() -> service.findFree(TO, FROM, null, null, null))
                .isInstanceOf(InvalidReservationWindowException.class);
    }

    @Test
    void delegatesTheTypeFilterToTheDatabase() {
        ParkingSpot charging = ParkingSpot.create("P1-E01", "P1", SpotType.EV_CHARGING);
        when(spots.findFreeSpotsOfType(any(), any(), any())).thenReturn(List.of(charging));

        AvailabilityService service = new AvailabilityService(spots);

        assertThat(service.findFree(FROM, TO, SpotType.EV_CHARGING, null, null)).containsExactly(charging);
    }

    @Test
    void hidesAccessibleSpotsFromDriversWithoutAValidPermit() {
        ParkingSpot standard = ParkingSpot.create("P1-A01", "P1", SpotType.STANDARD);
        ParkingSpot accessible = ParkingSpot.create("P1-H01", "P1", SpotType.ACCESSIBLE);
        when(spots.findFreeSpots(FROM, TO)).thenReturn(List.of(standard, accessible));

        AvailabilityService service = new AvailabilityService(spots);
        AppUser withoutPermit = AppUser.driver("driver@example.edu", "Demo Driver");
        AppUser withPermit = AppUser.driver("permit@example.edu", "Permit Holder");
        withPermit.grantAccessibilityPermit(LocalDate.of(2030, 12, 31));
        AppUser expired = AppUser.driver("expired@example.edu", "Expired Permit");
        expired.grantAccessibilityPermit(FROM.toLocalDate().minusDays(1));

        assertThat(service.findFree(FROM, TO, null, null, withoutPermit)).containsExactly(standard);
        assertThat(service.findFree(FROM, TO, null, null, expired)).containsExactly(standard);
        assertThat(service.findFree(FROM, TO, null, null, withPermit)).containsExactly(standard, accessible);
        // No driver given: we cannot judge the permit, so we do not hide anything.
        assertThat(service.findFree(FROM, TO, null, null, null)).containsExactly(standard, accessible);
    }
}

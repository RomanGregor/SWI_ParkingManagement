package cz.swi.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cz.swi.parking.domain.exception.IncompatibleSpotException;
import cz.swi.parking.domain.exception.InvalidReservationWindowException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReservationPolicyTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.UTC);

    @Nested
    @DisplayName("reservation window")
    class Window {

        @Test
        void acceptsAWindowThatStartsBeforeItEnds() {
            assertThatCode(() -> ReservationPolicy.validateWindow(NOW.plusHours(1), NOW.plusHours(3), NOW))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsAWindowThatEndsBeforeItStarts() {
            assertThatThrownBy(() -> ReservationPolicy.validateWindow(NOW.plusHours(3), NOW.plusHours(1), NOW))
                    .isInstanceOf(InvalidReservationWindowException.class)
                    .hasMessageContaining("must be strictly before");
        }

        @Test
        void rejectsAZeroLengthWindow() {
            assertThatThrownBy(() -> ReservationPolicy.validateWindow(NOW.plusHours(1), NOW.plusHours(1), NOW))
                    .isInstanceOf(InvalidReservationWindowException.class);
        }

        @Test
        void rejectsAWindowThatIsAlreadyOver() {
            assertThatThrownBy(() -> ReservationPolicy.validateWindow(NOW.minusHours(4), NOW.minusHours(2), NOW))
                    .isInstanceOf(InvalidReservationWindowException.class)
                    .hasMessageContaining("in the past");
        }

        @Test
        void rejectsAWindowLongerThanTwentyFourHours() {
            assertThatThrownBy(() -> ReservationPolicy.validateWindow(NOW, NOW.plusHours(25), NOW))
                    .isInstanceOf(InvalidReservationWindowException.class)
                    .hasMessageContaining("exceeds the maximum");
        }

        @Test
        void acceptsExactlyTwentyFourHours() {
            assertThatCode(() -> ReservationPolicy.validateWindow(NOW, NOW.plusHours(24), NOW))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("domain rule: spot/vehicle compatibility")
    class Compatibility {

        private final AppUser plainDriver = AppUser.driver("driver@example.edu", "Demo Driver");

        @Test
        void combustionCarFitsAStandardSpot() {
            assertThatCode(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.STANDARD), VehicleType.COMBUSTION, plainDriver, NOW))
                    .doesNotThrowAnyException();
        }

        @Test
        void electricCarFitsAChargingSpot() {
            assertThatCode(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.EV_CHARGING), VehicleType.ELECTRIC, plainDriver, NOW))
                    .doesNotThrowAnyException();
        }

        @Test
        void combustionCarMayNotBlockAChargingSpot() {
            assertThatThrownBy(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.EV_CHARGING), VehicleType.COMBUSTION, plainDriver, NOW))
                    .isInstanceOf(IncompatibleSpotException.class)
                    .hasMessageContaining("EV_CHARGING");
        }

        @Test
        void motorcycleMayNotTakeAStandardSpot() {
            assertThatThrownBy(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.STANDARD), VehicleType.MOTORCYCLE, plainDriver, NOW))
                    .isInstanceOf(IncompatibleSpotException.class);
        }

        @Test
        void carMayNotTakeAMotorcycleSpot() {
            assertThatThrownBy(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.MOTORCYCLE), VehicleType.COMBUSTION, plainDriver, NOW))
                    .isInstanceOf(IncompatibleSpotException.class);
        }

        @Test
        void accessibleSpotNeedsAPermit() {
            assertThatThrownBy(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.ACCESSIBLE), VehicleType.COMBUSTION, plainDriver, NOW))
                    .isInstanceOf(IncompatibleSpotException.class)
                    .hasMessageContaining("no permit");
        }

        @Test
        void accessibleSpotAcceptsAValidPermitHolder() {
            AppUser holder = AppUser.driver("permit@example.edu", "Permit Holder");
            holder.grantAccessibilityPermit(LocalDate.of(2030, 12, 31));

            assertThatCode(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.ACCESSIBLE), VehicleType.COMBUSTION, holder, NOW))
                    .doesNotThrowAnyException();
        }

        @Test
        void accessibleSpotRejectsAnExpiredPermit() {
            AppUser holder = AppUser.driver("expired@example.edu", "Expired Permit");
            holder.grantAccessibilityPermit(NOW.toLocalDate().minusDays(1));

            assertThatThrownBy(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.ACCESSIBLE), VehicleType.COMBUSTION, holder, NOW))
                    .isInstanceOf(IncompatibleSpotException.class)
                    .hasMessageContaining("expiring");
        }

        @Test
        void permitIsStillValidOnItsLastDay() {
            AppUser holder = AppUser.driver("lastday@example.edu", "Last Day");
            holder.grantAccessibilityPermit(NOW.toLocalDate());

            assertThatCode(() -> ReservationPolicy.validateCompatibility(
                    spot(SpotType.ACCESSIBLE), VehicleType.COMBUSTION, holder, NOW))
                    .doesNotThrowAnyException();
        }

        @Test
        void everySpotTypeDeclaresWhichVehiclesItAccepts() {
            for (SpotType type : SpotType.values()) {
                assertThat(ReservationPolicy.allowedVehicles(type))
                        .as("allowed vehicles for %s", type)
                        .isNotNull()
                        .isNotEmpty();
            }
        }

        private ParkingSpot spot(SpotType type) {
            return ParkingSpot.create("P1-" + type.name(), "P1", type);
        }
    }
}

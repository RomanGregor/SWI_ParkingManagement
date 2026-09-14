package cz.swi.parking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cz.swi.parking.domain.exception.InvalidStateTransitionException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The state machine: DRAFT -> CONFIRMED -> COMPLETED, with CANCELLED reachable from the first two. */
class ReservationLifecycleTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void aNewReservationIsADraft() {
        assertThat(reservation().getStatus()).isEqualTo(ReservationStatus.DRAFT);
    }

    @Test
    void aDraftCanBeConfirmed() {
        Reservation reservation = reservation();
        reservation.confirm(NOW.plusMinutes(1));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getUpdatedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void aConfirmedReservationCannotBeConfirmedAgain() {
        Reservation reservation = reservation();
        reservation.confirm(NOW);

        assertThatThrownBy(() -> reservation.confirm(NOW))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("CONFIRMED to CONFIRMED");
    }

    @Test
    void aCancelledReservationIsTerminal() {
        Reservation reservation = reservation();
        reservation.cancel(NOW);

        assertThat(reservation.getStatus().isTerminal()).isTrue();
        assertThatThrownBy(() -> reservation.confirm(NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void onlyAConfirmedReservationCanBeCompleted() {
        Reservation draft = reservation();
        assertThatThrownBy(() -> draft.complete(NOW)).isInstanceOf(InvalidStateTransitionException.class);

        Reservation confirmed = reservation();
        confirmed.confirm(NOW);
        confirmed.complete(NOW.plusHours(3));
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    void touchingWindowsDoNotOverlap() {
        Reservation reservation = reservation(); // 09:00 - 11:00

        assertThat(reservation.overlaps(NOW.plusHours(3), NOW.plusHours(5))).isFalse();  // 11:00 - 13:00
        assertThat(reservation.overlaps(NOW.minusHours(2), NOW.plusHours(1))).isFalse(); // 06:00 - 09:00
    }

    @Test
    void partiallyCoveredWindowsOverlap() {
        Reservation reservation = reservation(); // 09:00 - 11:00

        assertThat(reservation.overlaps(NOW.plusHours(2), NOW.plusHours(4))).isTrue();   // 10:00 - 12:00
        assertThat(reservation.overlaps(NOW, NOW.plusHours(8))).isTrue();                // 08:00 - 16:00
        assertThat(reservation.overlaps(NOW.plusMinutes(90), NOW.plusMinutes(100))).isTrue();
    }

    private Reservation reservation() {
        ParkingSpot spot = ParkingSpot.create("P1-A01", "P1", SpotType.STANDARD);
        AppUser user = AppUser.driver("driver@example.edu", "Demo Driver");
        return Reservation.draft(spot, user, "1AB2345", VehicleType.COMBUSTION,
                NOW.plusHours(1), NOW.plusHours(3), NOW);
    }
}

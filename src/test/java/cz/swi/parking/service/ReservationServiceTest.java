package cz.swi.parking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cz.swi.parking.domain.AppUser;
import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.Reservation;
import cz.swi.parking.domain.ReservationStatus;
import cz.swi.parking.domain.SpotType;
import cz.swi.parking.domain.VehicleType;
import cz.swi.parking.domain.exception.BusinessRuleViolationException;
import cz.swi.parking.domain.exception.IncompatibleSpotException;
import cz.swi.parking.domain.exception.NotFoundException;
import cz.swi.parking.domain.exception.SpotNotAvailableException;
import cz.swi.parking.notification.NotificationService;
import cz.swi.parking.repo.AppUserRepository;
import cz.swi.parking.repo.ParkingSpotRepository;
import cz.swi.parking.repo.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-03-02T08:00:00Z");
    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @Mock
    private ReservationRepository reservations;
    @Mock
    private ParkingSpotRepository spots;
    @Mock
    private AppUserRepository users;
    @Mock
    private NotificationService notifications;

    private ReservationService service;

    private ParkingSpot standardSpot;
    private ParkingSpot chargingSpot;
    private AppUser driver;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservations, spots, users, notifications,
                Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));
        standardSpot = ParkingSpot.create("P1-A01", "P1", SpotType.STANDARD);
        chargingSpot = ParkingSpot.create("P1-E01", "P1", SpotType.EV_CHARGING);
        driver = AppUser.driver("driver@example.edu", "Demo Driver");
    }

    @Test
    void createStoresADraftAndNormalisesThePlate() {
        givenSpotAndUser(standardSpot, driver);
        when(reservations.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));

        Reservation created = service.create(command(standardSpot, VehicleType.COMBUSTION, "1ab 2345"));

        assertThat(created.getStatus()).isEqualTo(ReservationStatus.DRAFT);
        assertThat(created.getVehiclePlate()).isEqualTo("1AB 2345");
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        verify(notifications, never()).reservationConfirmed(any());
    }

    @Test
    void createRejectsAVehicleThatDoesNotFitTheSpot() {
        givenSpotAndUser(chargingSpot, driver);

        assertThatThrownBy(() -> service.create(command(chargingSpot, VehicleType.COMBUSTION, "1AB2345")))
                .isInstanceOf(IncompatibleSpotException.class);
        verify(reservations, never()).save(any());
    }

    @Test
    void createRejectsASpotThatIsOutOfService() {
        standardSpot.takeOutOfService();
        givenSpotAndUser(standardSpot, driver);

        assertThatThrownBy(() -> service.create(command(standardSpot, VehicleType.COMBUSTION, "1AB2345")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("OUT_OF_SERVICE");
    }

    @Test
    void createFailsForAnUnknownUser() {
        UUID unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateReservationCommand(
                unknown, standardSpot.getId(), "1AB2345", VehicleType.COMBUSTION,
                NOW.plusHours(1), NOW.plusHours(3))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void confirmMovesTheDraftToConfirmedAndNotifiesTheDriver() {
        Reservation draft = draft(standardSpot, VehicleType.COMBUSTION);
        when(reservations.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(reservations.existsConfirmedOverlap(eq(standardSpot.getId()), any(), any(), eq(draft.getId())))
                .thenReturn(false);
        when(reservations.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));

        Reservation confirmed = service.confirm(draft.getId());

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(notifications).reservationConfirmed(confirmed);
    }

    @Test
    void confirmRejectsAnOverlapWithAnotherConfirmedReservation() {
        Reservation draft = draft(standardSpot, VehicleType.COMBUSTION);
        when(reservations.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(reservations.existsConfirmedOverlap(eq(standardSpot.getId()), any(), any(), eq(draft.getId())))
                .thenReturn(true);

        assertThatThrownBy(() -> service.confirm(draft.getId()))
                .isInstanceOf(SpotNotAvailableException.class)
                .hasMessageContaining("P1-A01");

        assertThat(draft.getStatus()).isEqualTo(ReservationStatus.DRAFT);
        verify(reservations, never()).save(any());
        verify(notifications, never()).reservationConfirmed(any());
    }

    @Test
    void confirmSurvivesAnUnreachableNotificationService() {
        Reservation draft = draft(standardSpot, VehicleType.COMBUSTION);
        when(reservations.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(reservations.existsConfirmedOverlap(any(), any(), any(), any())).thenReturn(false);
        when(reservations.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        doThrow(new IllegalStateException("notification service timeout"))
                .when(notifications).reservationConfirmed(any());

        Reservation confirmed = service.confirm(draft.getId());

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void cancelReleasesTheSpotAndNotifiesTheDriver() {
        Reservation draft = draft(standardSpot, VehicleType.COMBUSTION);
        draft.confirm(NOW);
        when(reservations.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(reservations.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));

        Reservation cancelled = service.cancel(draft.getId());

        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(notifications).reservationCancelled(cancelled);
    }

    @Test
    void completeIsRefusedWhileTheWindowIsStillOpen() {
        Reservation draft = draft(standardSpot, VehicleType.COMBUSTION);
        draft.confirm(NOW);
        when(reservations.findById(draft.getId())).thenReturn(Optional.of(draft));

        BusinessRuleViolationException thrown = assertThrows(BusinessRuleViolationException.class,
                () -> service.complete(draft.getId()));
        assertThat(thrown.getRule()).isEqualTo("NOT_FINISHED");
    }

    private void givenSpotAndUser(ParkingSpot spot, AppUser user) {
        when(spots.findById(spot.getId())).thenReturn(Optional.of(spot));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private CreateReservationCommand command(ParkingSpot spot, VehicleType vehicleType, String plate) {
        return new CreateReservationCommand(driver.getId(), spot.getId(), plate, vehicleType,
                NOW.plusHours(1), NOW.plusHours(3));
    }

    private Reservation draft(ParkingSpot spot, VehicleType vehicleType) {
        return Reservation.draft(spot, driver, "1AB2345", vehicleType,
                NOW.plusHours(1), NOW.plusHours(3), NOW);
    }
}

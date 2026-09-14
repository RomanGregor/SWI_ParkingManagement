package cz.swi.parking.service;

import cz.swi.parking.domain.AppUser;
import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.Reservation;
import cz.swi.parking.domain.ReservationPolicy;
import cz.swi.parking.domain.exception.BusinessRuleViolationException;
import cz.swi.parking.domain.exception.NotFoundException;
import cz.swi.parking.domain.exception.SpotNotAvailableException;
import cz.swi.parking.notification.NotificationService;
import cz.swi.parking.repo.AppUserRepository;
import cz.swi.parking.repo.ParkingSpotRepository;
import cz.swi.parking.repo.ReservationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The four core operations of the reservation system, plus the rules that guard them. */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservations;
    private final ParkingSpotRepository spots;
    private final AppUserRepository users;
    private final NotificationService notifications;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations,
                              ParkingSpotRepository spots,
                              AppUserRepository users,
                              NotificationService notifications,
                              Clock clock) {
        this.reservations = reservations;
        this.spots = spots;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Create operation: validates the window and the domain-specific compatibility rule and stores
     * a DRAFT. A draft holds nothing, so it is deliberately <em>not</em> checked against the
     * overlap rule &mdash; that check happens at confirm time, on fresh data.
     */
    @Transactional
    public Reservation create(CreateReservationCommand command) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        AppUser user = users.findById(command.userId())
                .orElseThrow(() -> new NotFoundException("User", command.userId()));
        ParkingSpot spot = spots.findById(command.spotId())
                .orElseThrow(() -> new NotFoundException("Parking spot", command.spotId()));

        if (!spot.isReservable()) {
            throw new BusinessRuleViolationException("SPOT_OUT_OF_SERVICE",
                    "Spot %s is %s and cannot be reserved".formatted(spot.getCode(), spot.getStatus()));
        }
        ReservationPolicy.validateWindow(command.startsAt(), command.endsAt(), now);
        ReservationPolicy.validateCompatibility(spot, command.vehicleType(), user, command.startsAt());

        Reservation reservation = Reservation.draft(spot, user, command.vehiclePlate().toUpperCase(),
                command.vehicleType(), command.startsAt(), command.endsAt(), now);
        return reservations.save(reservation);
    }

    /**
     * Confirm operation: DRAFT to CONFIRMED. This is where the common rule bites &mdash; the spot
     * must have no other confirmed reservation overlapping the window.
     */
    @Transactional
    public Reservation confirm(UUID reservationId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Reservation reservation = require(reservationId);

        // Re-check the rules: the world may have moved since the draft was created.
        ReservationPolicy.validateWindow(reservation.getStartsAt(), reservation.getEndsAt(), now);
        ReservationPolicy.validateCompatibility(reservation.getSpot(), reservation.getVehicleType(),
                reservation.getUser(), reservation.getStartsAt());

        boolean clash = reservations.existsConfirmedOverlap(reservation.getSpot().getId(),
                reservation.getStartsAt(), reservation.getEndsAt(), reservation.getId());
        if (clash) {
            throw new SpotNotAvailableException(reservation.getSpot().getId(), reservation.getSpot().getCode());
        }

        reservation.confirm(now);
        Reservation saved = reservations.save(reservation);
        notifySafely(() -> notifications.reservationConfirmed(saved), saved.getId(), "confirmed");
        return saved;
    }

    /** Cancel operation: DRAFT or CONFIRMED to CANCELLED. The spot becomes free again immediately. */
    @Transactional
    public Reservation cancel(UUID reservationId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Reservation reservation = require(reservationId);
        reservation.cancel(now);
        Reservation saved = reservations.save(reservation);
        notifySafely(() -> notifications.reservationCancelled(saved), saved.getId(), "cancelled");
        return saved;
    }

    /** Closes a reservation whose window has elapsed. */
    @Transactional
    public Reservation complete(UUID reservationId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Reservation reservation = require(reservationId);
        if (reservation.getEndsAt().isAfter(now)) {
            throw new BusinessRuleViolationException("NOT_FINISHED",
                    "Reservation %s ends at %s and cannot be completed before that"
                            .formatted(reservationId, reservation.getEndsAt()));
        }
        reservation.complete(now);
        return reservations.save(reservation);
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID reservationId) {
        return require(reservationId);
    }

    @Transactional(readOnly = true)
    public List<Reservation> findByUser(UUID userId) {
        return reservations.findByUserIdOrderByStartsAtDesc(userId);
    }

    private Reservation require(UUID reservationId) {
        return reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation", reservationId));
    }

    /**
     * The Notification Service is outside our trust boundary. If it is down, the reservation has
     * still happened, so we log and carry on instead of failing the operation.
     */
    private void notifySafely(Runnable call, UUID reservationId, String what) {
        try {
            call.run();
        } catch (RuntimeException e) {
            log.warn("Notification Service failed for reservation {} ({}): {}",
                    reservationId, what, e.toString());
        }
    }
}

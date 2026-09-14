package cz.swi.parking.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cz.swi.parking.domain.AppUser;
import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.Reservation;
import cz.swi.parking.domain.ReservationStatus;
import cz.swi.parking.domain.SpotType;
import cz.swi.parking.domain.VehicleType;
import cz.swi.parking.domain.exception.SpotNotAvailableException;
import cz.swi.parking.repo.AppUserRepository;
import cz.swi.parking.repo.ParkingSpotRepository;
import cz.swi.parking.repo.ReservationRepository;
import cz.swi.parking.service.CreateReservationCommand;
import cz.swi.parking.service.ReservationService;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * C01 engineering spike, variant A &mdash; persistence.
 *
 * <p>Question: <em>does a Reservation created through our service really land in PostgreSQL,
 * come back identical, and does the no-overlap rule still hold when the check runs against the
 * database instead of against objects in memory &mdash; including when two drivers confirm at
 * the same moment?</em>
 *
 * <p>Needs a live PostgreSQL. Start everything with {@code ./scripts/spike-persistence.sh}.
 */
@SpringBootTest
@DisplayName("C01 spike A: reservation -> real PostgreSQL -> read back")
class ReservationPersistenceSpikeIT {

    /** Seeded by V2__seed_demo_data.sql. */
    private static final UUID DEMO_DRIVER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private ParkingSpotRepository spots;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("1. a reservation survives a real write/read round trip")
    void reservationSurvivesARoundTrip() {
        ParkingSpot spot = freshSpot(SpotType.STANDARD);
        OffsetDateTime startsAt = OffsetDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MILLIS);
        OffsetDateTime endsAt = startsAt.plusHours(2);

        UUID id = reservationService.create(new CreateReservationCommand(
                DEMO_DRIVER, spot.getId(), "1AB 2345", VehicleType.COMBUSTION, startsAt, endsAt)).getId();

        // Read it back in a brand new transaction: nothing can come from a first-level cache.
        Reservation reloaded = transactionTemplate.execute(status -> {
            Reservation found = reservations.findById(id).orElseThrow();
            found.getSpot().getCode();   // force the lazy associations while the session is open
            found.getUser().getEmail();
            return found;
        });

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.DRAFT);
        assertThat(reloaded.getSpot().getCode()).isEqualTo(spot.getCode());
        assertThat(reloaded.getUser().getId()).isEqualTo(DEMO_DRIVER);
        assertThat(reloaded.getVehiclePlate()).isEqualTo("1AB 2345");
        assertThat(reloaded.getVehicleType()).isEqualTo(VehicleType.COMBUSTION);
        // timestamptz keeps the instant, not the offset the client happened to use
        assertThat(reloaded.getStartsAt().toInstant()).isEqualTo(startsAt.toInstant());
        assertThat(reloaded.getEndsAt().toInstant()).isEqualTo(endsAt.toInstant());

        System.out.printf("[SPIKE] round trip OK: id=%s status=%s spot=%s version=%d%n",
                reloaded.getId(), reloaded.getStatus(), reloaded.getSpot().getCode(), reloaded.getVersion());
    }

    @Test
    @DisplayName("2. the state change DRAFT -> CONFIRMED is durable")
    void confirmIsDurable() {
        ParkingSpot spot = freshSpot(SpotType.EV_CHARGING);
        OffsetDateTime startsAt = OffsetDateTime.now().plusHours(4);

        UUID id = reservationService.create(new CreateReservationCommand(
                DEMO_DRIVER, spot.getId(), "2EV 1111", VehicleType.ELECTRIC,
                startsAt, startsAt.plusHours(1))).getId();
        reservationService.confirm(id);

        ReservationStatus persisted = transactionTemplate.execute(
                status -> reservations.findById(id).orElseThrow().getStatus());

        assertThat(persisted).isEqualTo(ReservationStatus.CONFIRMED);
        System.out.printf("[SPIKE] state change persisted: id=%s status=%s%n", id, persisted);
    }

    @Test
    @DisplayName("3. the overlap rule holds against the database, sequentially")
    void overlapRuleHoldsAgainstTheDatabase() {
        ParkingSpot spot = freshSpot(SpotType.STANDARD);
        OffsetDateTime startsAt = OffsetDateTime.now().plusHours(6);

        UUID first = draft(spot, startsAt, startsAt.plusHours(2));
        reservationService.confirm(first);

        UUID overlapping = draft(spot, startsAt.plusHours(1), startsAt.plusHours(3));
        assertThatThrownBy(() -> reservationService.confirm(overlapping))
                .isInstanceOf(SpotNotAvailableException.class);

        // A window that only touches the confirmed one is fine: intervals are half-open.
        UUID touching = draft(spot, startsAt.plusHours(2), startsAt.plusHours(4));
        assertThatCode(() -> reservationService.confirm(touching)).doesNotThrowAnyException();

        List<Reservation> onSpot = transactionTemplate.execute(
                status -> reservations.findBySpotIdOrderByStartsAtAsc(spot.getId()));
        assertThat(onSpot).hasSize(3);
        assertThat(onSpot.stream().filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)).hasSize(2);

        System.out.printf("[SPIKE] sequential overlap rule OK on spot %s: %d reservations, 2 confirmed, "
                + "1 rejected%n", spot.getCode(), onSpot.size());
    }

    @Test
    @DisplayName("4. OBSERVATION: two simultaneous confirms of the same spot")
    void concurrentConfirmsOnTheSameSpot() throws Exception {
        ParkingSpot spot = freshSpot(SpotType.STANDARD);
        OffsetDateTime startsAt = OffsetDateTime.now().plusHours(10);

        UUID a = draft(spot, startsAt, startsAt.plusHours(2));
        UUID b = draft(spot, startsAt.plusMinutes(30), startsAt.plusHours(3));

        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Void>> runs = new ArrayList<>();
            for (UUID id : List.of(a, b)) {
                Callable<Void> confirmWhenTheLatchOpens = () -> {
                    startLine.await();
                    try {
                        reservationService.confirm(id);
                        accepted.incrementAndGet();
                    } catch (RuntimeException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                };
                runs.add(pool.submit(confirmWhenTheLatchOpens));
            }

            startLine.countDown();
            for (Future<Void> run : runs) {
                run.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        long confirmedOnSpot = transactionTemplate.execute(
                status -> reservations.findBySpotIdOrderByStartsAtAsc(spot.getId()).stream()
                        .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                        .count());

        // This test records what the current design really does under concurrency; it does not
        // claim the behaviour is correct. Timing decides whether the race is hit, so the only
        // safe assertion is that at least one driver got the spot. The number printed below is
        // the actual finding of the spike and is quoted in docs/evidence-and-evolution.md.
        assertThat(accepted.get()).isGreaterThanOrEqualTo(1);
        System.out.printf("[SPIKE] concurrent confirms on spot %s: accepted=%d rejected=%d "
                        + "-> OVERLAPPING CONFIRMED ROWS IN DB = %d (1 = rule held, 2 = double booking)%n",
                spot.getCode(), accepted.get(), rejected.get(), confirmedOnSpot);
    }

    private ParkingSpot freshSpot(SpotType type) {
        AppUser driver = users.findById(DEMO_DRIVER).orElseThrow();
        assertThat(driver.getEmail()).isEqualTo("driver@example.edu"); // proves V2 seed ran
        return spots.save(ParkingSpot.create("SPIKE-" + UUID.randomUUID().toString().substring(0, 8), "P1", type));
    }

    private UUID draft(ParkingSpot spot, OffsetDateTime from, OffsetDateTime to) {
        return reservationService.create(new CreateReservationCommand(
                DEMO_DRIVER, spot.getId(), "1AB 2345", VehicleType.COMBUSTION, from, to)).getId();
    }
}

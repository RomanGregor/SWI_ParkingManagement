package cz.swi.parking.repo;

import cz.swi.parking.domain.Reservation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Every read of a reservation brings its spot and its driver with it.
     *
     * <p>We run with {@code open-in-view: false}, so the transaction is closed before a controller
     * turns the entity into a DTO. Without these entity graphs the lazy associations are dead
     * proxies by then and the read fails with a LazyInitializationException; loading them up front
     * also keeps the list endpoints from firing one query per row.
     */
    @Override
    @EntityGraph(attributePaths = {"spot", "user"})
    Optional<Reservation> findById(UUID id);

    /**
     * The common business rule, asked as a question: does any <em>other</em> confirmed reservation
     * of this spot overlap the half-open window [from, to)?
     */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.spot.id = :spotId
              and r.status = cz.swi.parking.domain.ReservationStatus.CONFIRMED
              and r.id <> :excludeReservationId
              and r.startsAt < :to
              and r.endsAt > :from
            """)
    boolean existsConfirmedOverlap(@Param("spotId") UUID spotId,
                                   @Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to,
                                   @Param("excludeReservationId") UUID excludeReservationId);

    @EntityGraph(attributePaths = {"spot", "user"})
    List<Reservation> findByUserIdOrderByStartsAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"spot", "user"})
    List<Reservation> findBySpotIdOrderByStartsAtAsc(UUID spotId);
}

package cz.swi.parking.repo;

import cz.swi.parking.domain.Reservation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

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

    List<Reservation> findByUserIdOrderByStartsAtDesc(UUID userId);

    List<Reservation> findBySpotIdOrderByStartsAtAsc(UUID spotId);
}

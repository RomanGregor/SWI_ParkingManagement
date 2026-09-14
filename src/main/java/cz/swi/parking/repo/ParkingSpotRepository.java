package cz.swi.parking.repo;

import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.SpotType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {

    Optional<ParkingSpot> findByCode(String code);

    /** Active spots with no confirmed reservation overlapping [from, to). */
    @Query("""
            select s from ParkingSpot s
            where s.status = cz.swi.parking.domain.SpotStatus.ACTIVE
              and not exists (
                select 1 from Reservation r
                where r.spot = s
                  and r.status = cz.swi.parking.domain.ReservationStatus.CONFIRMED
                  and r.startsAt < :to
                  and r.endsAt > :from)
            order by s.code
            """)
    List<ParkingSpot> findFreeSpots(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** Same as {@link #findFreeSpots}, restricted to one spot type. */
    @Query("""
            select s from ParkingSpot s
            where s.status = cz.swi.parking.domain.SpotStatus.ACTIVE
              and s.type = :type
              and not exists (
                select 1 from Reservation r
                where r.spot = s
                  and r.status = cz.swi.parking.domain.ReservationStatus.CONFIRMED
                  and r.startsAt < :to
                  and r.endsAt > :from)
            order by s.code
            """)
    List<ParkingSpot> findFreeSpotsOfType(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                          @Param("type") SpotType type);
}

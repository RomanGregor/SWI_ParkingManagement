package cz.swi.parking.service;

import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.ReservationPolicy;
import cz.swi.parking.domain.SpotType;
import cz.swi.parking.domain.VehicleType;
import cz.swi.parking.domain.exception.InvalidReservationWindowException;
import cz.swi.parking.repo.ParkingSpotRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Check-availability operation: which spots are free for a window, optionally for a given vehicle. */
@Service
public class AvailabilityService {

    private final ParkingSpotRepository spots;

    public AvailabilityService(ParkingSpotRepository spots) {
        this.spots = spots;
    }

    /**
     * @param spotType    optional filter on spot type
     * @param vehicleType optional filter: only spots this vehicle is allowed to use
     */
    @Transactional(readOnly = true)
    public List<ParkingSpot> findFree(OffsetDateTime from, OffsetDateTime to, SpotType spotType,
                                      VehicleType vehicleType) {
        if (!from.isBefore(to)) {
            throw new InvalidReservationWindowException(
                    "from (%s) must be strictly before to (%s)".formatted(from, to));
        }
        List<ParkingSpot> free = spotType == null
                ? spots.findFreeSpots(from, to)
                : spots.findFreeSpotsOfType(from, to, spotType);

        if (vehicleType == null) {
            return free;
        }
        // Applying the domain-specific rule to the search means a driver never sees a spot
        // that would be rejected at confirm time.
        return free.stream()
                .filter(spot -> ReservationPolicy.allowedVehicles(spot.getType()).contains(vehicleType))
                .toList();
    }
}

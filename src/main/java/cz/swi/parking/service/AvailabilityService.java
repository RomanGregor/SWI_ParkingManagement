package cz.swi.parking.service;

import cz.swi.parking.domain.AppUser;
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
     * @param requester   optional: the driver asking. When given, spots they could never confirm
     *                    (accessible spots without a valid permit) are left out of the answer.
     */
    @Transactional(readOnly = true)
    public List<ParkingSpot> findFree(OffsetDateTime from, OffsetDateTime to, SpotType spotType,
                                      VehicleType vehicleType, AppUser requester) {
        if (!from.isBefore(to)) {
            throw new InvalidReservationWindowException(
                    "from (%s) must be strictly before to (%s)".formatted(from, to));
        }
        List<ParkingSpot> free = spotType == null
                ? spots.findFreeSpots(from, to)
                : spots.findFreeSpotsOfType(from, to, spotType);

        // Applying the domain-specific rule to the search means a driver never sees a spot that
        // would be rejected at confirm time. Both halves of the rule are applied: which vehicles
        // the spot accepts, and the permit an accessible spot demands.
        return free.stream()
                .filter(spot -> vehicleType == null
                        || ReservationPolicy.allowedVehicles(spot.getType()).contains(vehicleType))
                .filter(spot -> requester == null || mayUse(requester, spot, from))
                .toList();
    }

    private boolean mayUse(AppUser requester, ParkingSpot spot, OffsetDateTime from) {
        return spot.getType() != SpotType.ACCESSIBLE
                || requester.hasValidAccessibilityPermitOn(from.toLocalDate());
    }
}

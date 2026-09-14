package cz.swi.parking.domain;

import cz.swi.parking.domain.exception.IncompatibleSpotException;
import cz.swi.parking.domain.exception.InvalidReservationWindowException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * All reservation rules that do not need the database, in one place and free of Spring.
 *
 * <p>Two rules live here:
 * <ul>
 *   <li>window sanity (ordering, not in the past, bounded length);</li>
 *   <li><b>spot/vehicle compatibility</b> &mdash; our domain-specific business rule.</li>
 * </ul>
 * The common no-overlap rule needs to see the other reservations, so it lives in
 * {@code ReservationService} where the repository is available.
 */
public final class ReservationPolicy {

    /** A single booking may not block a spot for longer than this. */
    public static final Duration MAX_DURATION = Duration.ofHours(24);

    /** Which vehicles physically fit which spot type. */
    private static final Map<SpotType, Set<VehicleType>> ALLOWED_VEHICLES = Map.of(
            SpotType.STANDARD, EnumSet.of(VehicleType.COMBUSTION, VehicleType.ELECTRIC),
            SpotType.EV_CHARGING, EnumSet.of(VehicleType.ELECTRIC),
            SpotType.ACCESSIBLE, EnumSet.of(VehicleType.COMBUSTION, VehicleType.ELECTRIC),
            SpotType.MOTORCYCLE, EnumSet.of(VehicleType.MOTORCYCLE));

    private ReservationPolicy() {
    }

    /**
     * @throws InvalidReservationWindowException if the window is backwards, already over, or too long
     */
    public static void validateWindow(OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime now) {
        if (!startsAt.isBefore(endsAt)) {
            throw new InvalidReservationWindowException(
                    "startsAt (%s) must be strictly before endsAt (%s)".formatted(startsAt, endsAt));
        }
        if (endsAt.isBefore(now) || endsAt.isEqual(now)) {
            throw new InvalidReservationWindowException(
                    "endsAt (%s) is in the past (now is %s)".formatted(endsAt, now));
        }
        Duration length = Duration.between(startsAt, endsAt);
        if (length.compareTo(MAX_DURATION) > 0) {
            throw new InvalidReservationWindowException(
                    "Reservation length %s exceeds the maximum of %s".formatted(length, MAX_DURATION));
        }
    }

    /**
     * Domain-specific business rule &mdash; <b>spot/vehicle compatibility</b>.
     *
     * <p>A reservation is only valid when the vehicle fits the spot type and, for an
     * ACCESSIBLE spot, the driver holds an accessibility permit that is still valid on the
     * day the reservation starts:
     * <ul>
     *   <li>EV_CHARGING accepts ELECTRIC vehicles only &mdash; a combustion car would block a charger;</li>
     *   <li>MOTORCYCLE spots accept motorcycles only, and motorcycles use nothing else;</li>
     *   <li>ACCESSIBLE additionally requires a valid permit.</li>
     * </ul>
     *
     * @throws IncompatibleSpotException when the vehicle or the driver does not fit the spot
     */
    public static void validateCompatibility(ParkingSpot spot, VehicleType vehicleType, AppUser user,
                                             OffsetDateTime startsAt) {
        Set<VehicleType> allowed = ALLOWED_VEHICLES.get(spot.getType());
        if (!allowed.contains(vehicleType)) {
            throw new IncompatibleSpotException(
                    "Spot %s is of type %s and accepts %s, but the vehicle is %s"
                            .formatted(spot.getCode(), spot.getType(), allowed, vehicleType));
        }
        if (spot.getType() == SpotType.ACCESSIBLE
                && !user.hasValidAccessibilityPermitOn(startsAt.toLocalDate())) {
            throw new IncompatibleSpotException(
                    "Spot %s is an accessible spot and requires an accessibility permit valid on %s; user %s has %s"
                            .formatted(spot.getCode(), startsAt.toLocalDate(), user.getEmail(),
                                    user.getAccessibilityPermitValidUntil() == null
                                            ? "no permit"
                                            : "one expiring " + user.getAccessibilityPermitValidUntil()));
        }
    }

    /** Which vehicle types a given spot type accepts. Exposed for the availability endpoint and tests. */
    public static Set<VehicleType> allowedVehicles(SpotType spotType) {
        return ALLOWED_VEHICLES.get(spotType);
    }
}

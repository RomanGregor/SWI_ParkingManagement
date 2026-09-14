package cz.swi.parking.service;

import cz.swi.parking.domain.VehicleType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Input of {@link ReservationService#create}. */
public record CreateReservationCommand(
        UUID userId,
        UUID spotId,
        String vehiclePlate,
        VehicleType vehicleType,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt) {
}

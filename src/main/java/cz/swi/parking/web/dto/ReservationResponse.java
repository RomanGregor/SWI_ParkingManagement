package cz.swi.parking.web.dto;

import cz.swi.parking.domain.Reservation;
import cz.swi.parking.domain.ReservationStatus;
import cz.swi.parking.domain.VehicleType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID spotId,
        String spotCode,
        UUID userId,
        String vehiclePlate,
        VehicleType vehicleType,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        ReservationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getSpot().getId(),
                r.getSpot().getCode(),
                r.getUser().getId(),
                r.getVehiclePlate(),
                r.getVehicleType(),
                r.getStartsAt(),
                r.getEndsAt(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}

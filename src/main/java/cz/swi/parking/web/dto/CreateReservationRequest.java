package cz.swi.parking.web.dto;

import cz.swi.parking.domain.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Body of {@code POST /api/reservations}. */
public record CreateReservationRequest(
        @NotNull UUID userId,
        @NotNull UUID spotId,
        @NotBlank @Size(max = 16) @Pattern(regexp = "[A-Za-z0-9 -]+", message = "must be a plain licence plate")
        String vehiclePlate,
        @NotNull VehicleType vehicleType,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt) {
}

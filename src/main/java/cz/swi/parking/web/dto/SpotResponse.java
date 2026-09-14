package cz.swi.parking.web.dto;

import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.SpotStatus;
import cz.swi.parking.domain.SpotType;
import java.util.UUID;

public record SpotResponse(UUID id, String code, String zone, SpotType type, SpotStatus status) {

    public static SpotResponse from(ParkingSpot spot) {
        return new SpotResponse(spot.getId(), spot.getCode(), spot.getZone(), spot.getType(), spot.getStatus());
    }
}

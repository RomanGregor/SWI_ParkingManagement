package cz.swi.parking.web;

import cz.swi.parking.domain.SpotType;
import cz.swi.parking.domain.VehicleType;
import cz.swi.parking.repo.ParkingSpotRepository;
import cz.swi.parking.service.AvailabilityService;
import cz.swi.parking.web.dto.SpotResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AvailabilityController {

    private final AvailabilityService availability;
    private final ParkingSpotRepository spots;

    public AvailabilityController(AvailabilityService availability, ParkingSpotRepository spots) {
        this.availability = availability;
        this.spots = spots;
    }

    /** Check availability: {@code GET /api/availability?from=...&to=...&spotType=...&vehicleType=...} */
    @GetMapping("/availability")
    public List<SpotResponse> free(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) SpotType spotType,
            @RequestParam(required = false) VehicleType vehicleType) {
        return availability.findFree(from, to, spotType, vehicleType).stream().map(SpotResponse::from).toList();
    }

    /** Every spot in the car park, whatever its status. Handy when trying the API by hand. */
    @GetMapping("/spots")
    public List<SpotResponse> all() {
        return spots.findAll().stream()
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(SpotResponse::from)
                .toList();
    }
}

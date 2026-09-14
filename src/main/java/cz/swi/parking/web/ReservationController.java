package cz.swi.parking.web;

import cz.swi.parking.service.CreateReservationCommand;
import cz.swi.parking.service.ReservationService;
import cz.swi.parking.web.dto.CreateReservationRequest;
import cz.swi.parking.web.dto.ReservationResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    /** Create: returns 201 with the new reservation ID in the body and in the Location header. */
    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse body = ReservationResponse.from(reservations.create(new CreateReservationCommand(
                request.userId(),
                request.spotId(),
                request.vehiclePlate(),
                request.vehicleType(),
                request.startsAt(),
                request.endsAt())));
        return ResponseEntity.created(URI.create("/api/reservations/" + body.id())).body(body);
    }

    @PostMapping("/{id}/confirm")
    public ReservationResponse confirm(@PathVariable UUID id) {
        return ReservationResponse.from(reservations.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return ReservationResponse.from(reservations.cancel(id));
    }

    @PostMapping("/{id}/complete")
    public ReservationResponse complete(@PathVariable UUID id) {
        return ReservationResponse.from(reservations.complete(id));
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable UUID id) {
        return ReservationResponse.from(reservations.get(id));
    }

    @GetMapping
    public List<ReservationResponse> byUser(@RequestParam UUID userId) {
        return reservations.findByUser(userId).stream().map(ReservationResponse::from).toList();
    }
}

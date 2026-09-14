package cz.swi.parking.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import cz.swi.parking.domain.ParkingSpot;
import cz.swi.parking.domain.SpotType;
import cz.swi.parking.repo.ParkingSpotRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The CP1 walking skeleton, checked over real HTTP against a real database:
 * POST /reservations -> validate -> persist -> return reservation ID -> read it back.
 *
 * <p>Needs a live PostgreSQL; run with {@code ./scripts/integration-tests.sh}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Reservation API end to end")
class ReservationApiIT {

    private static final UUID DEMO_DRIVER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ParkingSpotRepository spots;

    @Test
    @DisplayName("create -> confirm -> read back -> cancel, all over HTTP")
    void theWalkingSkeletonRuns() {
        ParkingSpot spot = freshSpot();
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);

        ResponseEntity<JsonNode> created = http.postForEntity("/api/reservations",
                body(spot, startsAt, startsAt.plusHours(2)), JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status").asText()).isEqualTo("DRAFT");
        String id = created.getBody().get("id").asText();
        assertThat(created.getHeaders().getLocation()).hasToString("/api/reservations/" + id);

        // Regression guard: the response is built after the transaction closed, so the reservation's
        // spot and driver must have been fetched with it. Without the entity graphs this is a 500.
        ResponseEntity<JsonNode> fetched = http.getForEntity("/api/reservations/" + id, JsonNode.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("spotCode").asText()).isEqualTo(spot.getCode());
        assertThat(fetched.getBody().get("userId").asText()).isEqualTo(DEMO_DRIVER.toString());

        ResponseEntity<JsonNode> confirmed = http.postForEntity(
                "/api/reservations/" + id + "/confirm", null, JsonNode.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().get("status").asText()).isEqualTo("CONFIRMED");

        ResponseEntity<JsonNode> mine = http.getForEntity(
                "/api/reservations?userId=" + DEMO_DRIVER, JsonNode.class);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody().findValuesAsText("id")).contains(id);

        ResponseEntity<JsonNode> cancelled = http.postForEntity(
                "/api/reservations/" + id + "/cancel", null, JsonNode.class);
        assertThat(cancelled.getBody().get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("the common rule is reported as 409 NO_OVERLAP")
    void overlappingConfirmIsRejected() {
        ParkingSpot spot = freshSpot();
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withNano(0);

        String first = create(spot, startsAt, startsAt.plusHours(2));
        assertThat(http.postForEntity("/api/reservations/" + first + "/confirm", null, JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        String overlapping = create(spot, startsAt.plusHours(1), startsAt.plusHours(3));
        ResponseEntity<JsonNode> refused = http.postForEntity(
                "/api/reservations/" + overlapping + "/confirm", null, JsonNode.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("rule").asText()).isEqualTo("NO_OVERLAP");

        // Half-open windows: merely touching the confirmed one is fine.
        String touching = create(spot, startsAt.plusHours(2), startsAt.plusHours(4));
        assertThat(http.postForEntity("/api/reservations/" + touching + "/confirm", null, JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("the domain rule is reported as 409 SPOT_VEHICLE_COMPATIBILITY")
    void aCombustionCarCannotTakeACharger() {
        ParkingSpot charger = spots.save(ParkingSpot.create(
                "IT-" + UUID.randomUUID().toString().substring(0, 8), "P1", SpotType.EV_CHARGING));
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).withNano(0);

        ResponseEntity<JsonNode> refused = http.postForEntity("/api/reservations",
                body(charger, startsAt, startsAt.plusHours(1)), JsonNode.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("rule").asText()).isEqualTo("SPOT_VEHICLE_COMPATIBILITY");
    }

    @Test
    @DisplayName("an unknown reservation is a 404, a malformed body a 400")
    void failuresAreReportedPredictably() {
        assertThat(http.getForEntity("/api/reservations/" + UUID.randomUUID(), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> invalid = http.postForEntity("/api/reservations",
                Map.of("userId", DEMO_DRIVER.toString()), JsonNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().get("rule").asText()).isEqualTo("VALIDATION");
    }

    private ParkingSpot freshSpot() {
        return spots.save(ParkingSpot.create(
                "IT-" + UUID.randomUUID().toString().substring(0, 8), "P1", SpotType.STANDARD));
    }

    private String create(ParkingSpot spot, OffsetDateTime from, OffsetDateTime to) {
        ResponseEntity<JsonNode> created = http.postForEntity(
                "/api/reservations", body(spot, from, to), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().get("id").asText();
    }

    private Map<String, String> body(ParkingSpot spot, OffsetDateTime from, OffsetDateTime to) {
        return Map.of(
                "userId", DEMO_DRIVER.toString(),
                "spotId", spot.getId().toString(),
                "vehiclePlate", "1AB 2345",
                "vehicleType", "COMBUSTION",
                "startsAt", ISO.format(from),
                "endsAt", ISO.format(to));
    }
}

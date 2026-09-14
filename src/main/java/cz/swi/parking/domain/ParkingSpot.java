package cz.swi.parking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** The reserved resource: one physical parking spot in the car park. */
@Entity
@Table(name = "parking_spot")
public class ParkingSpot {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Human readable label painted on the floor, e.g. "P1-A12". Unique in the car park. */
    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    /** Level or section, e.g. "P1". */
    @Column(name = "zone_code", nullable = false, length = 32)
    private String zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SpotType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SpotStatus status;

    protected ParkingSpot() {
        // for JPA
    }

    public ParkingSpot(UUID id, String code, String zone, SpotType type, SpotStatus status) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.zone = Objects.requireNonNull(zone);
        this.type = Objects.requireNonNull(type);
        this.status = Objects.requireNonNull(status);
    }

    public static ParkingSpot create(String code, String zone, SpotType type) {
        return new ParkingSpot(UUID.randomUUID(), code, zone, type, SpotStatus.ACTIVE);
    }

    public boolean isReservable() {
        return status == SpotStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getZone() {
        return zone;
    }

    public SpotType getType() {
        return type;
    }

    public SpotStatus getStatus() {
        return status;
    }

    public void takeOutOfService() {
        this.status = SpotStatus.OUT_OF_SERVICE;
    }

    public void returnToService() {
        this.status = SpotStatus.ACTIVE;
    }
}

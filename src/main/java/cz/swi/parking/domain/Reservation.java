package cz.swi.parking.domain;

import cz.swi.parking.domain.exception.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** A request to occupy one {@link ParkingSpot} for a half-open time window [startsAt, endsAt). */
@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id", nullable = false)
    private ParkingSpot spot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "vehicle_plate", nullable = false, length = 16)
    private String vehiclePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Optimistic lock, so two concurrent edits of the same row cannot silently overwrite. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Reservation() {
        // for JPA
    }

    private Reservation(UUID id, ParkingSpot spot, AppUser user, String vehiclePlate, VehicleType vehicleType,
                        OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime now) {
        this.id = Objects.requireNonNull(id);
        this.spot = Objects.requireNonNull(spot);
        this.user = Objects.requireNonNull(user);
        this.vehiclePlate = Objects.requireNonNull(vehiclePlate);
        this.vehicleType = Objects.requireNonNull(vehicleType);
        this.startsAt = Objects.requireNonNull(startsAt);
        this.endsAt = Objects.requireNonNull(endsAt);
        this.status = ReservationStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Every reservation starts its life as a DRAFT. */
    public static Reservation draft(ParkingSpot spot, AppUser user, String vehiclePlate, VehicleType vehicleType,
                                    OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime now) {
        return new Reservation(UUID.randomUUID(), spot, user, vehiclePlate, vehicleType, startsAt, endsAt, now);
    }

    public void confirm(OffsetDateTime now) {
        transitionTo(ReservationStatus.CONFIRMED, now);
    }

    public void cancel(OffsetDateTime now) {
        transitionTo(ReservationStatus.CANCELLED, now);
    }

    public void complete(OffsetDateTime now) {
        transitionTo(ReservationStatus.COMPLETED, now);
    }

    private void transitionTo(ReservationStatus target, OffsetDateTime now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(id, status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    /** Half-open interval test: a reservation ending exactly when another starts does not overlap. */
    public boolean overlaps(OffsetDateTime otherStart, OffsetDateTime otherEnd) {
        return startsAt.isBefore(otherEnd) && otherStart.isBefore(endsAt);
    }

    public UUID getId() {
        return id;
    }

    public ParkingSpot getSpot() {
        return spot;
    }

    public AppUser getUser() {
        return user;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}

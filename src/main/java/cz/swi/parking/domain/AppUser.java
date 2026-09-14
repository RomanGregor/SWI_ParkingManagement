package cz.swi.parking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Somebody who can create reservations. Named AppUser because "user" is reserved in SQL. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /**
     * Expiry date of the driver's accessibility permit, or null if they hold none.
     * Used by the spot/vehicle compatibility rule for ACCESSIBLE spots.
     */
    @Column(name = "accessibility_permit_valid_until")
    private LocalDate accessibilityPermitValidUntil;

    protected AppUser() {
        // for JPA
    }

    public AppUser(UUID id, String email, String fullName, UserRole role, LocalDate accessibilityPermitValidUntil) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.fullName = Objects.requireNonNull(fullName);
        this.role = Objects.requireNonNull(role);
        this.accessibilityPermitValidUntil = accessibilityPermitValidUntil;
    }

    public static AppUser driver(String email, String fullName) {
        return new AppUser(UUID.randomUUID(), email, fullName, UserRole.DRIVER, null);
    }

    /** True if the permit exists and has not expired on the given day. */
    public boolean hasValidAccessibilityPermitOn(LocalDate day) {
        return accessibilityPermitValidUntil != null && !accessibilityPermitValidUntil.isBefore(day);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public LocalDate getAccessibilityPermitValidUntil() {
        return accessibilityPermitValidUntil;
    }

    public void grantAccessibilityPermit(LocalDate validUntil) {
        this.accessibilityPermitValidUntil = validUntil;
    }
}

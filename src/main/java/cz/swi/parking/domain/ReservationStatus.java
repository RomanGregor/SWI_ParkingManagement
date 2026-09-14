package cz.swi.parking.domain;

import java.util.Set;

/**
 * Reservation lifecycle.
 *
 * <pre>
 *   DRAFT ──confirm──> CONFIRMED ──complete──> COMPLETED
 *     │                    │
 *     └──cancel──> CANCELLED <──cancel──┘
 * </pre>
 */
public enum ReservationStatus {
    /** Created and validated, but holds no spot yet. Does not block anybody. */
    DRAFT,
    /** Holds the spot for the time window. Only CONFIRMED reservations can collide. */
    CONFIRMED,
    /** Withdrawn by the driver or the operator. Terminal. */
    CANCELLED,
    /** The parking window has elapsed and the spot was released. Terminal. */
    COMPLETED;

    private static final java.util.Map<ReservationStatus, Set<ReservationStatus>> ALLOWED = java.util.Map.of(
            DRAFT, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(CANCELLED, COMPLETED),
            CANCELLED, Set.of(),
            COMPLETED, Set.of());

    public boolean canTransitionTo(ReservationStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}

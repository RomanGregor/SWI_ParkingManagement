package cz.swi.parking.domain;

public enum UserRole {
    /** Books a spot for their own vehicle. */
    DRIVER,
    /** Runs the car park: approves, cancels, takes spots out of service. */
    OPERATOR,
    /** Manages spots and users. */
    ADMIN
}

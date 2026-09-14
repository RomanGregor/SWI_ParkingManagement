package cz.swi.parking.domain;

/** Operational status of a spot. Only ACTIVE spots can be reserved. */
public enum SpotStatus {
    ACTIVE,
    OUT_OF_SERVICE
}

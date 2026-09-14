package cz.swi.parking.domain;

/** Physical type of a parking spot. Drives the spot/vehicle compatibility rule. */
public enum SpotType {
    STANDARD,
    EV_CHARGING,
    ACCESSIBLE,
    MOTORCYCLE
}

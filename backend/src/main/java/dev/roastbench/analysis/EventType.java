package dev.roastbench.analysis;

/**
 * Roast events a roaster marks (or a logger records) during a roast.
 * Event times are expressed in seconds since CHARGE (the moment the beans enter the drum).
 */
public enum EventType {
    CHARGE("Charge"),
    TURNING_POINT("Turning point"),
    DRY_END("Dry end"),
    FIRST_CRACK_START("First crack start"),
    FIRST_CRACK_END("First crack end"),
    SECOND_CRACK_START("Second crack start"),
    SECOND_CRACK_END("Second crack end"),
    DROP("Drop"),
    COOL("Cool end");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

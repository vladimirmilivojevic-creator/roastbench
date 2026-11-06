package dev.roastbench.inventory;

/** Why green coffee entered or left stock. The sign of the quantity is fixed by the type (except ADJUSTMENT). */
public enum MovementType {
    RECEIPT("Received"),
    ROAST_CONSUMPTION("Used in roast"),
    ADJUSTMENT("Adjustment"),
    TRANSFER_IN("Transfer in"),
    TRANSFER_OUT("Transfer out"),
    ROAST_REVERSAL("Roast deleted");

    private final String label;

    MovementType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

package dev.roastbench.inventory;

import dev.roastbench.common.ConflictException;
import java.math.BigDecimal;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String lotCode, BigDecimal available, BigDecimal needed) {
        super("Not enough stock of lot " + lotCode + ": " + available.stripTrailingZeros().toPlainString()
                + " kg available, " + needed.stripTrailingZeros().toPlainString() + " kg needed");
    }
}

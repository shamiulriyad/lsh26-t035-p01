package com.mirpurprint.loadshed.scheduler;

import com.mirpurprint.loadshed.model.Cut;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/**
 * Converts a shop day into fixed-size slots so placement can be done on plain
 * int/boolean arrays instead of repeated LocalTime arithmetic.
 */
public final class Slotizer {

    public static final int SLOT_MINUTES = 15;

    private Slotizer() {
    }

    public static int totalSlots(LocalTime shopOpen, LocalTime shopClose) {
        long minutes = Duration.between(shopOpen, shopClose).toMinutes();
        return (int) (minutes / SLOT_MINUTES);
    }

    /**
     * Boolean mask, one entry per slot, true where grid power is down.
     * Cut boundaries are clamped to the shop day in case a cut starts before
     * opening or runs past closing.
     */
    public static boolean[] cutMask(LocalTime shopOpen, LocalTime shopClose, List<Cut> cuts) {
        int n = totalSlots(shopOpen, shopClose);
        boolean[] mask = new boolean[n];
        for (Cut cut : cuts) {
            int from = Math.max(0, slotOf(shopOpen, cut.start()));
            int to = Math.min(n, slotOf(shopOpen, cut.end()));
            for (int i = from; i < to; i++) {
                mask[i] = true;
            }
        }
        return mask;
    }

    /** Slot index of a time-of-day relative to shop open. Can be negative or beyond the day. */
    public static int slotOf(LocalTime shopOpen, LocalTime t) {
        long minutes = Duration.between(shopOpen, t).toMinutes();
        return (int) Math.floorDiv(minutes, SLOT_MINUTES);
    }
}

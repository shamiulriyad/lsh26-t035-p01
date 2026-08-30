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

    private static final long MINUTES_PER_DAY = 24 * 60;

    /**
     * True when the shop day itself spans past midnight - close isn't after
     * open on the clock, whether because it's an overnight day (e.g.
     * 22:00-06:00) or a full 24h day (close == open). Only in that case do
     * "before open" clock times get interpreted as landing on the next
     * calendar day; on an ordinary same-day shop they just clamp away.
     */
    private static boolean dayWraps(LocalTime shopOpen, LocalTime shopClose) {
        return !shopClose.isAfter(shopOpen);
    }

    /**
     * Minutes from open to close, wrapping past midnight when close isn't
     * after open on the clock - so an overnight day (e.g. 22:00-06:00) spans
     * 8 hours instead of going negative, and a shop open around the clock
     * (close == open) spans the full 24 hours instead of zero.
     */
    private static long spanMinutes(LocalTime shopOpen, LocalTime shopClose) {
        long minutes = Duration.between(shopOpen, shopClose).toMinutes();
        return dayWraps(shopOpen, shopClose) ? minutes + MINUTES_PER_DAY : minutes;
    }

    public static int totalSlots(LocalTime shopOpen, LocalTime shopClose) {
        return (int) (spanMinutes(shopOpen, shopClose) / SLOT_MINUTES);
    }

    /**
     * Boolean mask, one entry per slot, true where grid power is down.
     * Cut boundaries are clamped to the shop day in case a cut starts before
     * opening or runs past closing.
     */
    public static boolean[] cutMask(LocalTime shopOpen, LocalTime shopClose, List<Cut> cuts) {
        int n = totalSlots(shopOpen, shopClose);
        boolean[] mask = new boolean[n];
        boolean wraps = dayWraps(shopOpen, shopClose);
        for (Cut cut : cuts) {
            int from = Math.max(0, slotOf(shopOpen, shopClose, cut.start()));
            int to = slotOf(shopOpen, shopClose, cut.end());
            if (wraps && to <= from) {
                // "end" landed at/before "start" (e.g. a cut running to
                // exactly midnight on a full-24h day maps to slot 0) - wrap
                // it to the far side of the day instead of dropping it.
                to += (int) (MINUTES_PER_DAY / SLOT_MINUTES);
            }
            to = Math.min(n, to);
            for (int i = from; i < to; i++) {
                mask[i] = true;
            }
        }
        return mask;
    }

    /**
     * Slot index of a time-of-day relative to shop open. On a day that wraps
     * past midnight (see {@link #dayWraps}), a clock time "before" open is
     * treated as landing on the next calendar day instead of going negative;
     * on an ordinary same-day shop it's left negative so callers can clamp
     * it away as out-of-range. Either way it can still land beyond the day
     * if it's past close.
     */
    public static int slotOf(LocalTime shopOpen, LocalTime shopClose, LocalTime t) {
        long minutes = Duration.between(shopOpen, t).toMinutes();
        if (minutes < 0 && dayWraps(shopOpen, shopClose)) {
            minutes += MINUTES_PER_DAY;
        }
        return (int) Math.floorDiv(minutes, SLOT_MINUTES);
    }

    /**
     * Number of slots needed to cover a job's duration, rounded up so a
     * length that isn't a multiple of {@link #SLOT_MINUTES} still reserves
     * enough room to fit its full duration instead of being truncated.
     */
    public static int lenSlots(int minutes) {
        return (minutes + SLOT_MINUTES - 1) / SLOT_MINUTES;
    }
}

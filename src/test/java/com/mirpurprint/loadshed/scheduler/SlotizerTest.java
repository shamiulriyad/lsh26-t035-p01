package com.mirpurprint.loadshed.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirpurprint.loadshed.model.Cut;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotizerTest {

    @Test
    void totalSlotsCoversWholeDayAt15MinuteGranularity() {
        int slots = Slotizer.totalSlots(LocalTime.of(9, 0), LocalTime.of(21, 0));
        assertThat(slots).isEqualTo((21 - 9) * 60 / 15);
    }

    @Test
    void cutMaskMarksOnlyTheCutSlots() {
        boolean[] mask = Slotizer.cutMask(
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                List.of(new Cut(LocalTime.of(9, 15), LocalTime.of(9, 45))));

        // 9:00-9:15 free, 9:15-9:45 cut, 9:45-10:00 free -> slots [F, C, C, F]
        assertThat(mask).containsExactly(false, true, true, false);
    }

    @Test
    void cutBoundariesAreClampedToTheShopDay() {
        boolean[] mask = Slotizer.cutMask(
                LocalTime.of(9, 0), LocalTime.of(9, 30),
                List.of(new Cut(LocalTime.of(8, 0), LocalTime.of(9, 15))));

        assertThat(mask).containsExactly(true, false);
    }

    @Test
    void noCutsMeansEveryoneSlotIsFree() {
        boolean[] mask = Slotizer.cutMask(LocalTime.of(9, 0), LocalTime.of(9, 30), List.of());
        assertThat(mask).containsExactly(false, false);
    }

    @Test
    void closeEqualToOpenMeansAFull24HourDay() {
        int slots = Slotizer.totalSlots(LocalTime.of(0, 0), LocalTime.of(0, 0));
        assertThat(slots).isEqualTo(24 * 60 / 15);
    }

    @Test
    void overnightDaySpansPastMidnight() {
        int slots = Slotizer.totalSlots(LocalTime.of(22, 0), LocalTime.of(6, 0));
        assertThat(slots).isEqualTo(8 * 60 / 15);
    }

    @Test
    void cutRunningToExactlyMidnightOnAFull24HourDayIsNotDropped() {
        boolean[] mask = Slotizer.cutMask(
                LocalTime.of(0, 0), LocalTime.of(0, 0),
                List.of(new Cut(LocalTime.of(23, 30), LocalTime.of(0, 0))));

        // Last two 15-min slots of the day (23:30-23:45, 23:45-00:00) are cut.
        assertThat(mask[mask.length - 1]).isTrue();
        assertThat(mask[mask.length - 2]).isTrue();
        assertThat(mask[mask.length - 3]).isFalse();
    }

    @Test
    void cutBeforeOpenOnAnOrdinaryDayIsStillClampedNotWrapped() {
        // A stray early-morning cut on a normal 09:00-21:00 day shouldn't
        // wrap around and swallow the whole day - it should simply have no
        // effect since it falls entirely outside shop hours.
        boolean[] mask = Slotizer.cutMask(
                LocalTime.of(9, 0), LocalTime.of(21, 0),
                List.of(new Cut(LocalTime.of(2, 0), LocalTime.of(3, 0))));

        for (boolean cut : mask) {
            assertThat(cut).isFalse();
        }
    }
}

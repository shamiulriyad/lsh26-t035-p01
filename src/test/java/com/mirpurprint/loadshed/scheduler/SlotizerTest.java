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
}

package com.mirpurprint.loadshed.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirpurprint.loadshed.model.Cut;
import com.mirpurprint.loadshed.model.DayCase;
import com.mirpurprint.loadshed.model.Job;
import com.mirpurprint.loadshed.model.PlacedJob;
import com.mirpurprint.loadshed.model.PowerType;
import com.mirpurprint.loadshed.model.ScheduleResult;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulerServiceTest {

    private final SchedulerService scheduler = new SchedulerService();

    @Test
    void gridJobWithNoCutsIsPlacedAndCostsNoGeneratorTime() {
        DayCase day = new DayCase("t1", LocalTime.of(9, 0), LocalTime.of(11, 0), List.of(),
                List.of(new Job("Large format poster", 60, PowerType.GRID)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.unplaced()).isEmpty();
        assertThat(result.totalGeneratorMinutes()).isZero();
        assertThat(result.placements()).hasSize(1);
    }

    @Test
    void gridJobIsPlacedInTheExactGapBetweenTwoCuts() {
        // 09:00-10:00 cut, 10:00-10:30 free gap, 10:30-11:30 cut
        DayCase day = new DayCase("t2", LocalTime.of(9, 0), LocalTime.of(11, 30),
                List.of(new Cut(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        new Cut(LocalTime.of(10, 30), LocalTime.of(11, 30))),
                List.of(new Job("Wedding card print", 30, PowerType.GRID)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.unplaced()).isEmpty();
        PlacedJob placed = result.placements().get(0);
        assertThat(placed.start()).isEqualTo(LocalTime.of(10, 0));
        assertThat(placed.end()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void gridJobThatCannotDodgeAnyCutIsFlaggedUnplacedWithGridReason() {
        // The only 45-minute gaps in the day are broken up by a cut in the middle.
        DayCase day = new DayCase("t3", LocalTime.of(9, 0), LocalTime.of(11, 0),
                List.of(new Cut(LocalTime.of(9, 45), LocalTime.of(10, 15))),
                List.of(new Job("Large format poster", 90, PowerType.GRID)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.placements()).isEmpty();
        assertThat(result.unplaced()).hasSize(1);
        assertThat(result.unplaced().get(0).reason()).contains("uninterrupted grid power");
    }

    @Test
    void generatorJobPrefersFreeCapacityOverACutWhenBothAreAvailable() {
        // 09:00-10:00 free, 10:00-11:00 cut. A 30-min generator job should land
        // in the free hour and cost nothing, not the cut hour.
        DayCase day = new DayCase("t4", LocalTime.of(9, 0), LocalTime.of(11, 0),
                List.of(new Cut(LocalTime.of(10, 0), LocalTime.of(11, 0))),
                List.of(new Job("Passport photos", 30, PowerType.GENERATOR)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.totalGeneratorMinutes()).isZero();
        assertThat(result.placements().get(0).start()).isBefore(LocalTime.of(10, 0));
    }

    @Test
    void generatorJobForcedIntoACutPaysExactlyTheOverlap() {
        // The whole day is one cut; a 30-min generator job has nowhere else to go.
        DayCase day = new DayCase("t5", LocalTime.of(9, 0), LocalTime.of(9, 30),
                List.of(new Cut(LocalTime.of(9, 0), LocalTime.of(9, 30))),
                List.of(new Job("Invoice print", 30, PowerType.GENERATOR)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.unplaced()).isEmpty();
        assertThat(result.totalGeneratorMinutes()).isEqualTo(30);
        assertThat(result.placements().get(0).generatorMinutes()).isEqualTo(30);
    }

    @Test
    void noneJobNeverContributesGeneratorMinutesRegardlessOfPlacement() {
        DayCase day = new DayCase("t6", LocalTime.of(9, 0), LocalTime.of(9, 30),
                List.of(new Cut(LocalTime.of(9, 0), LocalTime.of(9, 30))),
                List.of(new Job("Spiral binding", 30, PowerType.NONE)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.unplaced()).isEmpty();
        assertThat(result.totalGeneratorMinutes()).isZero();
    }

    @Test
    void jobLongerThanTheEntireShopDayIsUnplacedWithACapacityReason() {
        DayCase day = new DayCase("t7", LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(),
                List.of(new Job("Packing parcels", 120, PowerType.NONE)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.placements()).isEmpty();
        assertThat(result.unplaced().get(0).reason()).contains("only open");
    }

    @Test
    void overSubscribedDayPlacesSomeJobsAndFlagsTheRestWithoutAnyOverlap() {
        // 60 minutes of shop time, 90 minutes of job time requested.
        DayCase day = new DayCase("t8", LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(),
                List.of(new Job("Photocopy 50 pages", 45, PowerType.NONE),
                        new Job("Photocopy 500 pages", 45, PowerType.NONE)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.placements().size() + result.unplaced().size()).isEqualTo(2);
        assertThat(result.placements()).hasSizeLessThanOrEqualTo(1);
        assertNoOverlaps(result.placements());
    }

    @Test
    void wholeDayCutForcesAllGridJobsUnplacedButLeavesGeneratorAndNoneJobsFine() {
        DayCase day = new DayCase("t9", LocalTime.of(9, 0), LocalTime.of(10, 0),
                List.of(new Cut(LocalTime.of(9, 0), LocalTime.of(10, 0))),
                List.of(new Job("A0 banner print", 30, PowerType.GRID),
                        new Job("Passport photos", 30, PowerType.GENERATOR),
                        new Job("Spiral binding", 30, PowerType.NONE)));

        ScheduleResult result = scheduler.schedule(day);

        assertThat(result.unplaced()).hasSize(1);
        assertThat(result.unplaced().get(0).job().power()).isEqualTo(PowerType.GRID);
        assertThat(result.placements()).hasSize(2);
        assertNoOverlaps(result.placements());
    }

    private void assertNoOverlaps(List<PlacedJob> placements) {
        List<PlacedJob> sorted = placements.stream()
                .sorted((a, b) -> a.start().compareTo(b.start()))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).start()).isAfterOrEqualTo(sorted.get(i - 1).end());
        }
    }
}

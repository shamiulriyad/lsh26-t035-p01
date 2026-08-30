package com.mirpurprint.loadshed.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mirpurprint.loadshed.model.Cut;
import com.mirpurprint.loadshed.model.DayCase;
import com.mirpurprint.loadshed.model.Job;
import com.mirpurprint.loadshed.model.PlacedJob;
import com.mirpurprint.loadshed.model.PowerType;
import com.mirpurprint.loadshed.model.ScheduleResult;
import com.mirpurprint.loadshed.model.UnplacedJob;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulerServiceMoveTest {

    private final SchedulerService scheduler = new SchedulerService();

    // Day 09:00-11:00, one cut 10:00-11:00. A generator job "Invoice print" (30m)
    // and a none job "Spiral binding" (30m) both start out in the free hour.
    private DayCase baseDay() {
        return new DayCase("move-test", LocalTime.of(9, 0), LocalTime.of(11, 0),
                List.of(new Cut(LocalTime.of(10, 0), LocalTime.of(11, 0))),
                List.of(new Job("Invoice print", 30, PowerType.GENERATOR),
                        new Job("Spiral binding", 30, PowerType.NONE)));
    }

    private List<PlacedJob> baseInitialPlan(DayCase day) {
        ScheduleResult initial = scheduler.schedule(day);
        assertThat(initial.unplaced()).isEmpty();
        return initial.placements();
    }

    @Test
    void draggingAGeneratorJobIntoACutRecomputesItsCostToTheOverlap() {
        DayCase day = baseDay();
        List<PlacedJob> plan = baseInitialPlan(day);
        // Spiral binding (NONE) already occupies 10:00-10:30 inside the cut by design
        // (it's parked there to preserve free capacity for generator jobs), so the
        // only open slot left inside the cut is 10:30-11:00.

        ScheduleResult moved = scheduler.move(day, plan, List.of(), "Invoice print", LocalTime.of(10, 30));

        PlacedJob invoicePrint = moved.placements().stream()
                .filter(p -> p.job().name().equals("Invoice print"))
                .findFirst().orElseThrow();
        assertThat(invoicePrint.start()).isEqualTo(LocalTime.of(10, 30));
        assertThat(invoicePrint.generatorMinutes()).isEqualTo(30);
        assertThat(moved.totalGeneratorMinutes()).isEqualTo(30);
    }

    @Test
    void draggingBackOutOfTheCutDropsTheCostToZeroAgain() {
        DayCase day = baseDay();
        List<PlacedJob> plan = baseInitialPlan(day);

        ScheduleResult intoCut = scheduler.move(day, plan, List.of(), "Invoice print", LocalTime.of(10, 30));
        ScheduleResult backOut = scheduler.move(day, intoCut.placements(), List.of(), "Invoice print", LocalTime.of(9, 0));

        assertThat(backOut.totalGeneratorMinutes()).isZero();
    }

    @Test
    void movingOneJobDoesNotChangeAnotherJobsPlacement() {
        DayCase day = baseDay();
        List<PlacedJob> plan = baseInitialPlan(day);
        PlacedJob spiralBefore = plan.stream().filter(p -> p.job().name().equals("Spiral binding")).findFirst().orElseThrow();

        ScheduleResult moved = scheduler.move(day, plan, List.of(), "Invoice print", LocalTime.of(10, 30));

        PlacedJob spiralAfter = moved.placements().stream()
                .filter(p -> p.job().name().equals("Spiral binding")).findFirst().orElseThrow();
        assertThat(spiralAfter.start()).isEqualTo(spiralBefore.start());
    }

    @Test
    void unplacedListPassesThroughUnchanged() {
        DayCase day = baseDay();
        List<PlacedJob> plan = baseInitialPlan(day);
        UnplacedJob someUnplacedJob = new UnplacedJob(new Job("Large format poster", 999, PowerType.GRID), "too long");

        ScheduleResult moved = scheduler.move(day, plan, List.of(someUnplacedJob), "Invoice print", LocalTime.of(9, 30));

        assertThat(moved.unplaced()).containsExactly(someUnplacedJob);
    }

    @Test
    void droppingOnTopOfAnotherJobIsRejected() {
        DayCase day = baseDay();
        List<PlacedJob> plan = baseInitialPlan(day);
        PlacedJob spiral = plan.stream().filter(p -> p.job().name().equals("Spiral binding")).findFirst().orElseThrow();

        assertThatThrownBy(() -> scheduler.move(day, plan, List.of(), "Invoice print", spiral.start()))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void draggingAGridJobIntoACutIsRejected() {
        DayCase day = new DayCase("move-grid", LocalTime.of(9, 0), LocalTime.of(11, 0),
                List.of(new Cut(LocalTime.of(10, 0), LocalTime.of(11, 0))),
                List.of(new Job("A0 banner print", 30, PowerType.GRID)));
        List<PlacedJob> plan = baseInitialPlan(day);

        assertThatThrownBy(() -> scheduler.move(day, plan, List.of(), "A0 banner print", LocalTime.of(10, 15)))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("power cut");
    }

    @Test
    void draggingPastShopCloseIsRejected() {
        DayCase day = baseDay();
        List<PlacedJob> plan = baseInitialPlan(day);

        assertThatThrownBy(() -> scheduler.move(day, plan, List.of(), "Invoice print", LocalTime.of(10, 45)))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("shop hours");
    }

    @Test
    void movingAJobNotOnThePlanIsRejected() {
        DayCase day = baseDay();
        List<PlacedJob> plan = baseInitialPlan(day);

        assertThatThrownBy(() -> scheduler.move(day, plan, List.of(), "Nonexistent job", LocalTime.of(9, 0)))
                .isInstanceOf(InvalidMoveException.class);
    }
}

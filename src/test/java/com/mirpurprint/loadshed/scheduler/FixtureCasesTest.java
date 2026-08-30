package com.mirpurprint.loadshed.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mirpurprint.loadshed.model.Cut;
import com.mirpurprint.loadshed.model.DayCase;
import com.mirpurprint.loadshed.model.Job;
import com.mirpurprint.loadshed.model.PlacedJob;
import com.mirpurprint.loadshed.model.ScheduleResult;
import com.mirpurprint.loadshed.model.UnplacedJob;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the scheduler over every case in the public fixture and checks the
 * invariants a correct plan must satisfy, rather than one golden answer per
 * case (the heuristic's exact layout can legitimately vary).
 */
class FixtureCasesTest {

    private final SchedulerService scheduler = new SchedulerService();

    static Stream<DayCase> cases() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        try (InputStream in = FixtureCasesTest.class.getResourceAsStream("/P01_load_shedding_public.json")) {
            FixtureFile fixture = mapper.readValue(in, FixtureFile.class);
            return fixture.cases().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyJobIsAccountedForExactlyOnce(DayCase dayCase) {
        ScheduleResult result = scheduler.schedule(dayCase);

        int placedCount = result.placements().size();
        int unplacedCount = result.unplaced().size();
        assertThat(placedCount + unplacedCount)
                .as("placed + unplaced should equal the number of jobs in " + dayCase.caseId())
                .isEqualTo(dayCase.jobs().size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void noTwoPlacedJobsOverlap(DayCase dayCase) {
        List<PlacedJob> sorted = scheduler.schedule(dayCase).placements().stream()
                .sorted((a, b) -> a.start().compareTo(b.start()))
                .toList();

        for (int i = 1; i < sorted.size(); i++) {
            assertThat(sorted.get(i).start())
                    .as(dayCase.caseId() + ": " + sorted.get(i - 1).job().name()
                            + " should finish before " + sorted.get(i).job().name() + " starts")
                    .isAfterOrEqualTo(sorted.get(i - 1).end());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void gridJobsNeverOverlapAPowerCut(DayCase dayCase) {
        ScheduleResult result = scheduler.schedule(dayCase);

        for (PlacedJob placed : result.placements()) {
            if (placed.job().power() != com.mirpurprint.loadshed.model.PowerType.GRID) {
                continue;
            }
            for (Cut cut : dayCase.cuts()) {
                boolean overlaps = placed.start().isBefore(cut.end()) && cut.start().isBefore(placed.end());
                assertThat(overlaps)
                        .as(dayCase.caseId() + ": grid job " + placed.job().name()
                                + " (" + placed.start() + "-" + placed.end() + ") overlaps cut "
                                + cut.start() + "-" + cut.end())
                        .isFalse();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void placementsStayWithinShopHours(DayCase dayCase) {
        for (PlacedJob placed : scheduler.schedule(dayCase).placements()) {
            assertThat(placed.start()).isAfterOrEqualTo(dayCase.shopOpen());
            assertThat(placed.end()).isBeforeOrEqualTo(dayCase.shopClose());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void reportedGeneratorMinutesMatchesTheSumOfEachPlacement(DayCase dayCase) {
        ScheduleResult result = scheduler.schedule(dayCase);
        int summed = result.placements().stream().mapToInt(PlacedJob::generatorMinutes).sum();
        assertThat(result.totalGeneratorMinutes()).isEqualTo(summed);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyUnplacedJobHasAReason(DayCase dayCase) {
        for (UnplacedJob unplaced : scheduler.schedule(dayCase).unplaced()) {
            assertThat(unplaced.reason()).isNotBlank();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void noJobNameIsDuplicatedBetweenPlacedAndUnplaced(DayCase dayCase) {
        ScheduleResult result = scheduler.schedule(dayCase);
        Set<Job> seen = new HashSet<>();
        for (PlacedJob placed : result.placements()) {
            assertThat(seen.add(placed.job())).isTrue();
        }
        for (UnplacedJob unplaced : result.unplaced()) {
            assertThat(seen.add(unplaced.job())).isTrue();
        }
    }
}

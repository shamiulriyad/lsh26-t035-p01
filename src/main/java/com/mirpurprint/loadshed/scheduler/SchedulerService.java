package com.mirpurprint.loadshed.scheduler;

import com.mirpurprint.loadshed.model.DayCase;
import com.mirpurprint.loadshed.model.Job;
import com.mirpurprint.loadshed.model.PlacedJob;
import com.mirpurprint.loadshed.model.PowerType;
import com.mirpurprint.loadshed.model.ScheduleResult;
import com.mirpurprint.loadshed.model.UnplacedJob;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Places jobs on a single shop-day timeline around power cuts.
 *
 * <p>Algorithm (single-machine scheduling with non-availability windows,
 * built from classical production-scheduling heuristics):
 * <ol>
 *   <li>Slotize the day into 15-minute units, mark which are inside a cut.</li>
 *   <li>Place jobs group by group in order of how constrained they are -
 *       GRID (must dodge cuts entirely) first, then NONE (parked on cut
 *       slots to preserve free capacity), then GENERATOR (placed wherever
 *       it costs the fewest generator minutes). Within a group, jobs are
 *       ordered by {@link SelectionPolicy} since the day is often
 *       over-subscribed and not everything fits.</li>
 *   <li>Best-Fit is used within each placement so large contiguous windows
 *       aren't needlessly fragmented.</li>
 *   <li>A reinsertion local search (an interchange-style pass) then tries to
 *       re-seat each generator job into a cheaper spot freed up by later
 *       placements.</li>
 * </ol>
 */
@Service
public class SchedulerService {

    public enum SelectionPolicy {
        /** Maximizes the number of jobs that fit in an over-subscribed day. */
        SHORTEST_FIRST,
        /** Minimizes fragmentation; prioritizes big jobs while space is open. */
        LONGEST_FIRST
    }

    public ScheduleResult schedule(DayCase dayCase) {
        return schedule(dayCase, SelectionPolicy.SHORTEST_FIRST);
    }

    public ScheduleResult schedule(DayCase dayCase, SelectionPolicy policy) {
        LocalTime open = dayCase.shopOpen();
        int totalSlots = Slotizer.totalSlots(open, dayCase.shopClose());
        boolean[] cutMask = Slotizer.cutMask(open, dayCase.shopClose(), dayCase.cuts());
        boolean[] occupied = new boolean[totalSlots];

        List<Job> ordered = orderJobs(dayCase.jobs(), policy);

        Map<Job, Integer> starts = new LinkedHashMap<>();
        Map<Job, Integer> costs = new LinkedHashMap<>();
        List<UnplacedJob> unplaced = new ArrayList<>();

        for (Job job : ordered) {
            int lenSlots = Slotizer.lenSlots(job.minutes());
            Optional<Placement> placement = findBestPlacement(occupied, cutMask, lenSlots, job.power());
            if (placement.isPresent()) {
                Placement p = placement.get();
                occupy(occupied, p.start(), lenSlots, true);
                starts.put(job, p.start());
                costs.put(job, p.generatorCostSlots());
            } else {
                unplaced.add(new UnplacedJob(job, reasonFor(job, lenSlots, cutMask, totalSlots)));
            }
        }

        improveGeneratorPlacements(ordered, starts, costs, occupied, cutMask);

        List<PlacedJob> placements = starts.keySet().stream()
                .map(job -> toPlacedJob(job, starts.get(job), costs.get(job), open))
                .sorted(Comparator.comparing(PlacedJob::start))
                .toList();

        int totalGeneratorMinutes = costs.values().stream()
                .mapToInt(slots -> slots * Slotizer.SLOT_MINUTES)
                .sum();

        return new ScheduleResult(placements, unplaced, totalGeneratorMinutes);
    }

    /**
     * Drag-to-reschedule: moves exactly one already-placed job to a new start
     * time, keeping every other placement fixed where the client currently
     * has it. Validates the new slot (inside shop hours, not overlapping
     * another job, and not overlapping a cut if the job needs grid power)
     * and recomputes that job's generator cost - and the plan's total -
     * rather than re-running the full auto-placer, so a drag never silently
     * reshuffles jobs the user didn't touch.
     *
     * @throws InvalidMoveException if the new slot is invalid, with a reason
     *         suitable for showing directly to the user.
     */
    public ScheduleResult move(DayCase dayCase, List<PlacedJob> placements, List<UnplacedJob> unplaced,
                                String jobName, LocalTime newStart) {
        LocalTime open = dayCase.shopOpen();
        int totalSlots = Slotizer.totalSlots(open, dayCase.shopClose());
        boolean[] cutMask = Slotizer.cutMask(open, dayCase.shopClose(), dayCase.cuts());

        PlacedJob target = placements.stream()
                .filter(p -> p.job().name().equals(jobName))
                .findFirst()
                .orElseThrow(() -> new InvalidMoveException("\"%s\" isn't on the current plan.".formatted(jobName)));

        int lenSlots = Slotizer.lenSlots(target.job().minutes());
        int newStartSlot = Slotizer.slotOf(open, dayCase.shopClose(), newStart);

        if (newStartSlot < 0 || newStartSlot + lenSlots > totalSlots) {
            throw new InvalidMoveException("That slot runs outside shop hours.");
        }

        boolean[] occupied = new boolean[totalSlots];
        for (PlacedJob other : placements) {
            if (other.job().name().equals(jobName)) {
                continue;
            }
            int otherStart = Slotizer.slotOf(open, dayCase.shopClose(), other.start());
            int otherLen = Slotizer.lenSlots(other.job().minutes());
            occupy(occupied, Math.max(0, otherStart), Math.min(otherLen, totalSlots - Math.max(0, otherStart)), true);
        }

        int cutCount = 0;
        for (int i = newStartSlot; i < newStartSlot + lenSlots; i++) {
            if (occupied[i]) {
                throw new InvalidMoveException(
                        "\"%s\" would overlap another job already on the timeline.".formatted(jobName));
            }
            if (cutMask[i]) {
                cutCount++;
            }
        }
        if (target.job().power() == PowerType.GRID && cutCount > 0) {
            throw new InvalidMoveException(
                    "\"%s\" needs grid power and that slot overlaps a power cut.".formatted(jobName));
        }

        int generatorCostSlots = target.job().power() == PowerType.GENERATOR ? cutCount : 0;
        PlacedJob moved = toPlacedJob(target.job(), newStartSlot, generatorCostSlots, open);

        List<PlacedJob> updated = placements.stream()
                .map(p -> p.job().name().equals(jobName) ? moved : p)
                .sorted(Comparator.comparing(PlacedJob::start))
                .toList();

        int totalGeneratorMinutes = updated.stream().mapToInt(PlacedJob::generatorMinutes).sum();

        return new ScheduleResult(updated, unplaced, totalGeneratorMinutes);
    }

    private List<Job> orderJobs(List<Job> jobs, SelectionPolicy policy) {
        Comparator<Job> byConstraintTightness = Comparator.comparingInt(j -> switch (j.power()) {
            case GRID -> 0;
            case NONE -> 1;
            case GENERATOR -> 2;
        });
        Comparator<Job> byDuration = policy == SelectionPolicy.SHORTEST_FIRST
                ? Comparator.comparingInt(Job::minutes)
                : Comparator.comparingInt(Job::minutes).reversed();
        return jobs.stream()
                .sorted(byConstraintTightness.thenComparing(byDuration))
                .toList();
    }

    /**
     * Scans every valid start slot for a job and picks the one with the
     * lowest cost (generator minutes for GENERATOR jobs, free-slots consumed
     * for NONE jobs so they don't eat into generator-free capacity, always
     * zero for GRID jobs since a cut-touching slot is simply invalid for
     * them). Ties are broken Best-Fit style, by the smallest leftover space
     * in the free run the placement lands in.
     */
    private Optional<Placement> findBestPlacement(boolean[] occupied, boolean[] cutMask, int len, PowerType type) {
        int n = occupied.length;
        if (len <= 0 || len > n) {
            return Optional.empty();
        }

        Integer bestStart = null;
        int bestCost = Integer.MAX_VALUE;
        int bestLeftover = Integer.MAX_VALUE;

        for (int s = 0; s + len <= n; s++) {
            boolean free = true;
            int cutCount = 0;
            for (int i = s; i < s + len; i++) {
                if (occupied[i]) {
                    free = false;
                    break;
                }
                if (cutMask[i]) {
                    cutCount++;
                }
            }
            if (!free) {
                continue;
            }
            if (type == PowerType.GRID && cutCount > 0) {
                continue;
            }

            int cost = switch (type) {
                case GRID -> 0;
                case GENERATOR -> cutCount;
                case NONE -> len - cutCount;
            };

            int leftover = leftoverInRun(occupied, s, len);
            if (cost < bestCost || (cost == bestCost && leftover < bestLeftover)) {
                bestCost = cost;
                bestLeftover = leftover;
                bestStart = s;
            }
        }

        if (bestStart == null) {
            return Optional.empty();
        }

        int generatorCost = type == PowerType.GENERATOR ? bestCost : 0;
        return Optional.of(new Placement(bestStart, generatorCost));
    }

    private int leftoverInRun(boolean[] occupied, int s, int len) {
        int n = occupied.length;
        int runStart = s;
        while (runStart > 0 && !occupied[runStart - 1]) {
            runStart--;
        }
        int runEnd = s + len;
        while (runEnd < n && !occupied[runEnd]) {
            runEnd++;
        }
        return (runEnd - runStart) - len;
    }

    /**
     * Reinsertion local search: repeatedly frees each already-placed
     * generator job and looks for a strictly cheaper spot given everyone
     * else's current position, keeping the move only if it lowers cost.
     * Equivalent in spirit to the classical pairwise-interchange heuristic,
     * simpler to implement correctly for this scale.
     */
    private void improveGeneratorPlacements(List<Job> ordered, Map<Job, Integer> starts, Map<Job, Integer> costs,
                                             boolean[] occupied, boolean[] cutMask) {
        List<Job> generatorJobs = ordered.stream()
                .filter(j -> j.power() == PowerType.GENERATOR && starts.containsKey(j))
                .toList();

        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 20) {
            improved = false;
            for (Job job : generatorJobs) {
                int len = Slotizer.lenSlots(job.minutes());
                int oldStart = starts.get(job);
                int oldCost = costs.get(job);

                occupy(occupied, oldStart, len, false);
                Optional<Placement> better = findBestPlacement(occupied, cutMask, len, PowerType.GENERATOR);

                if (better.isPresent() && better.get().generatorCostSlots() < oldCost) {
                    Placement p = better.get();
                    occupy(occupied, p.start(), len, true);
                    starts.put(job, p.start());
                    costs.put(job, p.generatorCostSlots());
                    improved = true;
                } else {
                    occupy(occupied, oldStart, len, true);
                }
            }
        }
    }

    private void occupy(boolean[] occupied, int start, int len, boolean value) {
        for (int i = start; i < start + len; i++) {
            occupied[i] = value;
        }
    }

    private String reasonFor(Job job, int lenSlots, boolean[] cutMask, int totalSlots) {
        if (lenSlots > totalSlots) {
            return "Needs %d min but the shop is only open %d min."
                    .formatted(job.minutes(), totalSlots * Slotizer.SLOT_MINUTES);
        }
        if (job.power() == PowerType.GRID) {
            int longestGridWindow = longestFreeRun(cutMask);
            if (lenSlots > longestGridWindow) {
                return "Needs %d min of uninterrupted grid power but the longest cut-free window all day is %d min."
                        .formatted(job.minutes(), longestGridWindow * Slotizer.SLOT_MINUTES);
            }
        }
        return "Would fit on its own, but the day filled up before this job could be placed.";
    }

    private int longestFreeRun(boolean[] cutMask) {
        int best = 0;
        int current = 0;
        for (boolean cut : cutMask) {
            if (!cut) {
                current++;
                best = Math.max(best, current);
            } else {
                current = 0;
            }
        }
        return best;
    }

    private PlacedJob toPlacedJob(Job job, int startSlot, int generatorCostSlots, LocalTime open) {
        LocalTime start = open.plusMinutes((long) startSlot * Slotizer.SLOT_MINUTES);
        LocalTime end = start.plusMinutes(job.minutes());
        return new PlacedJob(job, start, end, generatorCostSlots * Slotizer.SLOT_MINUTES);
    }

    private record Placement(int start, int generatorCostSlots) {
    }
}

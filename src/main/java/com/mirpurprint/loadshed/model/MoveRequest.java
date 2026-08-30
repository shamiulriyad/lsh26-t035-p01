package com.mirpurprint.loadshed.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalTime;
import java.util.List;

/**
 * A drag-to-reschedule request: the day's definition, the plan as the client
 * currently has it, which job is being dragged, and where it landed. Only
 * that one job's position changes; every other placement and the unplaced
 * list are carried through unchanged.
 */
public record MoveRequest(
        @JsonProperty("day_case") DayCase dayCase,
        List<PlacedJob> placements,
        List<UnplacedJob> unplaced,
        @JsonProperty("job_name") String jobName,
        @JsonProperty("new_start") @JsonFormat(pattern = "HH:mm") LocalTime newStart
) {
    public MoveRequest {
        placements = placements == null ? List.of() : List.copyOf(placements);
        unplaced = unplaced == null ? List.of() : List.copyOf(unplaced);
    }
}

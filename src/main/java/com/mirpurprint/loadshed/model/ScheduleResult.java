package com.mirpurprint.loadshed.model;

import java.util.List;

public record ScheduleResult(
        List<PlacedJob> placements,
        List<UnplacedJob> unplaced,
        int totalGeneratorMinutes
) {
}

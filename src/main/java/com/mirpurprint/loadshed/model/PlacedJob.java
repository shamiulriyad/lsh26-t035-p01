package com.mirpurprint.loadshed.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

public record PlacedJob(
        Job job,
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime end,
        int generatorMinutes
) {
}

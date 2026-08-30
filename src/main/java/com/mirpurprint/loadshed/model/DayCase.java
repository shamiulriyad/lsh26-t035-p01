package com.mirpurprint.loadshed.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalTime;
import java.util.List;

public record DayCase(
        @JsonProperty("case_id") String caseId,
        @JsonProperty("shop_open") @JsonFormat(pattern = "HH:mm") LocalTime shopOpen,
        @JsonProperty("shop_close") @JsonFormat(pattern = "HH:mm") LocalTime shopClose,
        List<Cut> cuts,
        List<Job> jobs
) {
    public DayCase {
        if (caseId == null || caseId.isBlank()) {
            caseId = "adhoc";
        }
        cuts = cuts == null ? List.of() : List.copyOf(cuts);
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
    }
}

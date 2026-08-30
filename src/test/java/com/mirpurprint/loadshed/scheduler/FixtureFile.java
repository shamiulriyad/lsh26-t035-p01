package com.mirpurprint.loadshed.scheduler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mirpurprint.loadshed.model.DayCase;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record FixtureFile(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("problem_id") String problemId,
        List<DayCase> cases
) {
}

package com.mirpurprint.loadshed.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void schedulingASimpleDayReturnsAValidPlan() throws Exception {
        String body = """
                {
                  "case_id": "smoke-test",
                  "shop_open": "09:00",
                  "shop_close": "12:00",
                  "cuts": [ { "start": "10:00", "end": "10:30" } ],
                  "jobs": [
                    { "name": "A0 banner print", "minutes": 60, "power": "grid" },
                    { "name": "Passport photos", "minutes": 30, "power": "generator" },
                    { "name": "Spiral binding", "minutes": 30, "power": "none" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalGeneratorMinutes", greaterThanOrEqualTo(0)));
    }

    @Test
    void movingAJobToAValidSlotReturnsTheUpdatedPlan() throws Exception {
        String body = """
                {
                  "day_case": {
                    "case_id": "move-smoke",
                    "shop_open": "09:00",
                    "shop_close": "10:00",
                    "cuts": [],
                    "jobs": [ { "name": "A0 banner print", "minutes": 30, "power": "grid" } ]
                  },
                  "placements": [ {
                    "job": { "name": "A0 banner print", "minutes": 30, "power": "GRID" },
                    "start": "09:00", "end": "09:30", "generatorMinutes": 0
                  } ],
                  "unplaced": [],
                  "job_name": "A0 banner print",
                  "new_start": "09:30"
                }
                """;

        mockMvc.perform(post("/api/schedule/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements[0].start", org.hamcrest.Matchers.is("09:30")))
                .andExpect(jsonPath("$.placements[0].end", org.hamcrest.Matchers.is("10:00")));
    }

    @Test
    void movingAJobPastShopCloseIsRejectedWithAReason() throws Exception {
        String body = """
                {
                  "day_case": {
                    "case_id": "move-smoke-2",
                    "shop_open": "09:00",
                    "shop_close": "10:00",
                    "cuts": [],
                    "jobs": [ { "name": "A0 banner print", "minutes": 30, "power": "grid" } ]
                  },
                  "placements": [ {
                    "job": { "name": "A0 banner print", "minutes": 30, "power": "GRID" },
                    "start": "09:00", "end": "09:30", "generatorMinutes": 0
                  } ],
                  "unplaced": [],
                  "job_name": "A0 banner print",
                  "new_start": "09:45"
                }
                """;

        mockMvc.perform(post("/api/schedule/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason", containsString("shop hours")));
    }
}

package com.mirpurprint.loadshed.web;

import com.mirpurprint.loadshed.model.DayCase;
import com.mirpurprint.loadshed.model.MoveRequest;
import com.mirpurprint.loadshed.model.ScheduleResult;
import com.mirpurprint.loadshed.scheduler.InvalidMoveException;
import com.mirpurprint.loadshed.scheduler.SchedulerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final SchedulerService schedulerService;

    public ScheduleController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @PostMapping
    public ScheduleResult schedule(@RequestBody DayCase dayCase) {
        return schedulerService.schedule(dayCase);
    }

    @PostMapping("/move")
    public ScheduleResult move(@RequestBody MoveRequest request) {
        return schedulerService.move(request.dayCase(), request.placements(), request.unplaced(),
                request.jobName(), request.newStart());
    }

    @ExceptionHandler(InvalidMoveException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleInvalidMove(InvalidMoveException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record ErrorResponse(String reason) {
    }
}

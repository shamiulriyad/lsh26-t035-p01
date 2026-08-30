package com.mirpurprint.loadshed.scheduler;

/** Thrown when a drag-to-reschedule move would overlap another job or break a grid job's cut constraint. */
public class InvalidMoveException extends RuntimeException {

    public InvalidMoveException(String message) {
        super(message);
    }
}

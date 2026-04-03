package com.nahuellofeudo.piplates;

/** Thrown when a Pi-Plates board does not acknowledge a command within the expected time. */
public class TimeoutException extends RuntimeException {

    public TimeoutException(String message) {
        super(message);
    }

}

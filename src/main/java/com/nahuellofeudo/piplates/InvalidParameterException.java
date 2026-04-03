package com.nahuellofeudo.piplates;

/** Thrown when a method parameter is outside its valid range. */
public class InvalidParameterException extends RuntimeException {

    public InvalidParameterException(String message) {
        super(message);
    }

}

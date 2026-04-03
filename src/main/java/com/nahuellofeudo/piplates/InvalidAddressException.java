package com.nahuellofeudo.piplates;

/** Thrown when a board address is outside its valid range. */
public class InvalidAddressException extends PiPlateException {

    public InvalidAddressException(String message) {
        super(message);
    }

}

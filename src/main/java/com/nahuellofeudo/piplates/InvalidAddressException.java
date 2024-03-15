package com.nahuellofeudo.piplates;

/**
 * The address is not in the range [0..7]
 * Created by nahuellofeudo on 8/31/16.
 */
public class InvalidAddressException extends PiPlateException {

    public InvalidAddressException(String message) {
        super(message);
    }

}

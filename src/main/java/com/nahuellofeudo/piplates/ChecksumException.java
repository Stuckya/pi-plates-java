package com.nahuellofeudo.piplates;

/** Thrown when an SPI response checksum does not match the expected value. */
public class ChecksumException extends RuntimeException {

    public ChecksumException(String message) {
        super(message);
    }

}

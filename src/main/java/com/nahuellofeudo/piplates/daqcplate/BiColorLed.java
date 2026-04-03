package com.nahuellofeudo.piplates.daqcplate;

/** Selects one of the two LEDs in the DAQCplate's bicolor LED package. */
public enum BiColorLed {
    GREEN(0),
    RED(1);

    private final int value;

    BiColorLed(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

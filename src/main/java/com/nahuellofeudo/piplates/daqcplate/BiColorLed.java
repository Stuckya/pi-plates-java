package com.nahuellofeudo.piplates.daqcplate;

/** Selects one of the two LEDs in the DAQCplate's bicolor LED package. */
public enum BiColorLed {
    RED(0),
    GREEN(1);

    private final int value;

    BiColorLed(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

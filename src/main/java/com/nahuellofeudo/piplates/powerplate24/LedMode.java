package com.nahuellofeudo.piplates.powerplate24;

public enum LedMode {
    ALWAYS_OFF(0),
    BLINK(1),
    ALWAYS_ON(2),
    MANUAL(3);

    private final int value;

    LedMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

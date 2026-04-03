package com.nahuellofeudo.piplates.relayplate;

/** SPI command opcodes shared by RELAYplate and RELAYplate2 boards. */
public enum RelayCommand {
    RELAY_ON(0x10),
    RELAY_OFF(0x11),
    RELAY_TOGGLE(0x12),
    RELAY_ALL(0x13),
    RELAY_STATE(0x14),
    LED_SET(0x60),
    LED_CLEAR(0x61),
    LED_TOGGLE(0x62),
    GET_ID(0x01);

    private final int code;

    RelayCommand(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

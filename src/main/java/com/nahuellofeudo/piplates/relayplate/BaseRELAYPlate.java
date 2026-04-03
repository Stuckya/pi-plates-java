package com.nahuellofeudo.piplates.relayplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.PiPlate;
import com.pi4j.context.Context;

/** Shared implementation for the RELAYplate (7-relay) and RELAYplate2 (8-relay) boards. */
public abstract class BaseRELAYPlate extends PiPlate implements RelayBoard {
    protected BaseRELAYPlate(Context pi4jContext, int address) throws InvalidAddressException {
        super(pi4jContext, address);
    }

    protected BaseRELAYPlate(int address) throws InvalidAddressException {
        super(address);
    }

    /**
     * Turn on the board's LED
     */
    public void setLed() {
        sendCommand(RelayCommand.LED_SET.getCode(), 0, 0);
    }

    /**
     * Turn off the board's LED
     */
    public void clearLed() {
        sendCommand(RelayCommand.LED_CLEAR.getCode(), 0, 0);
    }

    /**
     * Toggle the board's LED
     */
    public void toggleLed() {
        sendCommand(RelayCommand.LED_TOGGLE.getCode(), 0, 0);
    }

    protected abstract void validateRelay(int relay);
}

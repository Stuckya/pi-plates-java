package com.nahuellofeudo.piplates.relayplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.PiPlate;
import com.pi4j.context.Context;

public abstract class BaseRELAYPlate extends PiPlate implements RelayBoard {
    protected BaseRELAYPlate(Context pi4jContext, int address) throws InvalidAddressException {
        super(pi4jContext, address);
        this.address = address;
    }

    protected BaseRELAYPlate(int address) throws InvalidAddressException {
        super(address);
        this.address = address;
    }

    /**
     * Turn on the board's LED
     */
    public void setLed() {
        ppCommand(RelayCommand.LED_SET.getCode(), 0, 0, 0);
    }

    /**
     * Turn off the board's LED
     */
    public void clearLed() {
        ppCommand(RelayCommand.LED_CLEAR.getCode(), 0, 0, 0);
    }

    /**
     * Toggle the board's LED
     */
    public void toggleLed() {
        ppCommand(RelayCommand.LED_TOGGLE.getCode(), 0, 0, 0);
    }

    protected abstract void validateRelay(int relay);
}
package com.nahuellofeudo.piplates.relayplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.PiPlate;

public abstract class BaseRELAYPlate extends PiPlate implements RelayBoard {
    protected BaseRELAYPlate(int address) throws InvalidAddressException {
        super(address);
        this.address = address;
    }

    /**
     * Turn on the board's LED
     */
    public void setLED() {
        ppCommand(RelayCommand.LED_SET.getCode(), 0, 0, 0);
    }

    /**
     * Turn off the board's LED
     */
    public void clearLED() {
        ppCommand(RelayCommand.LED_CLEAR.getCode(), 0, 0, 0);
    }

    /**
     * Toggle the board's LED
     */
    public void toggleLED() {
        ppCommand(RelayCommand.LED_TOGGLE.getCode(), 0, 0, 0);
    }

    /**
     * Reads and returns the board's identifier string
     * @return a string ID read from the board
     */
    public String getId() {
        int ID_LENGTH = 20;
        return ppCommand(RelayCommand.GET_ID.getCode(), 0, 0, ID_LENGTH)
                .map(resp -> {
                    int length = ID_LENGTH;
                    for (int x = 0; x < ID_LENGTH; x++) {
                        if (resp[0] == 0) {
                            length = x;
                            break;
                        }
                    }
                    return new String(resp, 0, length);
                })
                .orElse("");
    }

    protected abstract void validateRelay(int relay);
}
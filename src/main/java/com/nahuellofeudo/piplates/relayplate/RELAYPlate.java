package com.nahuellofeudo.piplates.relayplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.pi4j.context.Context;


public class RELAYPlate extends BaseRELAYPlate {
    public RELAYPlate(Context pi4jContext, int address) throws InvalidAddressException {
        super(pi4jContext, address);
    }

    public RELAYPlate(int address) throws InvalidAddressException {
        super(address);
    }

    /**
     * Return the base address of the RELAYPlate
     */
    @Override
    protected int getBaseAddress() {
        return 24;
    }

    /**
     * Turns on (activates) a relay
     * @param relay the relay to activate, in the range [1..7]
     */
    @Override
    public void relayOn(int relay) {
        validateRelay(relay);
        ppCommand(RelayCommand.RELAY_ON.getCode(), relay, 0, 0);
    }

    /**
     * Turns off (deactivates) a relay
     * @param relay the relay to deactivate, in the range [1..7]
     */
    @Override
    public void relayOff(int relay) {
        validateRelay(relay);
        ppCommand(RelayCommand.RELAY_OFF.getCode(), relay, 0, 0);
    }

    /**
     * Toggles a relay
     * @param relay the relay to toggle, in the range [1..7]
     */
    @Override
    public void relayToggle(int relay) {
        validateRelay(relay);
        ppCommand(RelayCommand.RELAY_TOGGLE.getCode(), relay, 0, 0);
    }

    /**
     * Sets the state of all 7 relays in a single operation
     * @param relays the bit-field with the new states of all relays encoded in bits 0..6
     */
    @Override
    public void relayAll(int relays) {
        if (relays < 0 || relays > 127)
            throw new InvalidParameterException("Relays parameter must be between 0 and 127");
        ppCommand(RelayCommand.RELAY_ALL.getCode(), relays, 0, 0);
    }

    /**
     * Reads and returns the state of all 7 relays
     * @return the state of all relays encoded in bits 0..6
     */
    @Override
    public int relayState() {
        // TODO: Handle empty better
        byte [] resp = ppCommand(RelayCommand.RELAY_STATE.getCode(), 0, 0, 1).orElse(new byte[0]);
        return resp[0];
    }

    @Override
    protected void validateRelay(int relay) {
        if (relay < 1 || relay > 7)
            throw new InvalidParameterException("Relay parameter must be in the range [1..7]");
    }
}

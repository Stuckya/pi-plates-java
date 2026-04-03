package com.nahuellofeudo.piplates.relayplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.pi4j.context.Context;


/**
 * Interface to the Pi-Plates RELAYplate — a 7-relay board.
 * Each relay is an SPST switch rated at 1A / 24VDC.
 * Up to 8 boards can be stacked (addresses 0-7).
 *
 * @see <a href="https://pi-plates.com/relayplate-users-guide/">RELAYplate User's Guide</a>
 */
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
        sendCommand(RelayCommand.RELAY_ON.getCode(), relay, 0);
    }

    /**
     * Turns off (deactivates) a relay
     * @param relay the relay to deactivate, in the range [1..7]
     */
    @Override
    public void relayOff(int relay) {
        validateRelay(relay);
        sendCommand(RelayCommand.RELAY_OFF.getCode(), relay, 0);
    }

    /**
     * Toggles a relay
     * @param relay the relay to toggle, in the range [1..7]
     */
    @Override
    public void relayToggle(int relay) {
        validateRelay(relay);
        sendCommand(RelayCommand.RELAY_TOGGLE.getCode(), relay, 0);
    }

    /**
     * Sets the state of all 7 relays in a single operation
     * @param relays the bit-field with the new states of all relays encoded in bits 0..6
     */
    @Override
    public void relayAll(int relays) {
        if (relays < 0 || relays > 127) {
            throw new InvalidParameterException("Relays parameter must be between 0 and 127");
        }
        sendCommand(RelayCommand.RELAY_ALL.getCode(), relays, 0);
    }

    /**
     * Reads and returns the state of all 7 relays
     * @return the state of all relays encoded in bits 0..6
     */
    @Override
    public int relayState() {
        return unsigned(sendQuery(RelayCommand.RELAY_STATE.getCode(), 0, 0, 1)[0]);
    }

    /**
     * Resets the board to power-on state
     */
    public void reset() throws InterruptedException {
        sendCommand(0x0F, 0, 0);
        Thread.sleep(100);
    }

    @Override
    protected void validateRelay(int relay) {
        if (relay < 1 || relay > 7) {
            throw new InvalidParameterException("Relay parameter must be in the range [1..7]");
        }
    }
}

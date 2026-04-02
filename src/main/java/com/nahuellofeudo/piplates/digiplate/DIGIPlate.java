package com.nahuellofeudo.piplates.digiplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlate;
import com.nahuellofeudo.piplates.daqcplate.InterruptEdge;
import com.pi4j.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DIGIPlate extends PiPlate {
    static Logger log = LoggerFactory.getLogger(DIGIPlate.class);

    public DIGIPlate(Context pi4jContext, int address) throws InvalidAddressException {
        super(pi4jContext, address);
        this.address = address;
    }

    public DIGIPlate(int address) throws InvalidAddressException {
        super(address);
        this.address = address;
    }

    /**
     * Return the base address of the DIGIPlate
     */
    @Override
    protected int getBaseAddress() {
        return 88;
    }

    /* --------- Event functions --------- */

    // TODO: Refactor, reused by DAQCPlate
    // Note: bit-1 here and not with DAQCPlate
    public void enableDINEvent(int bit, InterruptEdge edge) throws InvalidParameterException {
        validateDINBit(bit-1);
        switch (edge) {
            case FALLING_EDGE:
                ppCommand(0x21, bit-1, 0, 0);
                break;
            case RISING_EDGE:
                ppCommand(0x22, bit-1, 0, 0);
                break;
            case BOTH_EDGES:
                ppCommand(0x23, bit-1, 0, 0);
                break;
        }
    }

    // TODO: Refactor, reused by DAQCPlate
    // Note: bit-1 here and not with DAQCPlate
    public void disableDINEvent(int bit) throws InvalidParameterException {
        validateDINBit(bit-1);
        ppCommand(0x24, bit-1, 0, 0);
    }

    /**
     * Enables SRQ pin on DIGIplate, will pull down on pin when event occurs
     */
    public void eventEnable() {
        ppCommand(0x04, 0, 0, 0);
    }

    /**
     * Disables SRQ pin on DIGIplate
     */
    public void eventDisable() {
        ppCommand(0x05, 0, 0, 0);
    }

    public boolean check4Events() {
        return isServiceRequest();
    }

    public int getEvents() {
        // TODO: Handle empty better
        byte [] resp = ppCommand(0x06, 0, 0, 2).orElse(new byte[0]);

        return ((resp[0]<<8) + resp[1]);
    }

    private void validateDINBit (int bit) throws InvalidParameterException {
        if (bit < 0 || bit > 1) throw new InvalidParameterException("Bit number parameter must be in the range [0..7]");
    }

}

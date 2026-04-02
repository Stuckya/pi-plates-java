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

    @Override
    protected int getBaseAddress() {
        return 88;
    }

    /* --------- Digital Input Functions --------- */

    /**
     * Reads the value of a single digital input
     * @param bit the input channel (1-8)
     * @return true if input is high, false if low
     */
    public boolean getDINBit(int bit) throws InvalidParameterException {
        validateDINBit(bit - 1);
        var resp = ppCommand(0x20, bit - 1, 0, 1).orElse(new byte[0]);
        return resp[0] > 0;
    }

    /**
     * Reads all 8 digital inputs
     * @return 8-bit value with all input states
     */
    public int getDINAll() {
        var resp = ppCommand(0x25, 0, 0, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }

    /* --------- Event Functions --------- */

    public void enableDINEvent(int bit, InterruptEdge edge) throws InvalidParameterException {
        validateDINBit(bit - 1);
        switch (edge) {
            case FALLING_EDGE:
                ppCommand(0x21, bit - 1, 0, 0);
                break;
            case RISING_EDGE:
                ppCommand(0x22, bit - 1, 0, 0);
                break;
            case BOTH_EDGES:
                ppCommand(0x23, bit - 1, 0, 0);
                break;
        }
    }

    public void disableDINEvent(int bit) throws InvalidParameterException {
        validateDINBit(bit - 1);
        ppCommand(0x24, bit - 1, 0, 0);
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

    /**
     * Reads the event register. Clears the interrupt line and the register.
     * @return 16-bit event flags (upper 8 = falling, lower 8 = rising)
     */
    public int getEvents() {
        byte[] resp = ppCommand(0x06, 0, 0, 2).orElse(new byte[0]);
        return ((resp[0] << 8) + resp[1]);
    }

    /* --------- Frequency Functions --------- */

    /**
     * Measures the frequency on a digital input channel
     * @param channel the input channel (1-6)
     * @return frequency in Hz, rounded to 3 decimal places
     */
    public double getFREQ(int channel) throws InvalidParameterException {
        validateFREQChannel(channel);
        // Get upper 16 bits
        var upper = ppCommand(0xC0, 0, channel - 1, 2).orElse(new byte[0]);
        long counts = ((long) unsigned(upper[0]) << 24) + ((long) unsigned(upper[1]) << 16);
        // Get lower 16 bits
        var lower = ppCommand(0xC0, 1, channel - 1, 2).orElse(new byte[0]);
        counts += ((long) unsigned(lower[0]) << 8) + unsigned(lower[1]);

        if (counts > 0) {
            return Math.round(1000000.0 / counts * 1000.0) / 1000.0;
        }
        return 0;
    }

    /**
     * Measures the frequency on all 6 input channels
     * @return array of 6 frequency values in Hz
     */
    public double[] getFREQAll() throws InvalidParameterException {
        double[] freqs = new double[6];
        for (int i = 0; i < 6; i++) {
            freqs[i] = getFREQ(i + 1);
        }
        return freqs;
    }

    /* --------- LED Functions --------- */

    public void setLED() {
        ppCommand(0x60, 0, 0, 0);
    }

    public void clrLED() {
        ppCommand(0x61, 0, 0, 0);
    }

    public void toggleLED() {
        ppCommand(0x62, 0, 0, 0);
    }

    /* --------- System Functions --------- */

    /**
     * Resets the board to power-on state
     */
    public void reset() throws InterruptedException {
        ppCommand(0x0F, 0, 0, 0);
        Thread.sleep(100);
    }

    public void setINT() {
        ppCommand(0xF4, 0, 0, 0);
    }

    public void clrINT() {
        ppCommand(0xF5, 0, 0, 0);
    }

    /* --------- Validation --------- */

    private void validateDINBit(int bit) throws InvalidParameterException {
        if (bit < 0 || bit > 7) throw new InvalidParameterException("Bit number parameter must be in the range [0..7]");
    }

    private void validateFREQChannel(int channel) throws InvalidParameterException {
        if (channel < 1 || channel > 6) throw new InvalidParameterException("Frequency input channel must be in the range [1..6]");
    }
}

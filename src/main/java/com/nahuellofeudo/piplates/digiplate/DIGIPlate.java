package com.nahuellofeudo.piplates.digiplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlate;
import com.nahuellofeudo.piplates.daqcplate.InterruptEdge;
import com.pi4j.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface to the Pi-Plates DIGIplate — an 8-channel digital input board with
 * edge-triggered event detection, frequency measurement (channels 1-6), and
 * interrupt signalling via GPIO22. Up to 8 boards can be stacked (addresses 0-7).
 *
 * <p>Input channels are 1-indexed (1-8) and internally converted to 0-indexed
 * values for the SPI protocol.
 *
 * @see <a href="https://pi-plates.com/digiplate-users-guide/">DIGIplate User's Guide</a>
 */
public class DIGIPlate extends PiPlate {
    static Logger log = LoggerFactory.getLogger(DIGIPlate.class);

    public DIGIPlate(Context pi4jContext, int address) throws InvalidAddressException {
        super(pi4jContext, address);
    }

    public DIGIPlate(int address) throws InvalidAddressException {
        super(address);
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
    public boolean getDigitalInput(int bit) throws InvalidParameterException {
        validateDINBit(bit - 1);
        var resp = sendQuery(0x20, bit - 1, 0, 1);
        return resp[0] > 0;
    }

    /**
     * Reads all 8 digital inputs
     * @return 8-bit value with all input states
     */
    public int getDigitalInputAll() {
        var resp = sendQuery(0x25, 0, 0, 1);
        return unsigned(resp[0]);
    }

    /* --------- Event Functions --------- */

    /**
     * Configures edge-triggered event detection on a digital input channel.
     * Use {@link #enableEvents()} to activate signalling after configuring.
     *
     * @param bit  input channel (1-8)
     * @param edge which edge(s) trigger the event
     */
    public void enableDigitalInputEvent(int bit, InterruptEdge edge) throws InvalidParameterException {
        validateDINBit(bit - 1);
        switch (edge) {
            case FALLING_EDGE:
                sendCommand(0x21, bit - 1, 0);
                break;
            case RISING_EDGE:
                sendCommand(0x22, bit - 1, 0);
                break;
            case BOTH_EDGES:
                sendCommand(0x23, bit - 1, 0);
                break;
        }
    }

    /**
     * Removes event monitoring from the specified input channel.
     *
     * @param bit input channel (1-8)
     */
    public void disableDigitalInputEvent(int bit) throws InvalidParameterException {
        validateDINBit(bit - 1);
        sendCommand(0x24, bit - 1, 0);
    }

    /**
     * Enables SRQ pin on DIGIplate, will pull down on pin when event occurs
     */
    public void enableEvents() {
        sendCommand(0x04, 0, 0);
    }

    /**
     * Disables SRQ pin on DIGIplate
     */
    public void disableEvents() {
        sendCommand(0x05, 0, 0);
    }

    /**
     * Checks whether the event pin (GPIO22) is currently asserted.
     *
     * @return {@code true} if an event has occurred since last read
     */
    public boolean checkForEvents() {
        return isServiceRequest();
    }

    /**
     * Reads the event register. Clears the interrupt line and the register.
     * @return 16-bit event flags (upper 8 = falling, lower 8 = rising)
     */
    public int getEventFlags() {
        byte[] resp = sendQuery(0x06, 0, 0, 2);
        return (unsigned(resp[0]) << 8) + unsigned(resp[1]);
    }

    /* --------- Frequency Functions --------- */

    /**
     * Measures the frequency on a digital input channel
     * @param channel the input channel (1-6)
     * @return frequency in Hz, rounded to 3 decimal places
     */
    public double getFrequency(int channel) throws InvalidParameterException {
        validateFREQChannel(channel);
        // Get upper 16 bits
        var upper = sendQuery(0xC0, 0, channel - 1, 2);
        long counts = ((long) unsigned(upper[0]) << 24) + ((long) unsigned(upper[1]) << 16);
        // Get lower 16 bits
        var lower = sendQuery(0xC0, 1, channel - 1, 2);
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
    public double[] getFrequencyAll() throws InvalidParameterException {
        double[] freqs = new double[6];
        for (int i = 0; i < 6; i++) {
            freqs[i] = getFrequency(i + 1);
        }
        return freqs;
    }

    /* --------- LED Functions --------- */

    /** Turns on the green indicator LED. */
    public void setLed() {
        sendCommand(0x60, 0, 0);
    }

    /** Turns off the green indicator LED. */
    public void clearLed() {
        sendCommand(0x61, 0, 0);
    }

    /** Toggles the green indicator LED. */
    public void toggleLed() {
        sendCommand(0x62, 0, 0);
    }

    /* --------- System Functions --------- */

    /**
     * Restores the board to its power-on configuration.
     *
     * @throws InterruptedException if the post-reset delay is interrupted
     */
    public void reset() throws InterruptedException {
        sendCommand(0x0F, 0, 0);
        Thread.sleep(100);
    }

    /** Asserts the SRQ interrupt line (for testing or manual signalling). */
    public void setInterrupt() {
        sendCommand(0xF4, 0, 0);
    }

    /** De-asserts the SRQ interrupt line. */
    public void clearInterrupt() {
        sendCommand(0xF5, 0, 0);
    }

    /* --------- Validation --------- */

    private void validateDINBit(int bit) throws InvalidParameterException {
        validateRange(bit, 0, 7, "Digital input bit");
    }

    private void validateFREQChannel(int channel) throws InvalidParameterException {
        validateRange(channel, 1, 6, "Frequency input channel");
    }
}

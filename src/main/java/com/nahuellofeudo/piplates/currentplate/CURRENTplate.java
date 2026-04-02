package com.nahuellofeudo.piplates.currentplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlate;
import com.pi4j.context.Context;

/**
 * Interface to the Pi-Plates CURRENTplate - an 8-channel 4-20mA current loop measurement board.
 * Reference: Pi-Plates Python library v11 (CURRENTplate)
 */
public class CURRENTplate extends PiPlate {

    public CURRENTplate(Context pi4jContext, int address) throws InvalidAddressException {
        super(pi4jContext, address);
    }

    public CURRENTplate(int address) throws InvalidAddressException {
        super(address);
    }

    @Override
    protected int getBaseAddress() {
        return 80; // 0x50
    }

    /* --------- Current Measurement Functions --------- */

    /**
     * Reads the current on a single channel.
     * Equivalent to Python library's {@code getI(addr, channel)}.
     * @param channel the input channel (1-8)
     * @return current in milliamps (0-24mA range), rounded to 4 decimal places
     */
    public double getCurrent(int channel) throws InvalidParameterException {
        validateChannel(channel);
        var resp = ppCommand(0x30, channel - 1, 0, 2).orElse(new byte[0]);
        double value = 256.0 * unsigned(resp[0]) + unsigned(resp[1]);
        return Math.round(value * 24.0 / 65536.0 * 10000.0) / 10000.0;
    }

    /**
     * Reads the current on all 8 channels simultaneously.
     * Equivalent to Python library's {@code getIall(addr)}.
     * @return array of 8 current values in milliamps
     */
    public double[] getCurrentAll() {
        var resp = ppCommand(0x31, 0, 0, 16).orElse(new byte[0]);
        double[] values = new double[8];
        for (int i = 0; i < 8; i++) {
            double raw = 256.0 * unsigned(resp[2 * i]) + unsigned(resp[2 * i + 1]);
            values[i] = Math.round(raw * 24.0 / 65536.0 * 10000.0) / 10000.0;
        }
        return values;
    }

    /* --------- Configuration --------- */

    /**
     * Sets the AC line frequency for measurement filtering
     * @param freq 50 or 60 (Hz)
     */
    public void setFreq(int freq) throws InvalidParameterException {
        if (freq != 50 && freq != 60) {
            throw new InvalidParameterException("Frequency must be 50 or 60");
        }
        ppCommand(0x3F, freq, 0, 0);
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

    /* --------- Interrupt Functions --------- */

    public void intEnable() {
        ppCommand(0x04, 0, 0, 0);
    }

    public void intDisable() {
        ppCommand(0x05, 0, 0, 0);
    }

    /**
     * Reads the interrupt flags. Clears the register and SRQ signal.
     * @return interrupt flags byte
     */
    public int getINTFlags() {
        var resp = ppCommand(0x06, 0, 0, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }

    public void setINT() {
        ppCommand(0xF4, 0, 0, 0);
    }

    public void clrINT() {
        ppCommand(0xF5, 0, 0, 0);
    }

    /* --------- Calibration Functions --------- */

    /**
     * Reads a byte from calibration flash memory
     * @param ptr memory address (0-255)
     * @return byte value at that address
     */
    public int calGetByte(int ptr) throws InvalidParameterException {
        validateByteRange(ptr, "Calibration pointer");
        var resp = ppCommand(0xFD, 2, ptr, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }

    /**
     * Writes a byte to calibration flash memory
     * @param data byte value to write (0-255)
     */
    public void calPutByte(int data) throws InvalidParameterException {
        validateByteRange(data, "Calibration data");
        ppCommand(0xFD, 1, data, 0);
    }

    /**
     * Erases the calibration flash memory block
     */
    public void calEraseBlock() {
        ppCommand(0xFD, 0, 0, 0);
    }

    /* --------- System Functions --------- */

    /**
     * Resets the board to power-on state
     */
    public void reset() throws InterruptedException {
        ppCommand(0x0F, 0, 0, 0);
        Thread.sleep(1100);
    }

    /* --------- Validation --------- */

    private void validateChannel(int channel) throws InvalidParameterException {
        if (channel < 1 || channel > 8) {
            throw new InvalidParameterException("4-20mA input channel must be in the range [1..8]");
        }
    }

    private void validateByteRange(int value, String name) throws InvalidParameterException {
        if (value < 0 || value > 255) {
            throw new InvalidParameterException(name + " must be in the range [0..255]");
        }
    }
}

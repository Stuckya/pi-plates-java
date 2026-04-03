package com.nahuellofeudo.piplates.daqcplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlate;
import com.nahuellofeudo.piplates.PiPlateException;
import com.pi4j.context.Context;

/**
 * Interface to the Pi-Plates DAQCplate — a data-acquisition board with
 * 8 analog inputs (10-bit, 0-4.095 V), 8 digital inputs, 7 digital outputs,
 * 2 PWM/DAC channels, temperature sensing (DS18B20), and ultrasonic range
 * measurement (HC-SR04). Up to 8 boards can be stacked (addresses 0-7).
 *
 * @see <a href="https://pi-plates.com/daqc-users-guide/">DAQCplate User's Guide</a>
 */
public class DAQCPlate extends PiPlate {

    // VCC supply voltage in volts, used for DAC conversion (matches Python Vcc)
    double vcc;

    public DAQCPlate(Context pi4jContext, int address) throws InvalidAddressException {
        super(pi4jContext, address);
        calibrateVCC();
    }

    public DAQCPlate(int address) throws InvalidAddressException {
        super(address);
        calibrateVCC();
    }

    private void calibrateVCC() {
        try {
            // getAnalogInput(8) returns rawAdc * 8 (×4 all channels, ×2 for ch8).
            // This is intentional: rawAdc*8 * 4.096/4096 == rawAdc * 4.096/512,
            // matching the Python library's getADC(8) which returns rawAdc * 4.096/1024 * 2.
            vcc = getAnalogInput(8) * 4.096 / 4096.0;
        } catch (Exception e) {
            vcc = 0;
        }
    }

    /**
     * Base address for the DAQCplate
     */
    @Override
    protected int getBaseAddress() {
        return 8;
    }

    /* --------- Interrupt Control Functions --------- */
    /**
     * Enables global interrupt signalling on GPIO22. The board will pull the
     * line low when a configured digital input or switch event occurs.
     */
    public void interruptEnable() {
        sendCommand(0x04, 0, 0);
    }

    /**
     * Disables and clears all interrupts on the board.
     */
    public void interruptDisable() {
        sendCommand(0x05, 0, 0);
    }

    /**
     * Reads the 16-bit interrupt flag register, then clears all flags.
     *
     * @return 16-bit value indicating which interrupt sources have fired
     */
    public int getInterruptFlags() {
        var resp = sendQuery(0x06, 0, 0, 2);
        return (unsigned(resp[0]) << 8) + unsigned(resp[1]);
    }

    /* ---------  Digital Input Functions --------- */
    /**
     * Returns the state of a single digital input.
     *
     * @param bit digital input number (0-7)
     * @return {@code true} if the input is high, {@code false} if low
     */
    public boolean getDigitalInput(int bit) throws InvalidParameterException {
        validateDINBit(bit);
        var resp = sendQuery(0x20, bit, 0, 1);
        return (resp[0] > 0);
    }

    /**
     * Returns the state of all 8 digital inputs as a bitmask.
     *
     * @return 8-bit value where bit 0 = DIN 0, bit 7 = DIN 7
     */
    public int getDigitalInputAll() {
        var resp = sendQuery(0x25, 0, 0, 1);
        return unsigned(resp[0]);
    }

    /**
     * Enables edge-triggered interrupt on a digital input. Global interrupts
     * must be enabled with {@link #interruptEnable()} for signalling to occur.
     *
     * @param bit  digital input number (0-7)
     * @param edge which edge(s) trigger the interrupt
     */
    public void enableDigitalInputInterrupt(int bit, InterruptEdge edge) throws InvalidParameterException {
        validateDINBit(bit);
        switch (edge) {
            case FALLING_EDGE:
                sendCommand(0x21, bit, 0);
                break;
            case RISING_EDGE:
                sendCommand(0x22, bit, 0);
                break;
            case BOTH_EDGES:
                sendCommand(0x23, bit, 0);
                break;
        }
    }

    /**
     * Disables the interrupt on the specified digital input.
     *
     * @param bit digital input number (0-7)
     */
    public void disableDigitalInputInterrupt(int bit) throws InvalidParameterException {
        validateDINBit(bit);
        sendCommand(0x24, bit, 0);
    }

    /* --------- Utility functions for peripherals --------- */
    /**
     * Reads temperature from a DS18B20 sensor connected to a digital input.
     * This function takes approximately 1 second to complete while the sensor
     * performs its conversion.
     *
     * @param channel digital input where the DS18B20 is connected (0-7)
     * @param unit    temperature scale to return
     * @return the measured temperature in the requested unit
     * @throws InterruptedException if the sensor conversion delay is interrupted
     */
    public double getTemperature(int channel, TemperatureUnit unit) throws InvalidParameterException, InterruptedException {
        validateRange(channel, 0, 7, "Temperature sensor channel");
        sendCommand(0x70, channel, 0);

        Thread.sleep(1000);

        var resp = sendQuery(0x71, channel, 0, 2);

        long temp = (long) unsigned(resp[0]) * 256 + unsigned(resp[1]);

        if (temp > 0x8000) {
            temp = temp ^ 0xFFFF;
            temp = -(temp + 1);
        }

        double dblTemp = temp / 16.0;

        switch (unit) {
            case CELSIUS:
                break;
            case KELVIN:
                dblTemp += 273;
                break;
            case FAHRENHEIT:
                dblTemp = dblTemp * 1.8 + 32.2; // 32.2 matches the Python pi-plates library (sensor calibration offset)
                break;
        }

        return dblTemp;
    }


    /**
     * Measures distance using an HC-SR04 ultrasonic range sensor connected to a
     * DIN/DOUT pair. Takes approximately 70ms while the sensor performs its measurement.
     *
     * @param channel the DIN/DOUT pair to use (0-6)
     * @param unit    distance unit for the result
     * @return the measured distance in the requested unit
     * @throws PiPlateException     if the sensor returns zero (not connected or fault)
     * @throws InterruptedException if the measurement delay is interrupted
     */
    public double getRange(int channel, DistanceUnit unit) throws PiPlateException, InterruptedException {
        validateRange(channel, 0, 6, "Range sensor channel");

        sendCommand(0x80, channel, 0);
        Thread.sleep(70);
        var resp = sendQuery(0x81, channel, 0, 2);

        long range = (long) unsigned(resp[0]) * 256 + unsigned(resp[1]);
        if (range == 0) throw new PiPlateException("Range sensor error or sensor not present on channel " + channel);

        double dblRange = 0;
        switch (unit) {
            case CENTIMETERS:
                dblRange = range / 58.326;
                break;
            case INCHES:
                dblRange = range / 148.148;
        }
        return dblRange;
    }


    /* --------- ADC Functions --------- */
    /**
     * Reads a scaled ADC count from a single analog input channel. Channels 0-7
     * measure 0-4.095 V; channel 8 reads the supply voltage (Vcc). The returned
     * value is the raw 10-bit reading multiplied by 4 (or 8 for channel 8).
     *
     * <p>Note: unlike the Python {@code getADC} which returns volts, this method
     * returns the scaled integer count.
     *
     * @param channel analog input channel (0-8, where 8 = Vcc reference)
     * @return scaled ADC count
     */
    public int getAnalogInput(int channel) throws InvalidParameterException {
        validateAnalogIn(channel);

        // TODO: This had a longer, 100ms delay. Does it still work?
        var resp = sendQuery(0x30, channel, 0, 2);

        int value = (256 * unsigned(resp[0]) + unsigned(resp[1]));

        value *= 4;

        if (channel == 8) {
            value = value * 2;
        }

        return value;
    }


    /**
     * Reads scaled ADC counts from all 8 analog input channels (0-7).
     * Each value is the raw 10-bit reading multiplied by 4.
     *
     * @return array of 8 scaled ADC counts, one per channel
     */
    public int[] getAnalogInputAll() {
        int [] values = new int [8];

        // TODO: This had a longer, 300ms delay. Does it still work?
        var resp = sendQuery(0x31, 0, 0, 16);

        for (int i = 0; i < 8; i++) {
            values[i] = (256 * unsigned(resp[2 * i]) + unsigned(resp[(2 * i) + 1]));
            values[i] *= 4;
        }

        return values;
    }


    /* --------- PWM and DAC Output Functions --------- */
    /**
     * Sets the duty cycle of a PWM output channel.
     *
     * @param channel PWM channel (0 or 1)
     * @param value   duty cycle (0 = 0%, 1023 = 100%)
     */
    public void setPwm(int channel, int value) throws InvalidParameterException {
        if (value < 0 || value > 1023) throw new InvalidParameterException("ERROR: PWM argument out of range - must be between 0 and 1023");
        if (channel != 0 && channel != 1) throw new InvalidParameterException("Error: PWM channel must be 0 or 1");

        byte hiByte = (byte) (value >> 8);
        byte loByte = (byte) (value - (hiByte << 8));

        sendCommand(0x40+channel, hiByte, loByte);
    }


    /**
     * Returns the current duty cycle setting of a PWM output channel.
     *
     * @param channel PWM channel (0 or 1)
     * @return current duty cycle value (0-1023)
     */
    public int getPwm(int channel) throws InvalidParameterException {
        validatePWMChannel(channel);

        var resp = sendQuery(0x40+channel+2, 0, 0, 2);

        return (unsigned(resp[0]) << 8) + unsigned(resp[1]);
    }


    /**
     * Sets the output voltage of a DAC channel. The actual output is
     * ratiometric to the board's Vcc supply voltage.
     *
     * @param channel DAC channel (0 or 1)
     * @param value   desired output voltage (0.0 to 4.095 V)
     */
    public void setDac(int channel, double value) throws InvalidParameterException {
        if (value < 0 || value > 4.095) throw new InvalidParameterException("ERROR: DAC argument out of range - must be between 0 and 4.095 volts");
        requireCalibratedVcc();

        int dacValue = (int) (value / vcc * 1024);

        this.setPwm(channel, dacValue);
    }


    /**
     * Returns the current DAC output voltage setting.
     *
     * @param channel DAC channel (0 or 1)
     * @return current output voltage in volts
     */
    double getDac(int channel) throws InvalidParameterException {
        requireCalibratedVcc();
        int value = getPwm(channel);

        return value * vcc / 1023;
    }

    private void requireCalibratedVcc() {
        if (vcc == 0) {
            throw new IllegalStateException("VCC not calibrated; DAC operations require a valid VCC reading");
        }
    }


    /* --------- LED Functions --------- */
    /**
     * Turns on one of the LEDs in the bicolor LED package.
     *
     * @param led which LED to turn on (RED = 0, GREEN = 1)
     */
    public void setLed(BiColorLed led) {
        sendCommand(0x60, led.getValue(), 0);
    }


    /**
     * Turns off one of the LEDs in the bicolor LED package.
     *
     * @param led which LED to turn off (RED = 0, GREEN = 1)
     */
    public void clearLed(BiColorLed led) {
        sendCommand(0x61, led.getValue(), 0);
    }


    /**
     * Toggles the state of one of the LEDs in the bicolor LED package.
     *
     * @param led which LED to toggle (RED = 0, GREEN = 1)
     */
    public void toggleLed(BiColorLed led) {
        sendCommand(0x62, led.getValue(), 0);
    }


    /**
     * Returns the current state of one of the LEDs in the bicolor LED package.
     *
     * @param led which LED to query (RED = 0, GREEN = 1)
     * @return 1 if the LED is on, 0 if off
     */
    int getLed(BiColorLed led) {
        return unsigned(sendQuery(0x63, led.getValue(), 0, 1)[0]);
    }


    /* --------- Validation --------- */

    private void validateDINBit(int bit) throws InvalidParameterException {
        validateRange(bit, 0, 7, "Digital input bit");
    }

    private void validateAnalogIn(int analogIn) throws InvalidParameterException {
        validateRange(analogIn, 0, 8, "Analog input channel");
    }

    private void validatePWMChannel(int channel) throws InvalidParameterException {
        validateRange(channel, 0, 1, "PWM channel");
    }
}

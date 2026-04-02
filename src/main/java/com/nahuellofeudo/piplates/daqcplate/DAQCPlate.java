package com.nahuellofeudo.piplates.daqcplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlate;
import com.nahuellofeudo.piplates.PiPlateException;
import com.pi4j.context.Context;

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
     * Enable triggering an interrupt when an ENABLED event occurs
     */
    public void interruptEnable() {
        sendCommand(0x04, 0, 0);
    }

    /**
     * Disable triggering interrupts
     */
    public void interruptDisable() {
        sendCommand(0x05, 0, 0);
    }

    /**
     * Read the interrupt flags
     * @return integer with all the interrupt flags
     */
    public int getInterruptFlags() {
        var resp = sendQuery(0x06, 0, 0, 2);
        return (unsigned(resp[0]) << 8) + unsigned(resp[1]);
    }

    /* ---------  Digital Input Functions --------- */
    /**
     * Returns the value of a specific input bit
     * @param bit the bit number to return
     * @return true if the input bit's state is high, false otherwise.
     * @throws InvalidParameterException
     */
    public boolean getDigitalInput(int bit) throws InvalidParameterException {
        validateDINBit(bit);
        var resp = sendQuery(0x20, bit, 0, 1);
        return (resp[0] > 0);
    }

    /**
     * Returns the value of all digital inputs
     * @return the values of all 8 digital inputs
     */
    public int getDigitalInputAll() {
        var resp = sendQuery(0x25, 0, 0, 1);
        return unsigned(resp[0]);
    }

    /**
     * Enables the triggering of an interrupt on digital input change
     * @param bit the bit (input) to trigger an interrupt on
     * @param edge the edge on which to trigger the interrupt (Rising, Falling or Both)
     * @throws InvalidParameterException
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
     * Disables the interrupt-on-change on the digital input bit
     * @param bit digital input on which to disable interrupt-on-change
     * @throws InvalidParameterException
     */
    public void disableDigitalInputInterrupt(int bit) throws InvalidParameterException {
        validateDINBit(bit);
        sendCommand(0x24, bit, 0);
    }

    /* --------- Utility functions for peripherals --------- */
    /**
     * Reads a temperature from a temperature measurement from a DS18B20 connected to a particular Digital Input channel
     * @param channel the channel to which the DS18B20 is connected, in the range [0..7]
     * @param unit the temperature unit to use (Fahrenheit, Celsius or Kelvin)
     * @return the value of temperature, in the selected unit, as read by a DS18B20
     * @throws InvalidParameterException
     * @throws InterruptedException
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
     * Reads range information from a HC-SR04 ultrasonic range finder connected to a digital input channel
     * @param channel the channel to which the HC-SR04 is connected, in the range [0..6]
     * @param unit the unit of distance to use when returning the range (Centimeters or Inches)
     * @return the range as measured by the HC-SR04 sensor, in the requested units.
     * @throws PiPlateException
     * @throws InterruptedException
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
     * Get the value of an A/D input
     * @param channel A/D channel to read from
     * @return the value returned by the A/D converter
     * @throws InvalidParameterException
     * @throws InvalidAddressException
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
     * Reads the values of all 8 analog inputs
     * @return an array of 8 ints containing the analog values
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
     * Sets a PWM output channel
     * @param channel the channel (0 or 1)
     * @param value the value (0..1023)
     * @throws InvalidParameterException
     */
    public void setPwm(int channel, int value) throws InvalidParameterException {
        if (value < 0 || value > 1023) throw new InvalidParameterException("ERROR: PWM argument out of range - must be between 0 and 1023");
        if (channel != 0 && channel != 1) throw new InvalidParameterException("Error: PWM channel must be 0 or 1");

        byte hiByte = (byte) (value >> 8);
        byte loByte = (byte) (value - (hiByte << 8));

        sendCommand(0x40+channel, hiByte, loByte);
    }


    /**
     * Returns the current output value of the PWM channel
     * @param channel the channel (0 or 1)
     * @return the value assigned to the PWM output
     * @throws InvalidParameterException
     */
    public int getPwm(int channel) throws InvalidParameterException {
        validatePWMChannel(channel);

        var resp = sendQuery(0x40+channel+2, 0, 0, 2);

        return (unsigned(resp[0]) << 8) + unsigned(resp[1]);
    }


    /**
     * Sets an analog value in one of the two analog outputs
     * @param channel the output channel (0 or 1)
     * @param value the value (0v to 4.095v)
     * @throws InvalidParameterException
     */
    public void setDac(int channel, double value) throws InvalidParameterException {
        if (value < 0 || value > 4.095) throw new InvalidParameterException("ERROR: DAC argument out of range - must be between 0 and 4.095 volts");
        requireCalibratedVcc();

        int dacValue = (int) (value / vcc * 1024);

        this.setPwm(channel, dacValue);
    }


    /**
     * Returns the analog output's value
     * @param channel the output channel (0 or 1)
     * @return the value of the output (0v to 4.095v)
     * @throws InvalidParameterException
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
     * Turns on the bi-color led
     * @param led color to turn on
     * @throws PiPlateException
     */
    public void setLed(BiColorLed led) {
        sendCommand(0x60, led.getValue(), 0);
    }


    /**
     * Turns off the bi-color led
     * @param led color to turn off
     * @throws PiPlateException
     */
    public void clearLed(BiColorLed led) {
        sendCommand(0x61, led.getValue(), 0);
    }


    /**
     * Toggles the bi-color LED in the DACQ-Plate
     * @param led the LED to toggle
     * @throws InvalidParameterException
     */
    public void toggleLed(BiColorLed led) {
        sendCommand(0x62, led.getValue(), 0);
    }


    /**
     * Returns the value of the bi-color LED in the DACQ-Plate
     * @param led the LED whose status to return
     * @throws InvalidParameterException
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

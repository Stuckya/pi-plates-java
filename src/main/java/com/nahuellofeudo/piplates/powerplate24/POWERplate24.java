package com.nahuellofeudo.piplates.powerplate24;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlate;
import com.nahuellofeudo.piplates.PiPlateException;
import com.pi4j.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Interface to the Pi-Plates POWERplate24 - a power management board with
 * high-voltage monitoring, RTC, fan control, and scheduled wake-up.
 * Only address 0 is valid (single board per stack).
 * Reference: ~/Projects/pi_plates-11.0/piplates/POWERplate24.py
 */
public class POWERplate24 extends PiPlate {

    public POWERplate24(Context pi4jContext, int address) throws InvalidAddressException {
        super(pi4jContext, address);
    }

    public POWERplate24(int address) throws InvalidAddressException {
        super(address);
    }

    public POWERplate24(Context pi4jContext) throws InvalidAddressException {
        this(pi4jContext, 0);
    }

    public POWERplate24() throws InvalidAddressException {
        this(0);
    }

    @Override
    protected int getBaseAddress() {
        return 0;
    }

    @Override
    public void validateAddress(int address) throws InvalidAddressException {
        if (address != 0) {
            throw new InvalidAddressException("POWERplate24 address must be 0");
        }
    }

    /* --------- ADC Functions --------- */

    /**
     * Reads the +5VDC rail voltage
     * @return voltage in volts
     */
    public double getVin() {
        var resp = ppCommand(0x30, 0, 0, 2).orElse(new byte[0]);
        int value = 256 * unsigned(resp[0]) + unsigned(resp[1]);
        return Math.round(value * 2.4 * 2.5 / 4095.0 * 1000.0) / 1000.0;
    }

    /**
     * Reads the high voltage input (0-24V range)
     * @return voltage in volts
     */
    public double getHVin() {
        var resp = ppCommand(0x30, 1, 0, 2).orElse(new byte[0]);
        int value = 256 * unsigned(resp[0]) + unsigned(resp[1]);
        return Math.round(value * 2.4 * 12.4573 / 4095.0 * 1000.0) / 1000.0;
    }

    /**
     * Reads a raw ADC channel value
     * @param channel the ADC channel
     * @return raw 12-bit ADC value
     */
    public int getADC(int channel) {
        var resp = ppCommand(0x30, channel, 0, 2).orElse(new byte[0]);
        return 256 * unsigned(resp[0]) + unsigned(resp[1]);
    }

    /**
     * Reads the Raspberry Pi CPU temperature from the system thermal zone.
     * This is a host-side reading, not an SPI command.
     * @return temperature in degrees Celsius
     */
    public double getCPUtemp() throws IOException {
        String content = Files.readString(Path.of("/sys/class/thermal/thermal_zone0/temp")).trim();
        return Integer.parseInt(content) / 1000.0;
    }

    /* --------- RTC and Schedule Functions --------- */

    /**
     * Sets the on-board real-time clock to the current time
     * @param zone LOCAL for local time, GREENWICH for UTC
     */
    public void setRTC(TimeZoneType zone) {
        LocalTime time;
        if (zone == TimeZoneType.LOCAL) {
            time = LocalTime.now();
        } else {
            time = ZonedDateTime.now(ZoneOffset.UTC).toLocalTime();
        }
        ppCommand(0xD1, time.getHour(), 0, 0);
        ppCommand(0xD0, time.getMinute(), time.getSecond(), 0);
    }

    /**
     * Sets the scheduled wake-up time
     * @param hour hour (0-23)
     * @param minute minute (0-59)
     * @param second second (0-59)
     */
    public void setWAKE(int hour, int minute, int second) throws InvalidParameterException {
        if (hour < 0 || hour > 23) throw new InvalidParameterException("Hour must be in range [0..23]");
        if (minute < 0 || minute > 59) throw new InvalidParameterException("Minute must be in range [0..59]");
        if (second < 0 || second > 59) throw new InvalidParameterException("Second must be in range [0..59]");
        ppCommand(0xD3, hour, 0, 0);
        ppCommand(0xD2, minute, second, 0);
    }

    /**
     * Enables scheduled wake-up
     */
    public void enableWAKE() {
        ppCommand(0xD5, 1, 0, 0);
    }

    /**
     * Disables scheduled wake-up
     */
    public void disableWAKE() {
        ppCommand(0xD5, 0, 0, 0);
    }

    /**
     * Returns the source of the last power-up
     * @return 0 = initial power up, 1 = pushbutton, 2 = scheduled wake
     */
    public int getWakeSource() {
        var resp = ppCommand(0x57, 0, 0, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }

    /* --------- LED Functions --------- */

    /**
     * Sets the bicolor LED to the specified color.
     * Clears both LEDs first, then sets the requested color.
     * @param color the LED color (OFF, RED, GREEN, YELLOW)
     */
    public void setLED(LEDColor color) {
        // Clear both LEDs
        ppCommand(0x61, 0, 0, 0); // clear green
        ppCommand(0x61, 1, 0, 0); // clear red
        switch (color) {
            case RED:
                ppCommand(0x60, 1, 0, 0);
                break;
            case GREEN:
                ppCommand(0x60, 0, 0, 0);
                break;
            case YELLOW:
                ppCommand(0x60, 0, 0, 0);
                ppCommand(0x60, 1, 0, 0);
                break;
            case OFF:
                // Already cleared
                break;
        }
    }

    /**
     * Sets the LED operating mode (persistent across power cycles)
     * @param mode the LED mode
     */
    public void ledMode(LEDMode mode) {
        ppCommand(0x6F, mode.getValue(), 0, 0);
    }

    /* --------- Fan Functions --------- */

    /**
     * Enables the cooling fan (persistent)
     */
    public void fanOn() {
        ppCommand(0xEF, 0, 0, 0);
    }

    /**
     * Disables the cooling fan (persistent)
     */
    public void fanOff() {
        ppCommand(0xEE, 0, 0, 0);
    }

    /**
     * Reads the current fan state
     * @return 1 if fan is on, 0 if off
     */
    public int fanState() {
        var resp = ppCommand(0xED, 0, 0, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }

    /* --------- Pushbutton and Power Functions --------- */

    /**
     * Reads the pushbutton state
     * @return 1 if pressed, 0 if released
     */
    public int getSWState() {
        var resp = ppCommand(0x50, 0, 0, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }

    /**
     * Sets the power-down delay after shutdown signal
     * @param delay delay in seconds (10-240)
     */
    public void setShutdownDelay(int delay) throws InvalidParameterException {
        if (delay < 10 || delay > 240) {
            throw new InvalidParameterException("Shutdown delay must be between 10 and 240 seconds");
        }
        ppCommand(0x55, delay, 0, 0);
    }

    /**
     * Enables the pushbutton power switch control
     * @param bypass if true and firmware >= 1.2, enables auto-power-on when supply is connected
     */
    public void enablePowerSwitch(boolean bypass) throws PiPlateException {
        int bparg = 0;
        if (getFWRev() >= 1.2 && bypass) {
            bparg = 1;
        }
        ppCommand(0x53, bparg, 0, 0);
    }

    /**
     * Disables the pushbutton power switch control
     */
    public void disablePowerSwitch() {
        ppCommand(0x54, 0, 0, 0);
    }

    /**
     * Initiates the power-down sequence
     */
    public void powerOff() {
        ppCommand(0x56, 0, 0, 0);
    }

    /* --------- Power Status Functions --------- */

    /**
     * Enables STAT pin to signal power status changes
     */
    public void statEnable() {
        ppCommand(0x04, 0, 0, 0);
    }

    /**
     * Disables STAT pin signaling
     */
    public void statDisable() {
        ppCommand(0x05, 0, 0, 0);
    }

    /**
     * Reads the power status change register. Clears the register and STAT line.
     * @return power change status byte
     */
    public int getPOWChange() {
        var resp = ppCommand(0x06, 0, 0, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }

    /**
     * Reads the current power status
     * @return power status byte (bit 0=NO_AC, bit 1=LOW_BAT, bit 2=LOW_DC_IN)
     */
    public int getPOWStatus() {
        var resp = ppCommand(0x07, 0, 0, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }

    /* --------- System Functions --------- */

    /**
     * Reads and returns the board's identifier string
     * @return a string ID read from the board
     */
    public String getId() {
        int ID_LENGTH = 20;
        return ppCommand(0x01, 0, 0, ID_LENGTH)
                .map(resp -> {
                    int length = ID_LENGTH;
                    for (int x = 0; x < ID_LENGTH; x++) {
                        if (resp[x] == 0) {
                            length = x;
                            break;
                        }
                    }
                    return new String(resp, 0, length);
                })
                .orElse("");
    }

    /**
     * Resets the board to power-on state
     */
    public void reset() throws InterruptedException {
        ppCommand(0x0F, 0, 0, 0);
        Thread.sleep(1000);
    }

    /**
     * Reads a byte from on-board flash memory
     * @param flashAddress the flash memory address
     * @return the byte value at that address
     */
    public int readFlash(int flashAddress) {
        int p1 = flashAddress >> 8;
        int p2 = flashAddress & 0xFF;
        var resp = ppCommand(0xFE, p1, p2, 1).orElse(new byte[0]);
        return unsigned(resp[0]);
    }
}

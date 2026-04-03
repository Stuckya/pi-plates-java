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
 * Interface to the Pi-Plates POWERplate24 — a power management board with
 * high-voltage monitoring (0-24 V), real-time clock, cooling fan control,
 * scheduled wake-up, and pushbutton power control. Only address 0 is valid
 * (single board per stack).
 *
 * <p>Many settings on this board (fan state, LED mode, power switch config,
 * shutdown delay) are persisted in non-volatile memory and retained across
 * power cycles.
 *
 * @see <a href="https://pi-plates.com/powerplate24-users-guide/">POWERplate24 User's Guide</a>
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
    protected void validateAddress(int address) throws InvalidAddressException {
        if (address != 0) {
            throw new InvalidAddressException("POWERplate24 address must be 0");
        }
    }

    /* --------- ADC Functions --------- */

    /**
     * Reads the +5 VDC rail voltage (accuracy +/-2%).
     *
     * @return measured voltage in volts
     */
    public double getVoltageIn() {
        var resp = sendQuery(0x30, 0, 0, 2);
        int value = 256 * unsigned(resp[0]) + unsigned(resp[1]);
        return Math.round(value * 2.4 * 2.5 / 4095.0 * 1000.0) / 1000.0;
    }

    /**
     * Reads the external DC input voltage (0-24 V range, accuracy +/-2%).
     *
     * @return measured voltage in volts
     */
    public double getHighVoltageIn() {
        var resp = sendQuery(0x30, 1, 0, 2);
        int value = 256 * unsigned(resp[0]) + unsigned(resp[1]);
        return Math.round(value * 2.4 * 12.4573 / 4095.0 * 1000.0) / 1000.0;
    }

    /**
     * Reads a raw ADC channel value
     * @param channel the ADC channel
     * @return raw 12-bit ADC value
     */
    public int getAnalogInput(int channel) {
        var resp = sendQuery(0x30, channel, 0, 2);
        return 256 * unsigned(resp[0]) + unsigned(resp[1]);
    }

    /**
     * Reads the Raspberry Pi CPU temperature from the system thermal zone.
     * This is a host-side reading, not an SPI command.
     * @return temperature in degrees Celsius
     */
    public double getCpuTemperature() throws IOException {
        String content = Files.readString(Path.of("/sys/class/thermal/thermal_zone0/temp")).trim();
        return Integer.parseInt(content) / 1000.0;
    }

    /* --------- RTC and Schedule Functions --------- */

    /**
     * Sets the on-board real-time clock to the specified time.
     *
     * @param time the time to set (hour, minute, second)
     */
    public void setRealTimeClock(LocalTime time) {
        sendCommand(0xD1, time.getHour(), 0);
        sendCommand(0xD0, time.getMinute(), time.getSecond());
    }

    /**
     * Sets the on-board real-time clock to the current time.
     * @param zone LOCAL for local time, GREENWICH for UTC
     */
    public void setRealTimeClockToNow(TimeZoneType zone) {
        LocalTime time;
        if (zone == TimeZoneType.LOCAL) {
            time = LocalTime.now();
        } else {
            time = ZonedDateTime.now(ZoneOffset.UTC).toLocalTime();
        }
        setRealTimeClock(time);
    }

    /**
     * Sets the scheduled wake-up time in 24-hour format.
     *
     * @param hour   hour (0-23)
     * @param minute minute (0-59)
     * @param second second (0-59)
     */
    public void setWakeTime(int hour, int minute, int second) throws InvalidParameterException {
        validateRange(hour, 0, 23, "Hour");
        validateRange(minute, 0, 59, "Minute");
        validateRange(second, 0, 59, "Second");
        sendCommand(0xD3, hour, 0);
        sendCommand(0xD2, minute, second);
    }

    /**
     * Enables scheduled wake-up. Call {@link #setWakeTime} first to configure
     * the wake time, then {@link #powerOff()} to begin the sleep cycle.
     */
    public void enableWake() {
        sendCommand(0xD5, 1, 0);
    }

    /**
     * Disables scheduled wake-up
     */
    public void disableWake() {
        sendCommand(0xD5, 0, 0);
    }

    /**
     * Returns the source of the last power-up
     * @return 0 = initial power up, 1 = pushbutton, 2 = scheduled wake
     */
    public int getWakeSource() {
        var resp = sendQuery(0x57, 0, 0, 1);
        return unsigned(resp[0]);
    }

    /* --------- LED Functions --------- */

    /**
     * Sets the bicolor LED to the specified color.
     * Clears both LEDs first, then sets the requested color.
     * @param color the LED color (OFF, RED, GREEN, YELLOW)
     */
    public void setLed(LedColor color) {
        // Clear both LEDs
        sendCommand(0x61, 0, 0); // clear green
        sendCommand(0x61, 1, 0); // clear red
        switch (color) {
            case RED:
                sendCommand(0x60, 1, 0);
                break;
            case GREEN:
                sendCommand(0x60, 0, 0);
                break;
            case YELLOW:
                sendCommand(0x60, 0, 0);
                sendCommand(0x60, 1, 0);
                break;
            case OFF:
                // Already cleared
                break;
        }
    }

    /**
     * Sets the LED operating mode. This setting is persisted on the board and
     * retained across power cycles. Holding the pushbutton for 10 seconds
     * resets it to {@link LedMode#ALWAYS_ON}.
     *
     * @param mode the desired LED operating mode
     */
    public void setLedMode(LedMode mode) {
        sendCommand(0x6F, mode.getValue(), 0);
    }

    /* --------- Fan Functions --------- */

    /**
     * Enables the cooling fan. This setting is persisted on the board and
     * retained across power cycles.
     */
    public void setFanOn() {
        sendCommand(0xEF, 0, 0);
    }

    /**
     * Disables the cooling fan. This setting is persisted on the board and
     * retained across power cycles. Holding the pushbutton for 10 seconds
     * resets the fan to the ON state.
     */
    public void setFanOff() {
        sendCommand(0xEE, 0, 0);
    }

    /**
     * Reads the current fan state
     * @return 1 if fan is on, 0 if off
     */
    public int getFanState() {
        var resp = sendQuery(0xED, 0, 0, 1);
        return unsigned(resp[0]);
    }

    /* --------- Pushbutton and Power Functions --------- */

    /**
     * Reads the pushbutton state
     * @return 1 if pressed, 0 if released
     */
    public int getSwitchState() {
        var resp = sendQuery(0x50, 0, 0, 1);
        return unsigned(resp[0]);
    }

    /**
     * Sets the delay between the shutdown signal and power removal. Default is
     * 20 seconds. This setting is persisted on the board. Holding the
     * pushbutton for 10 seconds resets it to 20 seconds.
     *
     * @param delay delay in seconds (10-240)
     */
    public void setShutdownDelay(int delay) throws InvalidParameterException {
        validateRange(delay, 10, 240, "Shutdown delay (seconds)");
        sendCommand(0x55, delay, 0);
    }

    /**
     * Enables pushbutton power control. The board will initiate a shutdown
     * sequence when the button is held for 3 seconds. This setting is persisted
     * on the board. Requires {@code dtoverlay=gpio-shutdown,gpio_pin=24} in
     * {@code /boot/config.txt}.
     */
    public void enablePowerSwitch() {
        sendCommand(0x53, 0, 0);
    }

    /**
     * Enables pushbutton power control with auto-power-on bypass. When bypass
     * is active, the board powers up the stack whenever DC supply is initially
     * connected (without waiting for a button press). This setting is persisted
     * on the board.
     *
     * @apiNote Bypass requires firmware >= 1.2. Older firmware silently falls
     *          back to standard power switch mode without bypass.
     */
    public void enablePowerSwitchWithBypass() throws PiPlateException {
        int bparg = getFirmwareRevision() >= 1.2 ? 1 : 0;
        sendCommand(0x53, bparg, 0);
    }

    /**
     * Disables pushbutton power control. This setting is persisted on the board.
     */
    public void disablePowerSwitch() {
        sendCommand(0x54, 0, 0);
    }

    /**
     * Initiates the power-down sequence. Requires
     * {@code dtoverlay=gpio-shutdown,gpio_pin=24} in {@code /boot/config.txt}.
     */
    public void powerOff() {
        sendCommand(0x56, 0, 0);
    }

    /* --------- Power Status Functions --------- */

    /**
     * Enables the STAT pin (GPIO22) interrupt. The board will pull the line low
     * when a change occurs to the power status of the external DC supply.
     */
    public void enableStatusInterrupt() {
        sendCommand(0x04, 0, 0);
    }

    /**
     * Disables the STAT pin (GPIO22) interrupt. The board will stop asserting
     * status changes on the line.
     */
    public void disableStatusInterrupt() {
        sendCommand(0x05, 0, 0);
    }

    /**
     * Reads the power status change register. Clears the register and STAT line.
     * @return power change status byte
     */
    public int getPowerChange() {
        var resp = sendQuery(0x06, 0, 0, 1);
        return unsigned(resp[0]);
    }

    /**
     * Reads the current power status of the external DC supply.
     *
     * @return power status byte: bit 0 = NO_AC (running on battery),
     *         bit 1 = LOW_BAT (battery below threshold),
     *         bit 2 = LOW_DC_IN (external DC below 8 V)
     */
    public int getPowerStatus() {
        var resp = sendQuery(0x07, 0, 0, 1);
        return unsigned(resp[0]);
    }

    /* --------- System Functions --------- */

    /**
     * Resets the board to power-on state
     */
    public void reset() throws InterruptedException {
        sendCommand(0x0F, 0, 0);
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
        var resp = sendQuery(0xFE, p1, p2, 1);
        return unsigned(resp[0]);
    }
}

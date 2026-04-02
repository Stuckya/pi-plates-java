package com.nahuellofeudo.piplates.powerplate24;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlateException;
import com.nahuellofeudo.piplates.PiPlateTestHelper;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for POWERplate24.
 * Verifies SPI command bytes and response parsing against Python v11 (POWERplate24).
 * Base address: 0. Only address 0 is valid.
 */
class POWERplate24Test {

    private static Context mockContext;
    private static PiPlateTestHelper helper;
    private static POWERplate24 plate;

    private static final int BASE_ADDR = 0;
    private static final int PLATE_ADDR = 0;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = PiPlateTestHelper.createMockContext();
        plate = new POWERplate24(mockContext, PLATE_ADDR);
        helper = new PiPlateTestHelper(mockContext);
    }

    @BeforeEach
    void clearBuffer() {
        helper.clearSpiBuffer();
    }

    @AfterAll
    static void tearDown() {
        mockContext.shutdown();
    }

    /* --------- ADC Functions --------- */

    @Test
    void getVoltageInSendsCorrectCommandAndParsesResponse() {
        // raw = 2048 → high byte = 0x08, low byte = 0x00
        helper.preloadResponse((byte) 0x08, (byte) 0x00);
        double result = plate.getVoltageIn();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x30, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        // 2048 * 2.4 * 2.5 / 4095 = 3.001... → rounded to 3 places = 3.001
        assertEquals(3.001, result, 0.001);
    }

    @Test
    void getVoltageInZeroRaw() {
        helper.preloadResponse((byte) 0x00, (byte) 0x00);
        double result = plate.getVoltageIn();
        assertEquals(0.0, result, 0.001);
    }

    @Test
    void getVoltageInMaxRaw() {
        // raw = 4095 (0x0F, 0xFF) → 4095 * 2.4 * 2.5 / 4095 = 6.0
        helper.preloadResponse((byte) 0x0F, (byte) 0xFF);
        double result = plate.getVoltageIn();
        assertEquals(6.0, result, 0.001);
    }

    @Test
    void getHighVoltageInSendsCorrectCommandAndParsesResponse() {
        // raw = 1000 → high byte = 0x03, low byte = 0xE8
        helper.preloadResponse((byte) 0x03, (byte) 0xE8);
        double result = plate.getHighVoltageIn();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x30, 0x01, 0x00},
                helper.getLastCommandPacket()
        );
        // 1000 * 2.4 * 12.4573 / 4095 = 7.302...
        assertEquals(7.302, result, 0.001);
    }

    @Test
    void getAnalogInputSendsCorrectCommand() {
        // raw = 512 → high byte = 0x02, low byte = 0x00
        helper.preloadResponse((byte) 0x02, (byte) 0x00);
        int result = plate.getAnalogInput(3);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x30, 0x03, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(512, result);
    }

    /* --------- RTC Functions (multi-command) --------- */

    @Test
    void setRealTimeClockLocalSendsTwoCommands() {
        // setRealTimeClock(LOCAL) sends two commands:
        // 1. [0, 0xD1, hour, 0]
        // 2. [0, 0xD0, minute, second]
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        plate.setRealTimeClockToNow(TimeZoneType.LOCAL);
        byte[] allBytes = helper.getAllSpiBytes();
        // Two 4-byte commands = 8 bytes total
        assertTrue(allBytes.length >= 8, "Expected at least 8 SPI bytes for two commands");
        // Verify command codes (time values depend on current clock, so just check cmd bytes)
        byte[] cmd1 = Arrays.copyOfRange(allBytes, 0, 4);
        byte[] cmd2 = Arrays.copyOfRange(allBytes, 4, 8);
        assertEquals((byte) (BASE_ADDR + PLATE_ADDR), cmd1[0]);
        assertEquals((byte) 0xD1, cmd1[1]);
        assertEquals((byte) 0x00, cmd1[3]);
        assertEquals((byte) (BASE_ADDR + PLATE_ADDR), cmd2[0]);
        assertEquals((byte) 0xD0, cmd2[1]);
    }

    @Test
    void setWakeTimeSendsCorrectTwoCommands() {
        // setWakeTime(12, 30, 45) sends:
        // 1. [0, 0xD3, 12, 0]
        // 2. [0, 0xD2, 30, 45]
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        plate.setWakeTime(12, 30, 45);
        byte[] allBytes = helper.getAllSpiBytes();
        assertTrue(allBytes.length >= 8, "Expected at least 8 SPI bytes for two commands");
        byte[] cmd1 = Arrays.copyOfRange(allBytes, 0, 4);
        byte[] cmd2 = Arrays.copyOfRange(allBytes, 4, 8);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xD3, 12, 0x00},
                cmd1
        );
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xD2, 30, 45},
                cmd2
        );
    }

    /* --------- Wake Enable/Disable --------- */

    @Test
    void enableWakeSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.enableWake();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xD5, 0x01, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void disableWakeSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.disableWake();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xD5, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    /* --------- getWakeSource --------- */

    @Test
    void getWakeSourceSendsCorrectCommandAndParsesResponse() {
        helper.preloadResponse((byte) 2);
        int source = plate.getWakeSource();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x57, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(2, source);
    }

    /* --------- LED Functions (multi-command) --------- */

    @Test
    void setLedRedSendsThreeCommands() {
        // setLed(RED) sends:
        // 1. [0, 0x61, 0, 0] — clear green
        // 2. [0, 0x61, 1, 0] — clear red
        // 3. [0, 0x60, 1, 0] — set red
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        plate.setLed(LedColor.RED);
        byte[] allBytes = helper.getAllSpiBytes();
        assertTrue(allBytes.length >= 12, "Expected at least 12 SPI bytes for three commands");
        byte[] cmd1 = Arrays.copyOfRange(allBytes, 0, 4);
        byte[] cmd2 = Arrays.copyOfRange(allBytes, 4, 8);
        byte[] cmd3 = Arrays.copyOfRange(allBytes, 8, 12);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x61, 0x00, 0x00},
                cmd1
        );
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x61, 0x01, 0x00},
                cmd2
        );
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x60, 0x01, 0x00},
                cmd3
        );
    }

    @Test
    void setLedGreenSendsThreeCommands() {
        // setLed(GREEN): clear green, clear red, set green
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        plate.setLed(LedColor.GREEN);
        byte[] allBytes = helper.getAllSpiBytes();
        assertTrue(allBytes.length >= 12);
        byte[] cmd3 = Arrays.copyOfRange(allBytes, 8, 12);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x60, 0x00, 0x00},
                cmd3
        );
    }

    @Test
    void setLedYellowSendsFourCommands() {
        // setLed(YELLOW): clear green, clear red, set green, set red
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        plate.setLed(LedColor.YELLOW);
        byte[] allBytes = helper.getAllSpiBytes();
        assertTrue(allBytes.length >= 16, "Expected at least 16 SPI bytes for four commands");
        byte[] cmd3 = Arrays.copyOfRange(allBytes, 8, 12);
        byte[] cmd4 = Arrays.copyOfRange(allBytes, 12, 16);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x60, 0x00, 0x00},
                cmd3
        );
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x60, 0x01, 0x00},
                cmd4
        );
    }

    @Test
    void setLedOffSendsTwoCommands() {
        // setLed(OFF): just clear green and clear red
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        plate.setLed(LedColor.OFF);
        byte[] allBytes = helper.getAllSpiBytes();
        assertTrue(allBytes.length >= 8);
        byte[] cmd1 = Arrays.copyOfRange(allBytes, 0, 4);
        byte[] cmd2 = Arrays.copyOfRange(allBytes, 4, 8);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x61, 0x00, 0x00},
                cmd1
        );
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x61, 0x01, 0x00},
                cmd2
        );
    }

    /* --------- setLedMode --------- */

    @Test
    void setLedModeBlinkSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setLedMode(LedMode.BLINK);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x6F, 0x01, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void setLedModeAlwaysOnSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setLedMode(LedMode.ALWAYS_ON);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x6F, 0x02, 0x00},
                helper.getLastCommandPacket()
        );
    }

    /* --------- Fan Functions --------- */

    @Test
    void setFanOnSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setFanOn();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xEF, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void setFanOffSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setFanOff();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xEE, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void getFanStateSendsCorrectCommandAndParsesResponse() {
        helper.preloadResponse((byte) 1);
        int state = plate.getFanState();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xED, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(1, state);
    }

    /* --------- Pushbutton and Power Functions --------- */

    @Test
    void getSwitchStateSendsCorrectCommandAndParsesResponse() {
        helper.preloadResponse((byte) 1);
        int state = plate.getSwitchState();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x50, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(1, state);
    }

    @Test
    void setShutdownDelaySendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setShutdownDelay(120);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x55, 120, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void enablePowerSwitchBypassFalseSendsCorrectCommands() throws PiPlateException {
        // enablePowerSwitch(false) first calls getFirmwareRevision() which sends
        // [0, 0x03, 0, 0] and reads 1 byte, then sends [0, 0x53, 0, 0].
        // Preload FW revision response (e.g. 0x00 → version 0.0, so bypass check irrelevant)
        helper.preloadResponse((byte) 0x00);
        // Preload no-response for the 0x53 command
        helper.preloadNoResponse();
        plate.enablePowerSwitch(false);
        byte[] allBytes = helper.getAllSpiBytes();
        // Command 1: [0, 0x03, 0, 0] (4 bytes) + 2 zero bytes for reading response (1 data + 1 checksum)
        byte[] fwCmd = Arrays.copyOfRange(allBytes, 0, 4);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x03, 0x00, 0x00},
                fwCmd
        );
        // Command 2: offset = 4 (cmd) + 2 (response read) = 6
        byte[] pwrCmd = Arrays.copyOfRange(allBytes, 6, 10);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x53, 0x00, 0x00},
                pwrCmd
        );
    }

    @Test
    void disablePowerSwitchSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.disablePowerSwitch();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x54, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void powerOffSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.powerOff();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x56, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    /* --------- Power Status Functions --------- */

    @Test
    void statusEnableSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.enableStatusInterrupt();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x04, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void statusDisableSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.disableStatusInterrupt();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x05, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void getPowerChangeSendsCorrectCommandAndParsesResponse() {
        helper.preloadResponse((byte) 0x07);
        int change = plate.getPowerChange();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x06, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(0x07, change);
    }

    @Test
    void getPowerStatusSendsCorrectCommandAndParsesResponse() {
        helper.preloadResponse((byte) 0x05);
        int status = plate.getPowerStatus();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x07, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(0x05, status);
    }

    /* --------- Flash and System Functions --------- */

    @Test
    void readFlashSendsCorrectCommandWithSplitAddress() {
        // readFlash(0x1234): p1 = 0x12, p2 = 0x34
        helper.preloadResponse((byte) 0xAB);
        int value = plate.readFlash(0x1234);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xFE, 0x12, 0x34},
                helper.getLastCommandPacket()
        );
        assertEquals(0xAB, value);
    }

    @Test
    void resetSendsCorrectCommand() throws InterruptedException {
        helper.preloadNoResponse();
        plate.reset();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x0F, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    /* --------- Parameter Validation --------- */

    @Test
    void setShutdownDelayRejectsTooLow() {
        assertThrows(InvalidParameterException.class, () -> plate.setShutdownDelay(9));
    }

    @Test
    void setShutdownDelayRejectsTooHigh() {
        assertThrows(InvalidParameterException.class, () -> plate.setShutdownDelay(241));
    }

    @Test
    void setShutdownDelayRejectsZero() {
        assertThrows(InvalidParameterException.class, () -> plate.setShutdownDelay(0));
    }

    @Test
    void setShutdownDelayRejectsNegative() {
        assertThrows(InvalidParameterException.class, () -> plate.setShutdownDelay(-1));
    }

    @Test
    void setWakeTimeRejectsInvalidHour() {
        assertThrows(InvalidParameterException.class, () -> plate.setWakeTime(-1, 0, 0));
        assertThrows(InvalidParameterException.class, () -> plate.setWakeTime(24, 0, 0));
    }

    @Test
    void setWakeTimeRejectsInvalidMinute() {
        assertThrows(InvalidParameterException.class, () -> plate.setWakeTime(0, -1, 0));
        assertThrows(InvalidParameterException.class, () -> plate.setWakeTime(0, 60, 0));
    }

    @Test
    void setWakeTimeRejectsInvalidSecond() {
        assertThrows(InvalidParameterException.class, () -> plate.setWakeTime(0, 0, -1));
        assertThrows(InvalidParameterException.class, () -> plate.setWakeTime(0, 0, 60));
    }

    @Test
    void setShutdownDelayAcceptsBoundaryValues() {
        helper.preloadNoResponse();
        assertDoesNotThrow(() -> plate.setShutdownDelay(10));
        helper.preloadNoResponse();
        assertDoesNotThrow(() -> plate.setShutdownDelay(240));
    }

    @Test
    void setWakeTimeAcceptsBoundaryValues() {
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        assertDoesNotThrow(() -> plate.setWakeTime(0, 0, 0));
        helper.preloadNoResponse();
        helper.preloadNoResponse();
        assertDoesNotThrow(() -> plate.setWakeTime(23, 59, 59));
    }

    /* --------- Address Validation --------- */

    @Test
    void rejectsNonZeroAddress() {
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, 1));
    }

    @Test
    void rejectsNegativeAddress() {
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, -1));
    }

    @Test
    void rejectsHighAddress() {
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, 7));
    }
}

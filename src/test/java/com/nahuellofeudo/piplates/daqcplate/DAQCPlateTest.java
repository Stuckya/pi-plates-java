package com.nahuellofeudo.piplates.daqcplate;

import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlateTestHelper;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DAQCPlate. Verifies SPI command bytes and response parsing.
 * Reference: Pi-Plates Python library v11 (DAQCplate)
 * Base address: 8. Digital inputs 0-7.
 */
class DAQCPlateTest {

    private static Context mockContext;
    private static PiPlateTestHelper helper;
    private static DAQCPlate plate;

    private static final int BASE_ADDR = 8;
    private static final int PLATE_ADDR = 0;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = PiPlateTestHelper.createMockContext();
        plate = new DAQCPlate(mockContext, PLATE_ADDR);
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

    /* --------- Interrupt Commands --------- */

    @Test
    void interruptEnableSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.interruptEnable();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x04, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void interruptDisableSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.interruptDisable();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x05, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void getInterruptFlagsParsesResponse() {
        helper.preloadResponse((byte) 0x12, (byte) 0x34);
        int flags = plate.getInterruptFlags();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x06, 0x00, 0x00}, helper.getLastCommandPacket());
        assertEquals(0x1234, flags);
    }

    /* --------- Digital Input Commands --------- */

    @Test
    void getDigitalInputSendsCorrectCommand() {
        helper.preloadResponse((byte) 0x01);
        boolean result = plate.getDigitalInput(0);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x20, 0x00, 0x00}, helper.getLastCommandPacket());
        assertTrue(result);
    }

    @Test
    void getDigitalInputBit7() {
        helper.preloadResponse((byte) 0x00);
        boolean result = plate.getDigitalInput(7);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x20, 0x07, 0x00}, helper.getLastCommandPacket());
        assertFalse(result);
    }

    @Test
    void getDigitalInputAllParsesUnsigned() {
        helper.preloadResponse((byte) 0xFF);
        int result = plate.getDigitalInputAll();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x25, 0x00, 0x00}, helper.getLastCommandPacket());
        assertEquals(0xFF, result); // unsigned: 255, not -1
    }

    @Test
    void enableDigitalInputInterruptFalling() {
        helper.preloadNoResponse();
        plate.enableDigitalInputInterrupt(3, InterruptEdge.FALLING_EDGE);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x21, 0x03, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void enableDigitalInputInterruptRising() {
        helper.preloadNoResponse();
        plate.enableDigitalInputInterrupt(5, InterruptEdge.RISING_EDGE);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x22, 0x05, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void enableDigitalInputInterruptBoth() {
        helper.preloadNoResponse();
        plate.enableDigitalInputInterrupt(0, InterruptEdge.BOTH_EDGES);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x23, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void disableDigitalInputInterrupt() {
        helper.preloadNoResponse();
        plate.disableDigitalInputInterrupt(6);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x24, 0x06, 0x00}, helper.getLastCommandPacket());
    }

    /* --------- ADC Commands --------- */

    @Test
    void getAnalogInputSendsCorrectCommand() {
        // Raw value: (0x08 * 256 + 0x00) = 2048, * 4 = 8192
        helper.preloadResponse((byte) 0x08, (byte) 0x00);
        int result = plate.getAnalogInput(0);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x30, 0x00, 0x00}, helper.getLastCommandPacket());
        assertEquals(8192, result); // 2048 * 4
    }

    @Test
    void getAnalogInputChannel8VCC() {
        // Channel 8 (VCC reference): raw * 4 * 2
        helper.preloadResponse((byte) 0x04, (byte) 0x00);
        int result = plate.getAnalogInput(8);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x30, 0x08, 0x00}, helper.getLastCommandPacket());
        assertEquals(8192, result); // (0x400=1024) * 4 * 2
    }

    @Test
    void getAnalogInputAllSendsCorrectCommand() {
        // 16 bytes: 8 channels, each 2 bytes
        byte[] response = new byte[16];
        response[0] = 0x01; response[1] = 0x00; // ch0: 256 * 4 = 1024
        helper.preloadResponse(response);
        int[] result = plate.getAnalogInputAll();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x31, 0x00, 0x00}, helper.getLastCommandPacket());
        assertEquals(1024, result[0]); // 256 * 4
    }

    /* --------- PWM Commands --------- */

    @Test
    void setPwmSendsCorrectCommand() {
        // value=512: hiByte=2, loByte=0
        helper.preloadNoResponse();
        plate.setPwm(0, 512);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x40, 0x02, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void setPwmChannel1() {
        helper.preloadNoResponse();
        plate.setPwm(1, 1023);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x41, 0x03, (byte) 0xFF}, helper.getLastCommandPacket());
    }

    @Test
    void getPwmSendsCorrectCommand() {
        helper.preloadResponse((byte) 0x02, (byte) 0x00);
        int result = plate.getPwm(0);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x42, 0x00, 0x00}, helper.getLastCommandPacket());
        assertEquals(512, result);
    }

    /* --------- LED Commands --------- */

    @Test
    void setLedGreen() {
        helper.preloadNoResponse();
        plate.setLed(BiColorLed.GREEN);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x60, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void setLedRed() {
        helper.preloadNoResponse();
        plate.setLed(BiColorLed.RED);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x60, 0x01, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void clearLedGreen() {
        helper.preloadNoResponse();
        plate.clearLed(BiColorLed.GREEN);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x61, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void toggleLedRed() {
        helper.preloadNoResponse();
        plate.toggleLed(BiColorLed.RED);
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x62, 0x01, 0x00}, helper.getLastCommandPacket());
    }

    /* --------- Parameter Validation --------- */

    @Test
    void getDigitalInputAcceptsFullRange() {
        for (int bit = 0; bit <= 7; bit++) {
            helper.clearSpiBuffer();
            helper.preloadResponse((byte) 0x00);
            final int b = bit;
            assertDoesNotThrow(() -> plate.getDigitalInput(b));
        }
    }

    @Test
    void getDigitalInputRejectsOutOfRange() {
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(-1));
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(8));
    }

    @Test
    void getAnalogInputRejectsOutOfRange() {
        assertThrows(InvalidParameterException.class, () -> plate.getAnalogInput(-1));
        assertThrows(InvalidParameterException.class, () -> plate.getAnalogInput(9));
    }

    @Test
    void setPwmRejectsInvalidValues() {
        assertThrows(InvalidParameterException.class, () -> plate.setPwm(0, -1));
        assertThrows(InvalidParameterException.class, () -> plate.setPwm(0, 1024));
        assertThrows(InvalidParameterException.class, () -> plate.setPwm(2, 0));
    }

    /* --------- Temperature Commands --------- */

    @Test
    void getTemperatureRejectsChannel8() {
        assertThrows(InvalidParameterException.class,
                () -> plate.getTemperature(8, TemperatureUnit.CELSIUS));
    }

    @Test
    void getTemperatureRejectsNegativeChannel() {
        assertThrows(InvalidParameterException.class,
                () -> plate.getTemperature(-1, TemperatureUnit.CELSIUS));
    }

    @Test
    void getTemperaturePositiveCelsius() throws Exception {
        // 25.0°C: raw = 25 * 16 = 400 = 0x0190
        helper.preloadNoResponse();                          // sendCommand(0x70, ...)
        helper.preloadResponse((byte) 0x01, (byte) 0x90);   // sendQuery(0x71, ...)
        double temp = plate.getTemperature(0, TemperatureUnit.CELSIUS);
        assertEquals(25.0, temp, 0.001);
    }

    @Test
    void getTemperatureNegativeCelsius() throws Exception {
        // -10.0°C: raw two's complement = 0xFF60
        helper.preloadNoResponse();
        helper.preloadResponse((byte) 0xFF, (byte) 0x60);
        double temp = plate.getTemperature(0, TemperatureUnit.CELSIUS);
        assertEquals(-10.0, temp, 0.001);
    }

    @Test
    void getTemperatureKelvin() throws Exception {
        // 25.0°C = 298K
        helper.preloadNoResponse();
        helper.preloadResponse((byte) 0x01, (byte) 0x90);
        double temp = plate.getTemperature(0, TemperatureUnit.KELVIN);
        assertEquals(298.0, temp, 0.001);
    }

    @Test
    void getTemperatureFahrenheit() throws Exception {
        // 25.0°C = 25 * 1.8 + 32.2 = 77.2°F
        helper.preloadNoResponse();
        helper.preloadResponse((byte) 0x01, (byte) 0x90);
        double temp = plate.getTemperature(0, TemperatureUnit.FAHRENHEIT);
        assertEquals(77.2, temp, 0.001);
    }

    /* --------- DAC Commands --------- */

    @Test
    void setDacSendsCorrectPwmValue() {
        // With vcc = 5.0V, setDac(0, 2.5) → dacValue = (int)(2.5 / 5.0 * 1024) = 512
        // setPwm(0, 512): hiByte=2, loByte=0 → command 0x40
        plate.vcc = 5.0;
        helper.preloadNoResponse();
        plate.setDac(0, 2.5);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x40, 0x02, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void getDacReturnsCorrectVoltage() {
        // With vcc = 5.0V, getPwm returns 512 → getDac = 512 * 5.0 / 1023 ≈ 2.502
        plate.vcc = 5.0;
        helper.preloadResponse((byte) 0x02, (byte) 0x00);
        double voltage = plate.getDac(0);
        assertEquals(2.502, voltage, 0.001);
    }

    @Test
    void setDacRejectsOutOfRange() {
        plate.vcc = 5.0;
        assertThrows(InvalidParameterException.class, () -> plate.setDac(0, -0.1));
        assertThrows(InvalidParameterException.class, () -> plate.setDac(0, 4.096));
    }
}

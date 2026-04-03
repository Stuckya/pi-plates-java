package com.nahuellofeudo.piplates.currentplate;

import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlateTestHelper;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CURRENTplate.
 * Verifies SPI command bytes and response parsing against Python v11 (CURRENTplate).
 * Base address: 80 (0x50). Channels 1-8, channel param sent as channel-1 (0-indexed).
 */
class CURRENTplateTest {

    private static Context mockContext;
    private static PiPlateTestHelper helper;
    private static CURRENTplate plate;

    private static final int BASE_ADDR = 80;
    private static final int PLATE_ADDR = 0;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = PiPlateTestHelper.createMockContext();
        plate = new CURRENTplate(mockContext, PLATE_ADDR);
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

    /* --------- getCurrent: Command Verification and Response Parsing --------- */

    @Test
    void getCurrentChannel1SendsCorrectCommand() {
        // raw = 32768 → high byte = 0x80, low byte = 0x00
        helper.preloadResponse((byte) 0x80, (byte) 0x00);
        double result = plate.getCurrent(1);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x30, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        // 32768 * 24.0 / 65536.0 = 12.0
        assertEquals(12.0, result, 0.0001);
    }

    @Test
    void getCurrentChannel8SendsCorrectParam() {
        // raw = 32768
        helper.preloadResponse((byte) 0x80, (byte) 0x00);
        double result = plate.getCurrent(8);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x30, 0x07, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(12.0, result, 0.0001);
    }

    @Test
    void getCurrentMaxRaw() {
        // raw = 65535 (0xFF, 0xFF) → 65535 * 24.0 / 65536.0 ≈ 23.9996
        helper.preloadResponse((byte) 0xFF, (byte) 0xFF);
        double result = plate.getCurrent(1);
        assertEquals(23.9996, result, 0.0001);
    }

    @Test
    void getCurrentZeroRaw() {
        // raw = 0 → 0.0
        helper.preloadResponse((byte) 0x00, (byte) 0x00);
        double result = plate.getCurrent(1);
        assertEquals(0.0, result, 0.0001);
    }

    /* --------- getCurrentAll: Command Verification and Response Parsing --------- */

    @Test
    void getCurrentAllSendsCorrectCommandAndParsesAllChannels() {
        // 16 bytes: 8 channels, each 2 bytes
        // Channel 1: raw=32768 (0x80, 0x00) → 12.0
        // Channel 2: raw=65535 (0xFF, 0xFF) → 23.9996
        // Channel 3: raw=0     (0x00, 0x00) → 0.0
        // Channel 4: raw=16384 (0x40, 0x00) → 6.0
        // Channel 5: raw=49152 (0xC0, 0x00) → 18.0
        // Channel 6: raw=256   (0x01, 0x00) → 0.0938
        // Channel 7: raw=1     (0x00, 0x01) → 0.0004
        // Channel 8: raw=10000 (0x27, 0x10) → 3.6621
        helper.preloadResponse(
                (byte) 0x80, (byte) 0x00,  // ch1
                (byte) 0xFF, (byte) 0xFF,  // ch2
                (byte) 0x00, (byte) 0x00,  // ch3
                (byte) 0x40, (byte) 0x00,  // ch4
                (byte) 0xC0, (byte) 0x00,  // ch5
                (byte) 0x01, (byte) 0x00,  // ch6
                (byte) 0x00, (byte) 0x01,  // ch7
                (byte) 0x27, (byte) 0x10   // ch8
        );
        double[] results = plate.getCurrentAll();
        byte[] cmd = helper.getLastCommandPacket();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x31, 0x00, 0x00},
                cmd
        );
        assertEquals(8, results.length);
        assertEquals(12.0, results[0], 0.0001);
        assertEquals(23.9996, results[1], 0.0001);
        assertEquals(0.0, results[2], 0.0001);
        assertEquals(6.0, results[3], 0.0001);
        assertEquals(18.0, results[4], 0.0001);
        // raw=256 → 256*24.0/65536.0 = 0.09375 → rounded to 4 places = 0.0938
        assertEquals(0.0938, results[5], 0.0001);
        // raw=1 → 1*24.0/65536.0 = 0.000366... → rounded to 4 places = 0.0004
        assertEquals(0.0004, results[6], 0.0001);
        // raw=10000 → 10000*24.0/65536.0 = 3.662109375 → rounded = 3.6621
        assertEquals(3.6621, results[7], 0.0001);
    }

    /* --------- setFrequency --------- */

    @Test
    void setFrequency50SendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setFrequency(50);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x3F, 50, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void setFrequency60SendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setFrequency(60);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x3F, 60, 0x00},
                helper.getLastCommandPacket()
        );
    }

    /* --------- LED Functions --------- */

    @Test
    void setLedSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setLed();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x60, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void clearLedSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.clearLed();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x61, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void toggleLedSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.toggleLed();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x62, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    /* --------- Interrupt Functions --------- */

    @Test
    void interruptEnableSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.interruptEnable();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x04, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void interruptDisableSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.interruptDisable();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x05, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void getInterruptFlagsSendsCorrectCommandAndParsesResponse() {
        helper.preloadResponse((byte) 0xA5);
        int flags = plate.getInterruptFlags();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x06, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(0xA5, flags);
    }

    @Test
    void setInterruptSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setInterrupt();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xF4, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void clearInterruptSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.clearInterrupt();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xF5, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    /* --------- Calibration Functions --------- */

    @Test
    void calibrationReadByteSendsCorrectCommandAndParsesResponse() {
        helper.preloadResponse((byte) 0x42);
        int value = plate.calibrationReadByte(100);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xFD, 0x02, 100},
                helper.getLastCommandPacket()
        );
        assertEquals(0x42, value);
    }

    @Test
    void calibrationReadBytePointer0() {
        helper.preloadResponse((byte) 0x00);
        int value = plate.calibrationReadByte(0);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xFD, 0x02, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(0, value);
    }

    @Test
    void calibrationReadBytePointer255() {
        helper.preloadResponse((byte) 0xFF);
        int value = plate.calibrationReadByte(255);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xFD, 0x02, (byte) 0xFF},
                helper.getLastCommandPacket()
        );
        assertEquals(0xFF, value);
    }

    @Test
    void calibrationWriteByteSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.calibrationWriteByte(0xAB);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xFD, 0x01, (byte) 0xAB},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void calibrationEraseBlockSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.calibrationEraseBlock();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), (byte) 0xFD, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    /* --------- System Functions --------- */

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
    void getCurrentRejectsChannel0() {
        assertThrows(InvalidParameterException.class, () -> plate.getCurrent(0));
    }

    @Test
    void getCurrentRejectsChannel9() {
        assertThrows(InvalidParameterException.class, () -> plate.getCurrent(9));
    }

    @Test
    void getCurrentRejectsNegativeChannel() {
        assertThrows(InvalidParameterException.class, () -> plate.getCurrent(-1));
    }

    @Test
    void setFrequencyRejectsInvalidValue() {
        assertThrows(InvalidParameterException.class, () -> plate.setFrequency(55));
        assertThrows(InvalidParameterException.class, () -> plate.setFrequency(0));
        assertThrows(InvalidParameterException.class, () -> plate.setFrequency(100));
    }

    @Test
    void calibrationReadByteRejectsNegativePointer() {
        assertThrows(InvalidParameterException.class, () -> plate.calibrationReadByte(-1));
    }

    @Test
    void calibrationReadByteRejectsPointerOver255() {
        assertThrows(InvalidParameterException.class, () -> plate.calibrationReadByte(256));
    }

    @Test
    void calibrationWriteByteRejectsNegativeData() {
        assertThrows(InvalidParameterException.class, () -> plate.calibrationWriteByte(-1));
    }

    @Test
    void calibrationWriteByteRejectsDataOver255() {
        assertThrows(InvalidParameterException.class, () -> plate.calibrationWriteByte(256));
    }
}

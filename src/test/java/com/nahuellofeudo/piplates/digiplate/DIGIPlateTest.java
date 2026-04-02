package com.nahuellofeudo.piplates.digiplate;

import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlateTestHelper;
import com.nahuellofeudo.piplates.daqcplate.InterruptEdge;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DIGIPlate. Verifies SPI command bytes and response parsing.
 * Reference: Pi-Plates Python library v11 (DIGIplate)
 * Base address: 88 (0x58). Digital inputs 1-8 (sent as bit-1 to hardware).
 */
class DIGIPlateTest {

    private static Context mockContext;
    private static PiPlateTestHelper helper;
    private static DIGIPlate plate;

    private static final int BASE_ADDR = 88;
    private static final int PLATE_ADDR = 0;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = PiPlateTestHelper.createMockContext();
        plate = new DIGIPlate(mockContext, PLATE_ADDR);
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

    /* --------- Digital Input Commands --------- */

    @Test
    void getDigitalInputSendsCorrectCommand() {
        helper.preloadResponse((byte) 0x01);
        boolean result = plate.getDigitalInput(1);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x20, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertTrue(result);
    }

    @Test
    void getDigitalInputChannel8() {
        helper.preloadResponse((byte) 0x00);
        boolean result = plate.getDigitalInput(8);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x20, 0x07, 0x00},
                helper.getLastCommandPacket()
        );
        assertFalse(result);
    }

    @Test
    void getDigitalInputAllSendsCorrectCommand() {
        helper.preloadResponse((byte) 0xA5);
        int result = plate.getDigitalInputAll();
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x25, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(0xA5, result);
    }

    /* --------- Event Commands --------- */

    @Test
    void enableDigitalInputEventFallingEdge() {
        helper.preloadNoResponse();
        plate.enableDigitalInputEvent(3, InterruptEdge.FALLING_EDGE);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x21, 0x02, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void enableDigitalInputEventRisingEdge() {
        helper.preloadNoResponse();
        plate.enableDigitalInputEvent(1, InterruptEdge.RISING_EDGE);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x22, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void enableDigitalInputEventBothEdges() {
        helper.preloadNoResponse();
        plate.enableDigitalInputEvent(5, InterruptEdge.BOTH_EDGES);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x23, 0x04, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void disableDigitalInputEvent() {
        helper.preloadNoResponse();
        plate.disableDigitalInputEvent(4);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x24, 0x03, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void enableEventsSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.enableEvents();
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x04, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void disableEventsSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.disableEvents();
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x05, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void getEventFlagsParsesResponse() {
        helper.preloadResponse((byte) 0x80, (byte) 0x01);
        int flags = plate.getEventFlags();
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x06, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(0x8001, flags);
    }

    /* --------- Frequency Commands --------- */

    @Test
    void getFrequency1Hz() {
        // counts = (0x00<<24)+(0x0F<<16)+(0x42<<8)+0x40 = 1000000
        helper.preloadResponse((byte) 0x00, (byte) 0x0F);
        helper.preloadResponse((byte) 0x42, (byte) 0x40);
        double freq = plate.getFrequency(1);
        assertEquals(1.0, freq, 0.001);
    }

    @Test
    void getFrequency1000Hz() {
        // counts = 1000 → freq = 1000.0 Hz
        helper.preloadResponse((byte) 0x00, (byte) 0x00);
        helper.preloadResponse((byte) 0x03, (byte) 0xE8);
        double freq = plate.getFrequency(3);
        assertEquals(1000.0, freq, 0.001);
    }

    @Test
    void getFrequencyZeroCounts() {
        helper.preloadResponse((byte) 0x00, (byte) 0x00);
        helper.preloadResponse((byte) 0x00, (byte) 0x00);
        double freq = plate.getFrequency(1);
        assertEquals(0.0, freq, 0.001);
    }

    /* --------- LED Commands --------- */

    @Test
    void setLedSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setLed();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x60, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void clearLedSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.clearLed();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x61, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void toggleLedSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.toggleLed();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x62, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    /* --------- System Commands --------- */

    @Test
    void setInterruptSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.setInterrupt();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, (byte) 0xF4, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void clearInterruptSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.clearInterrupt();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, (byte) 0xF5, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    @Test
    void resetSendsCorrectCommand() throws InterruptedException {
        helper.preloadNoResponse();
        plate.reset();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x0F, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    /* --------- Parameter Validation --------- */

    @Test
    void getDigitalInputRejectsInvalidChannels() {
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(0));
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(9));
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(-1));
    }

    @Test
    void getFrequencyRejectsInvalidChannels() {
        assertThrows(InvalidParameterException.class, () -> plate.getFrequency(0));
        assertThrows(InvalidParameterException.class, () -> plate.getFrequency(7));
    }

    @Test
    void enableDigitalInputEventAcceptsFullRange() {
        for (int bit = 1; bit <= 8; bit++) {
            helper.preloadNoResponse();
            final int b = bit;
            assertDoesNotThrow(() -> plate.enableDigitalInputEvent(b, InterruptEdge.BOTH_EDGES));
        }
    }
}

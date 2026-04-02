package com.nahuellofeudo.piplates.relayplate;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlateTestHelper;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RELAYPlate2.
 * Verifies SPI command bytes and response parsing against Python v11 (RELAYplate2).
 * Base address: 72 (0x48). Relays 1-8, relay param sent as relay-1 (0-indexed).
 */
class RELAYPlate2Test {

    private static Context mockContext;
    private static PiPlateTestHelper helper;
    private static RELAYPlate2 plate;

    private static final int BASE_ADDR = 72;
    private static final int PLATE_ADDR = 0;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = PiPlateTestHelper.createMockContext();
        plate = new RELAYPlate2(mockContext, PLATE_ADDR);
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

    /* --------- Command Byte Verification --------- */

    @Test
    void relayOnSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.relayOn(1);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x10, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayOnRelay8SendsCorrectParam() {
        helper.preloadNoResponse();
        plate.relayOn(8);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x10, 0x07, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayOffSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.relayOff(3);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x11, 0x02, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayToggleSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.relayToggle(5);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x12, 0x04, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayAllSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.relayAll(0xFF);
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x13, (byte) 0xFF, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayStateSendsCorrectCommandAndParsesResponse() {
        // Preload response: relay state byte = 0xA5 (relays 1,3,6,8 on)
        helper.preloadResponse((byte) 0xA5);
        int state = plate.relayState();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x14, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(0xA5, state);
    }

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
    void relayOnValidRange() {
        for (int relay = 1; relay <= 8; relay++) {
            helper.preloadNoResponse();
            final int r = relay;
            assertDoesNotThrow(() -> plate.relayOn(r));
        }
    }

    @Test
    void relayOnInvalidRange() {
        assertThrows(InvalidParameterException.class, () -> plate.relayOn(0));
        assertThrows(InvalidParameterException.class, () -> plate.relayOn(9));
        assertThrows(InvalidParameterException.class, () -> plate.relayOn(-1));
    }

    @Test
    void relayAllValidRange() {
        helper.preloadNoResponse();
        assertDoesNotThrow(() -> plate.relayAll(0));
        helper.preloadNoResponse();
        assertDoesNotThrow(() -> plate.relayAll(255));
    }

    @Test
    void relayAllInvalidRange() {
        assertThrows(InvalidParameterException.class, () -> plate.relayAll(-1));
        assertThrows(InvalidParameterException.class, () -> plate.relayAll(256));
    }

    /* --------- Address Validation --------- */

    @Test
    void rejectsInvalidAddress() {
        assertThrows(InvalidAddressException.class, () -> new RELAYPlate2(mockContext, 8));
        assertThrows(InvalidAddressException.class, () -> new RELAYPlate2(mockContext, -1));
    }

    @Test
    void addressIncludedInCommand() throws InvalidAddressException {
        var plate3 = new RELAYPlate2(mockContext, 3);
        helper.clearSpiBuffer();
        helper.preloadNoResponse();
        plate3.relayOn(1);
        byte[] cmd = helper.getLastCommandPacket();
        assertEquals((byte) (BASE_ADDR + 3), cmd[0]);
    }
}

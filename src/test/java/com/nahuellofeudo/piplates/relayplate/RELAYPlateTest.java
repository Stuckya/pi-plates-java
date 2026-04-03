package com.nahuellofeudo.piplates.relayplate;

import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.PiPlateTestHelper;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RELAYPlate (7-relay version).
 * Reference: Pi-Plates Python library v11 (RELAYplate)
 * Base address: 24. Relays 1-7. No relay-1 offset (unlike RELAYPlate2).
 */
class RELAYPlateTest {

    private static Context mockContext;
    private static PiPlateTestHelper helper;
    private static RELAYPlate plate;

    private static final int BASE_ADDR = 24;
    private static final int PLATE_ADDR = 0;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = PiPlateTestHelper.createMockContext();
        plate = new RELAYPlate(mockContext, PLATE_ADDR);
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

    @Test
    void relayOnSendsRelayDirectly() {
        // RELAYPlate sends relay number directly (not relay-1 like RELAYPlate2)
        helper.preloadNoResponse();
        plate.relayOn(1);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x10, 0x01, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayOnRelay7() {
        helper.preloadNoResponse();
        plate.relayOn(7);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x10, 0x07, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayOffSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.relayOff(4);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x11, 0x04, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayToggleSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.relayToggle(2);
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x12, 0x02, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayAllSendsCorrectCommand() {
        helper.preloadNoResponse();
        plate.relayAll(0x7F); // all 7 relays on
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x13, 0x7F, 0x00},
                helper.getLastCommandPacket()
        );
    }

    @Test
    void relayStateParsesResponse() {
        helper.preloadResponse((byte) 0x55);
        int state = plate.relayState();
        assertArrayEquals(
                new byte[]{(byte) BASE_ADDR, 0x14, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(0x55, state);
    }

    @Test
    void relayStateReturnsUnsignedValue() {
        helper.preloadResponse((byte) 0xFF);
        int state = plate.relayState();
        assertEquals(0xFF, state);
    }

    @Test
    void resetSendsCorrectCommand() throws InterruptedException {
        helper.preloadNoResponse();
        plate.reset();
        assertArrayEquals(new byte[]{(byte) BASE_ADDR, 0x0F, 0x00, 0x00}, helper.getLastCommandPacket());
    }

    /* --------- Parameter Validation --------- */

    @Test
    void relayOnRejectsOutOfRange() {
        assertThrows(InvalidParameterException.class, () -> plate.relayOn(0));
        assertThrows(InvalidParameterException.class, () -> plate.relayOn(8)); // Only 7 relays
    }

    @Test
    void relayAllRejectsOutOfRange() {
        assertThrows(InvalidParameterException.class, () -> plate.relayAll(-1));
        assertThrows(InvalidParameterException.class, () -> plate.relayAll(128)); // Max 127 for 7 relays
    }
}

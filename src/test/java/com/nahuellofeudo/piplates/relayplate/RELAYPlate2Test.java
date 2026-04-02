package com.nahuellofeudo.piplates.relayplate;

import com.nahuellofeudo.piplates.InvalidParameterException;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RELAYPlate2 validation.
 * Reference: Pi-Plates Python library v11 (RELAYplate2)
 */
class RELAYPlate2Test {

    private static Context mockContext;
    private static RELAYPlate2 plate;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = Pi4J.newAutoContext();
        plate = new RELAYPlate2(mockContext, 0);
    }

    @AfterAll
    static void tearDown() {
        mockContext.shutdown();
    }

    /* Relay validation — Python: relay 1-8 */

    @Test
    void relayOnValidRange() {
        for (int relay = 1; relay <= 8; relay++) {
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
    void relayOffValidRange() {
        for (int relay = 1; relay <= 8; relay++) {
            final int r = relay;
            assertDoesNotThrow(() -> plate.relayOff(r));
        }
    }

    @Test
    void relayToggleValidRange() {
        for (int relay = 1; relay <= 8; relay++) {
            final int r = relay;
            assertDoesNotThrow(() -> plate.relayToggle(r));
        }
    }

    /* relayAll validation — Python: 0-255 (8 relays) */

    @Test
    void relayAllValidRange() {
        assertDoesNotThrow(() -> plate.relayAll(0));
        assertDoesNotThrow(() -> plate.relayAll(127));
        assertDoesNotThrow(() -> plate.relayAll(255));
    }

    @Test
    void relayAllInvalidRange() {
        assertThrows(InvalidParameterException.class, () -> plate.relayAll(-1));
        assertThrows(InvalidParameterException.class, () -> plate.relayAll(256));
    }
}

package com.nahuellofeudo.piplates.daqcplate;

import com.nahuellofeudo.piplates.InvalidParameterException;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DAQCPlate validation (especially the validateDINBit bug fix).
 * Reference: Pi-Plates Python library v11 (DAQCplate)
 */
class DAQCPlateTest {

    private static Context mockContext;
    private static DAQCPlate plate;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = Pi4J.newAutoContext();
        plate = new DAQCPlate(mockContext, 0);
    }

    @AfterAll
    static void tearDown() {
        mockContext.shutdown();
    }

    /* Bug fix verification: validateDINBit now accepts 0-7 (was limited to 0-1)
       Python: assert ((din>=0) and (din<=7)) */

    @Test
    void getDINBitAcceptsFullRange() {
        // Bits 0-7 should not throw InvalidParameterException
        for (int bit = 0; bit <= 7; bit++) {
            final int b = bit;
            assertDoesNotThrow(() -> {
                try {
                    plate.getDigitalInput(b);
                } catch (InvalidParameterException e) {
                    throw e;
                } catch (Exception e) {
                    // TimeoutException from mock SPI is expected
                }
            });
        }
    }

    @Test
    void getDINBitRejectsOutOfRange() {
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(-1));
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(8));
    }

    /* enableDINInterrupt uses the same validateDINBit */

    @Test
    void enableDINInterruptAcceptsFullRange() {
        for (int bit = 0; bit <= 7; bit++) {
            final int b = bit;
            assertDoesNotThrow(() -> {
                try {
                    plate.enableDigitalInputInterrupt(b, InterruptEdge.BOTH_EDGES);
                } catch (InvalidParameterException e) {
                    throw e;
                } catch (Exception e) {
                    // Expected from mock
                }
            });
        }
    }

    @Test
    void enableDINInterruptRejectsOutOfRange() {
        assertThrows(InvalidParameterException.class,
                () -> plate.enableDigitalInputInterrupt(8, InterruptEdge.BOTH_EDGES));
    }
}

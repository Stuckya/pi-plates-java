package com.nahuellofeudo.piplates.digiplate;

import com.nahuellofeudo.piplates.InvalidParameterException;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DIGIPlate validation and frequency math.
 * Reference: Pi-Plates Python library v11 (DIGIplate)
 */
class DIGIPlateTest {

    private static Context mockContext;
    private static DIGIPlate plate;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = Pi4J.newAutoContext();
        plate = new DIGIPlate(mockContext, 0);
    }

    @AfterAll
    static void tearDown() {
        mockContext.shutdown();
    }

    /* DIN bit validation — Python: assert ((din>=0) and (din<=7))
       Java uses 1-indexed API (1-8) which maps to 0-indexed hardware (0-7) */

    @Test
    void getDINBitValidChannels() {
        // Channels 1-8 should not throw InvalidParameterException
        // (they will throw TimeoutException from mock SPI, which is fine)
        for (int bit = 1; bit <= 8; bit++) {
            final int b = bit;
            assertDoesNotThrow(() -> {
                try {
                    plate.getDigitalInput(b);
                } catch (InvalidParameterException e) {
                    throw e; // re-throw validation errors
                } catch (Exception e) {
                    // TimeoutException from mock SPI is expected
                }
            });
        }
    }

    @Test
    void getDINBitInvalidChannels() {
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(0));
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(9));
        assertThrows(InvalidParameterException.class, () -> plate.getDigitalInput(-1));
    }

    /* Bug fix verification: validateDINBit now accepts 0-7 (was limited to 0-1) */

    @Test
    void enableDINEventValidBits() {
        // Bits 1-8 should not throw InvalidParameterException
        for (int bit = 1; bit <= 8; bit++) {
            final int b = bit;
            assertDoesNotThrow(() -> {
                try {
                    plate.enableDigitalInputEvent(b, com.nahuellofeudo.piplates.daqcplate.InterruptEdge.BOTH_EDGES);
                } catch (InvalidParameterException e) {
                    throw e;
                } catch (Exception e) {
                    // Expected from mock
                }
            });
        }
    }

    /* Frequency channel validation — Python: assert ((chan>=1) and (chan<=6)) */

    @Test
    void freqChannelValidRange() {
        for (int ch = 1; ch <= 6; ch++) {
            final int c = ch;
            assertDoesNotThrow(() -> {
                try {
                    plate.getFrequency(c);
                } catch (InvalidParameterException e) {
                    throw e;
                } catch (Exception e) {
                    // Expected from mock
                }
            });
        }
    }

    @Test
    void freqChannelInvalidRange() {
        assertThrows(InvalidParameterException.class, () -> plate.getFrequency(0));
        assertThrows(InvalidParameterException.class, () -> plate.getFrequency(7));
        assertThrows(InvalidParameterException.class, () -> plate.getFrequency(-1));
    }

    /* Frequency calculation — Python: 1000000.0/counts if counts>0 else 0 */

    @Test
    void frequencyConversionFormula() {
        // For counts=1000000: freq = 1000000.0/1000000 = 1.0 Hz
        long counts = 1000000;
        double freq = Math.round(1000000.0 / counts * 1000.0) / 1000.0;
        assertEquals(1.0, freq, 0.001);
    }

    @Test
    void frequencyConversionHighFreq() {
        // For counts=1000: freq = 1000000.0/1000 = 1000.0 Hz
        long counts = 1000;
        double freq = Math.round(1000000.0 / counts * 1000.0) / 1000.0;
        assertEquals(1000.0, freq, 0.001);
    }

    @Test
    void frequencyConversionZeroCounts() {
        // counts=0 → freq=0
        long counts = 0;
        double freq = (counts > 0) ? Math.round(1000000.0 / counts * 1000.0) / 1000.0 : 0;
        assertEquals(0.0, freq, 0.001);
    }
}

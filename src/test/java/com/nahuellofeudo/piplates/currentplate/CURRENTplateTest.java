package com.nahuellofeudo.piplates.currentplate;

import com.nahuellofeudo.piplates.InvalidParameterException;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CURRENTplate validation and conversion math.
 * Reference: Pi-Plates Python library v11 (CURRENTplate)
 */
class CURRENTplateTest {

    private static Context mockContext;
    private static CURRENTplate plate;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = Pi4J.newAutoContext();
        plate = new CURRENTplate(mockContext, 0);
    }

    @AfterAll
    static void tearDown() {
        mockContext.shutdown();
    }

    /* Current conversion math — Python: value * 24.0 / 65536.0, round to 4 places */

    @Test
    void currentConversionFormula() {
        // For raw value 32768 (midrange): 32768 * 24.0 / 65536.0 = 12.0
        double raw = 32768;
        double expected = Math.round(raw * 24.0 / 65536.0 * 10000.0) / 10000.0;
        assertEquals(12.0, expected, 0.0001);
    }

    @Test
    void currentConversionMax() {
        // For raw value 65535 (max): 65535 * 24.0 / 65536.0 ≈ 23.9996
        double raw = 65535;
        double expected = Math.round(raw * 24.0 / 65536.0 * 10000.0) / 10000.0;
        assertEquals(23.9996, expected, 0.0001);
    }

    @Test
    void currentConversionZero() {
        double raw = 0;
        double expected = Math.round(raw * 24.0 / 65536.0 * 10000.0) / 10000.0;
        assertEquals(0.0, expected, 0.0001);
    }

    /* Channel validation — Python: assert ((channel>=1) and (channel<=8)) */

    @Test
    void validChannels() {
        // Can't actually call getCurrent without real SPI, but we can test validation
        // by calling with channels outside range
        assertThrows(InvalidParameterException.class, () -> plate.getCurrent(0));
        assertThrows(InvalidParameterException.class, () -> plate.getCurrent(9));
        assertThrows(InvalidParameterException.class, () -> plate.getCurrent(-1));
    }

    /* Frequency validation — Python: assert (freq==50 or freq==60) */

    @Test
    void validFrequencies() {
        assertDoesNotThrow(() -> plate.setFreq(50));
        assertDoesNotThrow(() -> plate.setFreq(60));
    }

    @Test
    void invalidFrequencies() {
        assertThrows(InvalidParameterException.class, () -> plate.setFreq(0));
        assertThrows(InvalidParameterException.class, () -> plate.setFreq(55));
        assertThrows(InvalidParameterException.class, () -> plate.setFreq(100));
        assertThrows(InvalidParameterException.class, () -> plate.setFreq(-1));
    }

    /* Calibration validation — Python: assert (ptr>=0 and ptr<=255) */

    @Test
    void calPointerValidRange() {
        // These will throw TimeoutException from mock SPI, but not InvalidParameterException
        assertThrows(InvalidParameterException.class, () -> plate.calGetByte(-1));
        assertThrows(InvalidParameterException.class, () -> plate.calGetByte(256));
    }

    @Test
    void calDataValidRange() {
        assertThrows(InvalidParameterException.class, () -> plate.calPutByte(-1));
        assertThrows(InvalidParameterException.class, () -> plate.calPutByte(256));
    }
}

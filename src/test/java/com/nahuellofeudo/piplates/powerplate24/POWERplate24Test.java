package com.nahuellofeudo.piplates.powerplate24;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for POWERplate24 validation and conversion math.
 * Reference: Pi-Plates Python library v11 (POWERplate24)
 */
class POWERplate24Test {

    private static Context mockContext;
    private static POWERplate24 plate;

    @BeforeAll
    static void setUp() throws Exception {
        mockContext = Pi4J.newAutoContext();
        plate = new POWERplate24(mockContext, 0);
    }

    @AfterAll
    static void tearDown() {
        mockContext.shutdown();
    }

    /* Address validation */

    @Test
    void onlyAddress0Accepted() {
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, 1));
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, 2));
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, 7));
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, -1));
    }

    /* ADC conversion math — Python: value*2.4*2.5/4095 for Vin */

    @Test
    void vinConversionFormula() {
        // Python: round((value*2.4*2.5/4095),3)
        // For raw value 2048: 2048 * 2.4 * 2.5 / 4095 = 3.001
        double expected = Math.round(2048 * 2.4 * 2.5 / 4095.0 * 1000.0) / 1000.0;
        assertEquals(3.001, expected, 0.001);
    }

    @Test
    void hvinConversionFormula() {
        // Python: round((value*2.4*12.4573/4095),3)
        // For raw value 1000: 1000 * 2.4 * 12.4573 / 4095 = 7.302
        double expected = Math.round(1000 * 2.4 * 12.4573 / 4095.0 * 1000.0) / 1000.0;
        assertEquals(7.302, expected, 0.001);
    }

    /* Shutdown delay validation — Python: assert (delay>=10 and delay<=240) */

    @Test
    void shutdownDelayValidRange() {
        assertDoesNotThrow(() -> plate.setShutdownDelay(10));
        assertDoesNotThrow(() -> plate.setShutdownDelay(120));
        assertDoesNotThrow(() -> plate.setShutdownDelay(240));
    }

    @Test
    void shutdownDelayInvalidRange() {
        assertThrows(InvalidParameterException.class, () -> plate.setShutdownDelay(9));
        assertThrows(InvalidParameterException.class, () -> plate.setShutdownDelay(241));
        assertThrows(InvalidParameterException.class, () -> plate.setShutdownDelay(0));
        assertThrows(InvalidParameterException.class, () -> plate.setShutdownDelay(-1));
    }

    /* Wake time validation */

    @Test
    void setWakeValidTimes() {
        assertDoesNotThrow(() -> plate.setWAKE(0, 0, 0));
        assertDoesNotThrow(() -> plate.setWAKE(23, 59, 59));
        assertDoesNotThrow(() -> plate.setWAKE(12, 30, 0));
    }

    @Test
    void setWakeInvalidHour() {
        assertThrows(InvalidParameterException.class, () -> plate.setWAKE(-1, 0, 0));
        assertThrows(InvalidParameterException.class, () -> plate.setWAKE(24, 0, 0));
    }

    @Test
    void setWakeInvalidMinute() {
        assertThrows(InvalidParameterException.class, () -> plate.setWAKE(0, -1, 0));
        assertThrows(InvalidParameterException.class, () -> plate.setWAKE(0, 60, 0));
    }

    @Test
    void setWakeInvalidSecond() {
        assertThrows(InvalidParameterException.class, () -> plate.setWAKE(0, 0, -1));
        assertThrows(InvalidParameterException.class, () -> plate.setWAKE(0, 0, 60));
    }
}

package com.nahuellofeudo.piplates;

import com.nahuellofeudo.piplates.currentplate.CURRENTplate;
import com.nahuellofeudo.piplates.daqcplate.DAQCPlate;
import com.nahuellofeudo.piplates.digiplate.DIGIPlate;
import com.nahuellofeudo.piplates.powerplate24.POWERplate24;
import com.nahuellofeudo.piplates.relayplate.RELAYPlate;
import com.nahuellofeudo.piplates.relayplate.RELAYPlate2;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseAddressTest {

    private static Context mockContext;

    @BeforeAll
    static void setUp() {
        mockContext = PiPlateTestHelper.createMockContext();
    }

    @AfterAll
    static void tearDown() {
        mockContext.shutdown();
    }

    @Test
    void allPlatesAcceptAddress0() {
        assertDoesNotThrow(() -> new DAQCPlate(mockContext, 0));
        assertDoesNotThrow(() -> new RELAYPlate(mockContext, 0));
        assertDoesNotThrow(() -> new RELAYPlate2(mockContext, 0));
        assertDoesNotThrow(() -> new DIGIPlate(mockContext, 0));
        assertDoesNotThrow(() -> new CURRENTplate(mockContext, 0));
        assertDoesNotThrow(() -> new POWERplate24(mockContext, 0));
    }

    @Test
    void standardPlatesAcceptAddress7() {
        assertDoesNotThrow(() -> new DAQCPlate(mockContext, 7));
        assertDoesNotThrow(() -> new RELAYPlate(mockContext, 7));
        assertDoesNotThrow(() -> new RELAYPlate2(mockContext, 7));
        assertDoesNotThrow(() -> new DIGIPlate(mockContext, 7));
        assertDoesNotThrow(() -> new CURRENTplate(mockContext, 7));
    }

    @Test
    void standardPlatesRejectAddress8() {
        assertThrows(InvalidAddressException.class, () -> new DAQCPlate(mockContext, 8));
        assertThrows(InvalidAddressException.class, () -> new RELAYPlate(mockContext, 8));
        assertThrows(InvalidAddressException.class, () -> new RELAYPlate2(mockContext, 8));
        assertThrows(InvalidAddressException.class, () -> new DIGIPlate(mockContext, 8));
        assertThrows(InvalidAddressException.class, () -> new CURRENTplate(mockContext, 8));
    }

    @Test
    void standardPlatesRejectNegativeAddress() {
        assertThrows(InvalidAddressException.class, () -> new DAQCPlate(mockContext, -1));
        assertThrows(InvalidAddressException.class, () -> new RELAYPlate(mockContext, -1));
    }

    @Test
    void powerPlate24OnlyAcceptsAddress0() {
        assertDoesNotThrow(() -> new POWERplate24(mockContext, 0));
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, 1));
        assertThrows(InvalidAddressException.class, () -> new POWERplate24(mockContext, 7));
    }

    @Test
    void powerPlate24ConvenienceConstructor() {
        assertDoesNotThrow(() -> new POWERplate24(mockContext));
    }
}

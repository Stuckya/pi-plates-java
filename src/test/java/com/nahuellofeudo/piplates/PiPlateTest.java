package com.nahuellofeudo.piplates;

import com.nahuellofeudo.piplates.relayplate.RELAYPlate2;
import com.pi4j.context.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PiPlate base class methods (using RELAYPlate2 as a concrete subclass).
 * Verifies getHardwareRevision, getFirmwareRevision, getId, getAddress.
 */
class PiPlateTest {

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

    /* --------- Hardware Revision --------- */

    @Test
    void getHardwareRevisionSendsCorrectCommand() throws PiPlateException {
        // Revision byte: upper nibble=whole, lower nibble=decimal
        // 0x21 → 2.1
        helper.preloadResponse((byte) 0x21);
        double rev = plate.getHardwareRevision();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x02, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(2.1, rev, 0.001);
    }

    @Test
    void getHardwareRevisionParsesVersionByte() throws PiPlateException {
        // 0x35 → 3.5
        helper.preloadResponse((byte) 0x35);
        assertEquals(3.5, plate.getHardwareRevision(), 0.001);
    }

    /* --------- Firmware Revision --------- */

    @Test
    void getFirmwareRevisionSendsCorrectCommand() throws PiPlateException {
        helper.preloadResponse((byte) 0x12);
        double rev = plate.getFirmwareRevision();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x03, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals(1.2, rev, 0.001);
    }

    /* --------- Get Address --------- */

    @Test
    void getAddressSendsCorrectCommand() throws PiPlateException {
        helper.preloadResponse((byte) 0x48); // 72 + 0 = 0x48
        byte addr = plate.getAddress();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x00, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals((byte) 0x48, addr);
    }

    /* --------- Get ID --------- */

    @Test
    void getIdSendsCorrectCommand() {
        // 20-byte response: "Pi-Plates RELAYPlate" + null terminator padding
        byte[] idBytes = "Pi-Plates".getBytes();
        byte[] response = new byte[20];
        System.arraycopy(idBytes, 0, response, 0, idBytes.length);
        // Rest is zeros (null terminator)
        helper.preloadResponse(response);
        String id = plate.getId();
        assertArrayEquals(
                new byte[]{(byte) (BASE_ADDR + PLATE_ADDR), 0x01, 0x00, 0x00},
                helper.getLastCommandPacket()
        );
        assertEquals("Pi-Plates", id);
    }

    @Test
    void getIdHandlesFullString() {
        byte[] response = "12345678901234567890".getBytes(); // exactly 20 chars, no null
        helper.preloadResponse(response);
        String id = plate.getId();
        assertEquals("12345678901234567890", id);
    }
}

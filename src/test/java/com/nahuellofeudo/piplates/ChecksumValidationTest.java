package com.nahuellofeudo.piplates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests checksum validation logic.
 * Reference: Pi-Plates Python library v11 (CMD.ppCMD2 checksum logic)
 *
 * Python checksum: ~resp[bytesToReturn] & 0xFF == sum(resp[0:bytesToReturn]) & 0xFF
 */
class ChecksumValidationTest {

    @Test
    void validChecksumCalculation() {
        // Data bytes: [0x10, 0x20] → sum = 0x30
        // Checksum byte should be ~0x30 & 0xFF = 0xCF
        int sum = 0x10 + 0x20;
        int expectedChecksum = ~sum & 0xFF;
        assertEquals(0xCF, expectedChecksum);
    }

    @Test
    void checksumForZeroData() {
        // Data bytes: [0x00, 0x00] → sum = 0x00
        // Checksum byte: ~0x00 & 0xFF = 0xFF
        int sum = 0;
        int expectedChecksum = ~sum & 0xFF;
        assertEquals(0xFF, expectedChecksum);
    }

    @Test
    void checksumForMaxData() {
        // Data bytes: [0xFF, 0xFF] → sum = 0x1FE → & 0xFF = 0xFE
        // Checksum byte: ~0xFE & 0xFF = 0x01
        int sum = 0xFF + 0xFF;
        int expectedChecksum = ~(sum & 0xFF) & 0xFF;
        assertEquals(0x01, expectedChecksum);

        // Verify the validation logic matches Python
        int checksumByte = 0x01;
        assertTrue((~checksumByte & 0xFF) == (sum & 0xFF));
    }

    @Test
    void checksumForSingleByte() {
        // Data: [0x42] → sum = 0x42
        // Checksum: ~0x42 & 0xFF = 0xBD
        int sum = 0x42;
        int checksumByte = ~sum & 0xFF;
        assertEquals(0xBD, checksumByte);
        assertTrue((~checksumByte & 0xFF) == (sum & 0xFF));
    }

    @Test
    void invalidChecksumDetected() {
        // Data: [0x10, 0x20] → sum = 0x30
        // Wrong checksum: 0x00 (should be 0xCF)
        int sum = 0x10 + 0x20;
        int wrongChecksum = 0x00;
        assertFalse((~wrongChecksum & 0xFF) == (sum & 0xFF));
    }
}

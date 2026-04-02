package com.nahuellofeudo.piplates;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.plugin.mock.platform.MockPlatform;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalInput;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalInputProvider;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalOutputProvider;
import com.pi4j.plugin.mock.provider.spi.MockSpi;
import com.pi4j.plugin.mock.provider.spi.MockSpiProvider;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Test helper that enables end-to-end testing of PiPlate commands using pi4j-plugin-mock.
 *
 * Sets ACK pin LOW so ppCommand never times out, and provides methods to
 * pre-load SPI response data and verify command bytes sent to the bus.
 */
public class PiPlateTestHelper {

    private final Context context;
    private final MockSpi mockSpi;
    private final MockDigitalInput mockAck;

    /**
     * Creates a mock Pi4J context with only mock providers (no FFM/hardware).
     * Resets PiPlate static fields to force re-initialization with this context.
     */
    public static Context createMockContext() {
        // Reset PiPlate's static GPIO/SPI fields so they re-initialize with the new context
        resetPiPlateStatics();

        return Pi4J.newContextBuilder()
                .noAutoDetect()
                .add(new MockPlatform())
                .add(
                        MockDigitalInputProvider.newInstance(),
                        MockDigitalOutputProvider.newInstance(),
                        MockSpiProvider.newInstance()
                )
                .build();
    }

    public PiPlateTestHelper(Context context) {
        this.context = context;
        this.mockSpi = (MockSpi) context.io("SPI1");
        this.mockAck = (MockDigitalInput) context.io("Ack");
        // Keep ACK LOW so ppCommand's acknowledgmentTimedOut() returns immediately
        mockAck.mockState(DigitalState.LOW);
    }

    /**
     * Pre-loads the mock SPI buffer so ppCommand can read response data.
     * Adds 4 padding bytes (consumed by sendCommand's transfer) followed by the
     * data bytes and a valid checksum byte.
     *
     * @param data the response data bytes the plate method should "receive"
     */
    public void preloadResponse(byte... data) {
        ArrayDeque<Byte> raw = getRawBuffer();
        // 4 padding bytes consumed by sendCommand's spi.transfer(packet, 4)
        for (int i = 0; i < 4; i++) {
            raw.add((byte) 0);
        }
        // Response data
        int sum = 0;
        for (byte b : data) {
            raw.add(b);
            sum += (b & 0xFF);
        }
        // Valid checksum: ~sum & 0xFF
        raw.add((byte) (~sum & 0xFF));
    }

    /**
     * Pre-loads the mock SPI buffer for a command that expects no response.
     * Adds 4 padding bytes consumed by sendCommand's transfer.
     */
    public void preloadNoResponse() {
        ArrayDeque<Byte> raw = getRawBuffer();
        for (int i = 0; i < 4; i++) {
            raw.add((byte) 0);
        }
    }

    /**
     * Reads the first 4 bytes from the mock SPI buffer — the command packet
     * that was sent by ppCommand: [baseAddr+addr, command, param1, param2].
     */
    public byte[] getLastCommandPacket() {
        byte[] all = mockSpi.readEntireMockBuffer();
        if (all.length >= 4) {
            return Arrays.copyOf(all, 4);
        }
        return all;
    }

    /**
     * Reads ALL bytes from the mock SPI buffer (command + zero padding from reads).
     */
    public byte[] getAllSpiBytes() {
        return mockSpi.readEntireMockBuffer();
    }

    /**
     * Clears the mock SPI buffer. Call between tests.
     */
    public void clearSpiBuffer() {
        getRawBuffer().clear();
        mockSpi.readEntireMockBuffer();
    }

    @SuppressWarnings("unchecked")
    private ArrayDeque<Byte> getRawBuffer() {
        try {
            Field rawField = MockSpi.class.getDeclaredField("raw");
            rawField.setAccessible(true);
            return (ArrayDeque<Byte>) rawField.get(mockSpi);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access MockSpi.raw buffer", e);
        }
    }

    /**
     * Resets PiPlate's private static GPIO/SPI fields to null,
     * forcing re-initialization on the next PiPlate constructor call.
     */
    private static void resetPiPlateStatics() {
        try {
            for (String fieldName : new String[]{"frame", "serviceRequest", "ack", "spi"}) {
                Field f = PiPlate.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(null, null);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reset PiPlate static fields", e);
        }
    }
}

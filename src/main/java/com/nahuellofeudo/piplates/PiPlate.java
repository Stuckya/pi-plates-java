package com.nahuellofeudo.piplates;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalInputConfig;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalOutputConfig;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.DigitalStateChangeEvent;
import com.pi4j.io.gpio.digital.PullResistance;
import com.pi4j.io.spi.Spi;
import com.pi4j.io.spi.SpiBus;
import com.pi4j.io.spi.SpiConfig;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public abstract class PiPlate {

    private static final int GPIO_FRAME = 25;
    private static final int GPIO_SRQ = 22;
    private static final int GPIO_ACK = 23;

    private static final Duration COMMAND_TIMEOUT = Duration.ofMillis(50);
    private static final Duration DATA_TIMEOUT = Duration.ofMillis(80);

    private static final int COMMAND_GET_ADDRESS = 0x00;
    private static final int COMMAND_GET_HW_REVISION = 0x02;
    private static final int COMMAND_GET_FW_REVISION = 0x03;

    private static final int REVISION_WHOLE_MASK = 0xF0;
    private static final int REVISION_POINT_MASK = 0x0F;

    private static DigitalOutput frame;
    private static DigitalInput serviceRequest;
    private static DigitalInput ack;
    private static Spi spi;

    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    private final int address;

    /**
     * Constructor with dependency-injected Pi4J context
     * @param pi4jContext the Pi4J context to use for GPIO and SPI
     * @param address the plate's address
     * @throws InvalidAddressException when address is outside the valid range
     */
    public PiPlate(Context pi4jContext, int address) throws InvalidAddressException {
        validateAddress(address);
        this.address = address;
        initializeGPIO(pi4jContext);
    }

    /**
     * Convenience constructor that creates a default Pi4J auto-context.
     * Automatically detects the platform and providers (FFM plugin).
     * @param address the plate's address
     * @throws InvalidAddressException when address is outside [0..7]
     */
    public PiPlate(int address) throws InvalidAddressException {
        this(Pi4J.newAutoContext(), address);
    }

    protected void validateAddress(int address) throws InvalidAddressException {
        if (address < 0 || address > 7) {
            throw new InvalidAddressException("Address must be in the range [0..7]");
        }
    }

    private void initializeGPIO(Context pi4j) {
        if (frame != null) return;

        frame = pi4j.create(buildFrameConfig(pi4j));
        serviceRequest = pi4j.create(buildSRQConfig(pi4j));
        ack = pi4j.create(buildAckConfig(pi4j));
        spi = pi4j.create(buildSpiConfig(pi4j, 1, 500000));

        ack.addListener(event -> {
            if (event.state().isLow()) {
                synchronized (PiPlate.class) {
                    acknowledged.set(true);
                    acknowledged.notify();
                }
            }
        });
    }

    private static DigitalOutputConfig buildFrameConfig(Context pi4j) {
        return DigitalOutput.newConfigBuilder(pi4j)
                .id("Frame")
                .name("Frame")
                .bcm(GPIO_FRAME)
                .initial(DigitalState.LOW)
                .build();
    }

    private static DigitalInputConfig buildSRQConfig(Context pi4j) {
        return DigitalInput.newConfigBuilder(pi4j)
                .id("ServiceRequest")
                .name("ServiceRequest")
                .bcm(GPIO_SRQ)
                .pull(PullResistance.PULL_UP)
                .build();
    }

    private static DigitalInputConfig buildAckConfig(Context pi4j) {
        return DigitalInput.newConfigBuilder(pi4j)
                .id("Ack")
                .name("Ack")
                .bcm(GPIO_ACK)
                .pull(PullResistance.PULL_UP)
                .build();
    }

    private static SpiConfig buildSpiConfig(Context pi4j, int channel, int frequency) {
        return Spi.newConfigBuilder(pi4j)
                .id("SPI" + channel)
                .bus(SpiBus.BUS_0)
                .channel(channel)
                .baud(frequency)
                .build();
    }

    protected boolean isServiceRequest() {
        return serviceRequest.isLow();
    }

    public void registerServiceRequestCallback(Consumer<DigitalStateChangeEvent<DigitalInput>> func) {
        serviceRequest.addListener(func::accept);
    }

    /* --------- SPI Communication --------- */

    /**
     * Sends a command to the plate with no response expected.
     * Equivalent to Python library's {@code ppCMD(addr, cmd, param1, param2, 0)}.
     */
    protected void sendCommand(int command, int parameter1, int parameter2) {
        executeCommand(command, parameter1, parameter2, 0);
    }

    /**
     * Sends a command to the plate and returns the response bytes.
     * Equivalent to Python library's {@code ppCMD(addr, cmd, param1, param2, bytesToReturn)}.
     * @param bytesToReturn number of data bytes expected (must be > 0)
     * @return the response data bytes (checksum already validated and stripped)
     */
    protected byte[] sendQuery(int command, int parameter1, int parameter2, int bytesToReturn) {
        if (bytesToReturn < 1) {
            throw new InvalidParameterException(
                    "bytesToReturn must be > 0; use sendCommand() for commands with no response");
        }
        return executeCommand(command, parameter1, parameter2, bytesToReturn);
    }

    private byte[] executeCommand(int command, int parameter1, int parameter2, int bytesToReturn) {
        byte[] packet = new byte[]{
                (byte) (getBaseAddress() + address),
                (byte) command,
                (byte) parameter1,
                (byte) parameter2
        };

        synchronized (PiPlate.class) {
            frame.high();
            try {
                transferPacket(packet);

                if (acknowledgmentTimedOut(COMMAND_TIMEOUT)) {
                    throw new TimeoutException("Command acknowledgment timed out.");
                }

                if (bytesToReturn == 0) {
                    return null;
                }

                if (acknowledgmentTimedOut(DATA_TIMEOUT)) {
                    throw new TimeoutException("Data acknowledgment timed out.");
                }

                var response = new byte[bytesToReturn + 1];
                readResponse(response);

                int sum = 0;
                for (int i = 0; i < bytesToReturn; i++) {
                    sum += unsigned(response[i]);
                }
                int checksumByte = unsigned(response[bytesToReturn]);
                if ((~checksumByte & 0xFF) != (sum & 0xFF)) {
                    throw new ChecksumException("Response checksum validation failed");
                }

                var data = new byte[bytesToReturn];
                System.arraycopy(response, 0, data, 0, bytesToReturn);
                return data;
            } finally {
                frame.low();
            }
        }
    }

    private void transferPacket(byte[] packet) {
        spi.transfer(packet, packet.length);
    }

    private boolean acknowledgmentTimedOut(Duration timeout) {
        synchronized (PiPlate.class) {
            var startTime = System.nanoTime();
            var timeoutNanos = timeout.toNanos();

            while (ack.isHigh()) {
                var elapsedNanos = System.nanoTime() - startTime;
                var remainingNanos = timeoutNanos - elapsedNanos;

                if (remainingNanos <= 0) {
                    return true;
                }

                try {
                    TimeUnit.NANOSECONDS.timedWait(acknowledged, remainingNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            }
        }

        return false;
    }

    private void readResponse(byte[] response) {
        var transferBuffer = new byte[1];

        for (int i = 0; i < response.length; i++) {
            spi.transfer(transferBuffer, 1);
            response[i] = transferBuffer[0];
        }
    }

    /* --------- System Functions --------- */

    /**
     * Ping the plate
     * @return address byte if the plate is available
     * @throws PiPlateException when plate is missing
     */
    public byte getAddress() throws PiPlateException {
        return sendQuery(COMMAND_GET_ADDRESS, 0, 0, 1)[0];
    }

    /**
     * Returns the hardware revision.
     * Equivalent to Python library's {@code getHWrev(addr)}.
     */
    public double getHardwareRevision() throws PiPlateException {
        return extractRevision(sendQuery(COMMAND_GET_HW_REVISION, 0, 0, 1)[0]);
    }

    /**
     * Returns the firmware version of the plate.
     * Equivalent to Python library's {@code getFWrev(addr)}.
     */
    public double getFirmwareRevision() throws PiPlateException {
        return extractRevision(sendQuery(COMMAND_GET_FW_REVISION, 0, 0, 1)[0]);
    }

    private double extractRevision(byte revisionByte) {
        int whole = (revisionByte & REVISION_WHOLE_MASK) >> 4;
        int point = revisionByte & REVISION_POINT_MASK;
        return whole + (point / 10.0);
    }

    /**
     * Reads and returns the board's identifier string.
     * Command 0x01 is universal across all Pi-Plates board types.
     */
    public String getId() {
        int ID_LENGTH = 20;
        byte[] resp = sendQuery(0x01, 0, 0, ID_LENGTH);
        int length = IntStream.range(0, ID_LENGTH)
                .filter(i -> resp[i] == 0)
                .findFirst()
                .orElse(ID_LENGTH);
        return new String(resp, 0, length);
    }

    /* --------- Validation Utilities --------- */

    /**
     * Validates that a value is within an inclusive range.
     * @param value the value to validate
     * @param min minimum valid value (inclusive)
     * @param max maximum valid value (inclusive)
     * @param name description for the error message
     */
    protected void validateRange(int value, int min, int max, String name) throws InvalidParameterException {
        if (value < min || value > max) {
            throw new InvalidParameterException(name + " must be in the range [" + min + ".." + max + "]");
        }
    }

    /**
     * Converts a signed byte to an unsigned int (0-255).
     */
    public int unsigned(byte val) {
        return val & 0xFF;
    }

    protected abstract int getBaseAddress();
}

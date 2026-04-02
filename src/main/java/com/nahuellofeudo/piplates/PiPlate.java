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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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

    public int address;

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

    public void validateAddress(int address) throws InvalidAddressException {
        if (address < 0 || address > 7) {
            throw new InvalidAddressException("Address must be in the range [0..7]");
        }
    }

    /**
     * Configures the GPIO pins for Frame, SRQ Interrupt, and Ack
     * Initializes the SPI bus
     */
    private void initializeGPIO(Context pi4j) {
        if (frame != null) {
            return; // Already initialized (static resources shared across instances)
        }
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

    /**
     * Send a command to a plate, optionally returning a response
     * @param command command (plate-dependent)
     * @param parameter1 1st parameter (command-dependent)
     * @param parameter2 2nd parameter (command-dependent)
     * @param bytesToReturn number of bytes to read back from the plate as a response
     * @return Optionally return an array of bytes with the plate's response
     */
    public Optional<byte[]> ppCommand(int command, int parameter1, int parameter2, int bytesToReturn) {
        byte[] packet = new byte[]{
                (byte) (getBaseAddress() + address),
                (byte) command,
                (byte) parameter1,
                (byte) parameter2
        };

        synchronized (PiPlate.class) {
            frame.high();
            try {
                sendCommand(packet);

                if (acknowledgmentTimedOut(COMMAND_TIMEOUT)) {
                    throw new TimeoutException("Command acknowledgment timed out.");
                }

                if (bytesToReturn == 0) {
                    return Optional.empty();
                }

                if (acknowledgmentTimedOut(DATA_TIMEOUT)) {
                    throw new TimeoutException("Data acknowledgment timed out.");
                }

                var response = new byte[bytesToReturn + 1];
                readResponse(response);

                // Validate checksum: ~checksum_byte & 0xFF == sum_of_data_bytes & 0xFF
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
                return Optional.of(data);
            } finally {
                frame.low();
            }
        }
    }

    private void sendCommand(byte[] packet) {
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
                    // suspend thread until notified or timeout.
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

    /**
     * Ping the plate
     * @return address + 8 if the plate is available
     * @throws PiPlateException when plate is missing
     */
    public byte getAddress() throws PiPlateException {
        var response = ppCommand(COMMAND_GET_ADDRESS, 0, 0, 1);

        if (response.isEmpty()) {
            // stop execution.
            throw new PiPlateException("PiPlate not found");
        }

        return response.get()[0];
    }

    /**
     * Returns the hardware revision.
     * Equivalent to Python library's {@code getHWrev(addr)}.
     * @return Double containing hardware revision of the plate
     * @throws PiPlateException when revision command fails
     */
    public double getHardwareRevision() throws PiPlateException {
        return getRevision(COMMAND_GET_HW_REVISION, "Failed to retrieve hardware revision");
    }

    /**
     * Returns the firmware version of the plate.
     * Equivalent to Python library's {@code getFWrev(addr)}.
     * @return a double with the firmware version
     * @throws PiPlateException when revision command fails
     */
    public double getFirmwareRevision() throws PiPlateException {
        return getRevision(COMMAND_GET_FW_REVISION, "Failed to retrieve firmware revision");
    }

    private double getRevision(int command, String errorMessage) throws PiPlateException {
        var response = ppCommand(command, 0, 0, 1);

        if (response.isEmpty()) {
            // TODO: do we want to stop execution?
            throw new PiPlateException(errorMessage);
        }

        return extractRevision(response.get()[0]);
    }

    private double extractRevision(byte revisionByte) {
        int whole = (revisionByte & REVISION_WHOLE_MASK) >> 4;
        int point = revisionByte & REVISION_POINT_MASK;

        return whole + (point / 10.0);
    }

    /**
     * Reads and returns the board's identifier string.
     * Command 0x01 is universal across all Pi-Plates board types.
     * @return a string ID read from the board
     */
    public String getId() {
        int ID_LENGTH = 20;
        return ppCommand(0x01, 0, 0, ID_LENGTH)
                .map(resp -> {
                    int length = ID_LENGTH;
                    for (int x = 0; x < ID_LENGTH; x++) {
                        if (resp[x] == 0) {
                            length = x;
                            break;
                        }
                    }
                    return new String(resp, 0, length);
                })
                .orElse("");
    }

    /**
     * Java does not support unsigned values. Bytes in the range 0..255 are interpreted as signed bytes in the range (-128..127).
     * This method converts a byte (0..255) into a Java int with the unsigned value represented by val (0..255)
     * This is necessary so that math with values > 127 does not fail
     * @param val the value to convert to unsigned.
     * @return a 32-bit int with the unsigned value of val
     */
    public int unsigned(byte val) {
        return val & 0xFF;
    }

    protected abstract int getBaseAddress();
}

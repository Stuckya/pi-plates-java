# Pi Plates library
A library to interact with Pi-Plates using Java.

Pi-Plates are a family of stackable and interchangeable add-on circuit boards that allow you to interact with the outside world using your Raspberry Pi (http://www.pi-plates.com).

### Requirements
- **Java 25** or later
- **Raspberry Pi** (Pi 3, Pi 4, Pi 5, Zero 2W) — multiple Pi versions are supported automatically via Pi4J 4.0's FFM plugin
- The user running the application must have read/write access to `/dev/gpiochip*` and `/dev/spidev*` device files (root/sudo is **not** required with the FFM plugin)

This library uses [Pi4J v4](https://pi4j.com/) with the Foreign Function & Memory (FFM) API for direct kernel-level GPIO and SPI access.

### Supported Plates
- `com.nahuellofeudo.piplates.daqcplate.DAQCPlate` — Data acquisition (ADC, DAC, PWM, digital I/O, temperature, range)
- `com.nahuellofeudo.piplates.digiplate.DIGIPlate` — Digital input with event/interrupt support and frequency measurement
- `com.nahuellofeudo.piplates.relayplate.RELAYPlate` — 7-relay control
- `com.nahuellofeudo.piplates.relayplate.RELAYPlate2` — 8-relay control
- `com.nahuellofeudo.piplates.currentplate.CURRENTplate` — 8-channel 4-20mA current loop measurement
- `com.nahuellofeudo.piplates.powerplate24.POWERplate24` — Power management with voltage monitoring, RTC, fan control, scheduled wake-up

### Usage
You interact with a plate by creating an instance of the corresponding class and passing the plate's address to the constructor. For example:

    // DAQC Plate on address 2
    DAQCPlate daqcPlate = new DAQCPlate(2);
    double hwVersion = daqcPlate.getHardwareRevision();
    int analogValue = daqcPlate.getAnalogInput(0);

    // CURRENTplate on address 0
    CURRENTplate current = new CURRENTplate(0);
    double milliamps = current.getI(1);       // Read channel 1
    double[] all = current.getIAll();          // Read all 8 channels

    // POWERplate24 (always address 0)
    POWERplate24 power = new POWERplate24();
    double voltage = power.getHighVoltageIn();           // Read high-voltage input
    power.setFanOn();                                    // Enable cooling fan
    power.setRealTimeClockToNow(TimeZoneType.LOCAL);     // Set RTC to local time

### Custom Pi4J Context
For advanced use cases (testing, custom providers, non-standard Pi setups), you can inject your own Pi4J context via the constructor:

    Context ctx = Pi4J.newContextBuilder()
            .noAutoDetect()
            .add(/* your providers */)
            .build();
    DAQCPlate plate = new DAQCPlate(ctx, 2);

The default no-context constructor uses `Pi4J.newAutoContext()` which auto-detects the platform and FFM providers.

### Thread Safety
The code includes synchronization primitives to avoid SPI bus collisions if multiple threads try to communicate at the same time, even with different plates.

The interfaces themselves are modeled following the same patterns as Pi-Plates' own Python library, although some methods and parameters have been renamed to follow Java naming conventions.

### Installation
The library has not yet been published to Maven Central and is currently considered experimental.

### License
The code is licensed under the GPL license (https://www.gnu.org/licenses/gpl.txt)

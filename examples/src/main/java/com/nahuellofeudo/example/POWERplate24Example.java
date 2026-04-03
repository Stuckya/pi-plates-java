package com.nahuellofeudo.example;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.powerplate24.LedColor;
import com.nahuellofeudo.piplates.powerplate24.POWERplate24;
import com.nahuellofeudo.piplates.powerplate24.TimeZoneType;

public class POWERplate24Example {

    public static void main(String[] args) throws InvalidAddressException, InterruptedException {
        var plate = new POWERplate24();

        System.out.println("POWERplate24");
        System.out.printf("HW revision: %.1f%n", plate.getHardwareRevision());
        System.out.printf("FW revision: %.1f%n", plate.getFirmwareRevision());

        plate.setRealTimeClockToNow(TimeZoneType.LOCAL);
        plate.setLed(LedColor.GREEN);

        while (true) {
            System.out.printf("  5V rail:       %.3f V%n", plate.getVoltageIn());
            System.out.printf("  High voltage:  %.3f V%n", plate.getHighVoltageIn());
            System.out.printf("  Fan state:     %s%n", plate.getFanState() == 1 ? "ON" : "OFF");
            System.out.printf("  Switch state:  %s%n", plate.getSwitchState() == 1 ? "PRESSED" : "RELEASED");
            System.out.println();
            Thread.sleep(2000);
        }
    }
}

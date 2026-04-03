package com.nahuellofeudo.example;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.currentplate.CURRENTplate;

public class CURRENTplateExample {

    public static void main(String[] args) throws InvalidAddressException, InterruptedException {
        var plate = new CURRENTplate(0);

        System.out.println("CURRENTplate at address 0");
        System.out.printf("HW revision: %.1f%n", plate.getHardwareRevision());
        System.out.printf("FW revision: %.1f%n", plate.getFirmwareRevision());

        while (true) {
            double[] readings = plate.getCurrentAll();
            for (int ch = 0; ch < readings.length; ch++) {
                System.out.printf("  Channel %d: %.4f mA%n", ch + 1, readings[ch]);
            }
            System.out.println();
            Thread.sleep(2000);
        }
    }
}

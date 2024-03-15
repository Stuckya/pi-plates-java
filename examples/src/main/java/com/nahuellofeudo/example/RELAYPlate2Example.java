package com.nahuellofeudo.example;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.relayplate.RELAYPlate2;

public class RELAYPlate2Example {
    public static void main(String[] args) throws InvalidAddressException, InvalidParameterException, InterruptedException {
        var relayPlate = new RELAYPlate2(0);

        while (true) {
            relayPlate.relayOn(1);
            Thread.sleep(2500);
            relayPlate.relayOff(1);
        }
    }
}

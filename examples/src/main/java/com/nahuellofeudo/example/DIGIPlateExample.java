package com.nahuellofeudo.example;

import com.nahuellofeudo.piplates.InvalidAddressException;
import com.nahuellofeudo.piplates.InvalidParameterException;
import com.nahuellofeudo.piplates.daqcplate.InterruptEdge;
import com.nahuellofeudo.piplates.digiplate.DIGIPlate;
import com.nahuellofeudo.piplates.relayplate.RELAYPlate2;

public class DIGIPlateExample {

    public static void main(String[] args) throws InvalidAddressException, InvalidParameterException {
        var digiPlate = new DIGIPlate(1);

        digiPlate.enableDigitalInputEvent(1, InterruptEdge.BOTH_EDGES);
        digiPlate.enableDigitalInputEvent(2, InterruptEdge.BOTH_EDGES);
        digiPlate.enableEvents();
        digiPlate.getEventFlags(); // Flushes out old event flags

        digiPlate.registerServiceRequestCallback(event -> {

            if (event.source().isLow()) {

                var eventRegister = digiPlate.getEventFlags();
                var formatted = String.format("%16s", Integer.toBinaryString(eventRegister)).replace(' ', '0');

                System.out.println(formatted);
            }
        });

    }
}

package com.nahuellofeudo.piplates.relayplate;

public interface RelayBoard {
    void relayOn(int relay);
    void relayOff(int relay);
    void relayToggle(int relay);
    void relayAll(int relays);
    int relayState();

    void setLed();
    void clearLed();
    void toggleLed();
    String getId();
}

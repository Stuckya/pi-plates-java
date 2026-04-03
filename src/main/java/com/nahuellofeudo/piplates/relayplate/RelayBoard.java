package com.nahuellofeudo.piplates.relayplate;

/** Common interface for relay-based Pi-Plates boards (RELAYplate and RELAYplate2). */
public interface RelayBoard {

    /**
     * Turns on (closes) the specified relay.
     *
     * @param relay relay number
     */
    void relayOn(int relay);

    /**
     * Turns off (opens) the specified relay.
     *
     * @param relay relay number
     */
    void relayOff(int relay);

    /**
     * Toggles the specified relay; on becomes off and vice versa.
     *
     * @param relay relay number
     */
    void relayToggle(int relay);

    /**
     * Sets all relays at once using a bitmask where bit 0 corresponds to relay 1.
     *
     * @param relays bitmask of desired relay states
     */
    void relayAll(int relays);

    /**
     * Returns the current state of all relays as a bitmask where bit 0 corresponds
     * to relay 1. A {@code 1} bit means the relay is on.
     *
     * @return relay state bitmask
     */
    int relayState();

    /** Turns on the programmable LED. */
    void setLed();

    /** Turns off the programmable LED. */
    void clearLed();

    /** Toggles the programmable LED. */
    void toggleLed();

    /**
     * Returns the board's identifier string (e.g. "Pi-Plates RELAYplate").
     *
     * @return board descriptor string
     */
    String getId();
}

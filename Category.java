package com.pvpclient.module;

public enum Category {
    COMBAT("Combat"),
    VISUALS("Visuals"),
    HUD("HUD"),
    AUTOMATION("Automation"),
    MOVEMENT("Movement"),
    SETTINGS("Settings");

    public final String label;

    Category(String label) { this.label = label; }
}

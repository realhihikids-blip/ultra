package com.pvpclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ModeSetting extends Setting<Integer> {
    private final String[] modes;

    public ModeSetting(String name, int defaultIndex, String... modes) {
        super(name, defaultIndex);
        this.modes = modes;
    }

    public String[] getModes() { return modes; }
    public String getMode() { return modes[value]; }

    public void cycle(int direction) {
        value = Math.floorMod(value + direction, modes.length);
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(getMode()); }

    @Override
    public void fromJson(JsonElement e) {
        String s = e.getAsString();
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(s)) { value = i; return; }
        }
    }
}

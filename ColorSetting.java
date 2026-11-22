package com.pvpclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Stores an RGB value (no alpha). The GUI offers a palette; the JSON config accepts any #RRGGBB. */
public final class ColorSetting extends Setting<Integer> {
    public static final int[] DEFAULT_PALETTE = {
            0x8B5CF6, 0x3B82F6, 0x22D3EE, 0x22C55E, 0xFACC15,
            0xF97316, 0xEF4444, 0xEC4899, 0xFFFFFF, 0x9CA3AF
    };

    private final int[] palette;

    public ColorSetting(String name, int rgb) { this(name, rgb, DEFAULT_PALETTE); }

    public ColorSetting(String name, int rgb, int... palette) {
        super(name, rgb & 0xFFFFFF);
        this.palette = palette;
    }

    public int[] getPalette() { return palette; }
    public int rgb() { return value; }
    public float r() { return ((value >> 16) & 255) / 255f; }
    public float g() { return ((value >> 8) & 255) / 255f; }
    public float b() { return (value & 255) / 255f; }

    @Override public JsonElement toJson() { return new JsonPrimitive(String.format("#%06X", value)); }

    @Override
    public void fromJson(JsonElement e) {
        value = Integer.parseInt(e.getAsString().replace("#", ""), 16) & 0xFFFFFF;
    }
}

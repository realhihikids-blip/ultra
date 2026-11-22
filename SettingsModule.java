package com.pvpclient.module;

import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ColorSetting;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.NumberSetting;

/** Global interface options (always on). */
public final class SettingsModule extends ClientModule {
    public final ModeSetting language = add(new ModeSetting("Language", 1, "English", "Русский"));
    public final NumberSetting guiScale = add(new NumberSetting("GUI Scale", 1.0, 0.6, 1.5, 0.05));
    public final BooleanSetting animations = add(new BooleanSetting("Animations", true));
    public final NumberSetting animSpeed = add(new NumberSetting("Animation Speed", 14, 6, 30, 1));
    public final ColorSetting accentPrimary = add(new ColorSetting("Accent Primary", 0xA855F7));
    public final ColorSetting accentSecondary = add(new ColorSetting("Accent Secondary", 0xE040FB));

    public SettingsModule() {
        super("Interface", "Menu scale, animations and theme colors", Category.SETTINGS);
        markAlwaysOn();
    }
}

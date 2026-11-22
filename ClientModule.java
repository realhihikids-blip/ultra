package com.pvpclient.module;

import com.pvpclient.setting.Setting;
import com.pvpclient.util.PrivateWorldGuard;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Base class: every feature lives in its own subclass. */
public abstract class ClientModule {
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private boolean enabled;
    private boolean alwaysOn;
    private boolean restricted;
    private int keyCode = GLFW.GLFW_KEY_UNKNOWN;

    protected ClientModule(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    protected void markAlwaysOn() { alwaysOn = true; enabled = true; }
    protected void markRestricted() { restricted = true; }
    protected void enabledByDefault() { enabled = true; }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }
    public boolean isAlwaysOn() { return alwaysOn; }
    public boolean isRestricted() { return restricted; }
    public boolean isEnabled() { return enabled; }
    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    /** Enabled and (if restricted) running inside an authorized private environment. */
    public boolean isActive() {
        return enabled && (!restricted || PrivateWorldGuard.isAuthorized(mc));
    }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean value) {
        if (alwaysOn || value == enabled) return;
        enabled = value;
        if (enabled) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}
    /** Called every tick while the module is not running (disabled, locked, or no world). */
    public void onIdle() {}
    public void onHudRender(DrawContext context, float tickDelta) {}
    public void onWorldRender(WorldRenderContext context) {}
}

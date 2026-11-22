package com.pvpclient.module;

import com.pvpclient.PvpClient;
import com.pvpclient.util.Anim;
import com.pvpclient.util.ClickTracker;
import com.pvpclient.util.ItemUser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private final List<ClientModule> modules = new ArrayList<>();
    private final Map<ClientModule, Boolean> keyState = new HashMap<>();

    public void register(ClientModule module) { modules.add(module); }
    public List<ClientModule> all() { return modules; }

    public List<ClientModule> inCategory(Category category) {
        List<ClientModule> out = new ArrayList<>();
        for (ClientModule m : modules) if (m.getCategory() == category) out.add(m);
        return out;
    }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register(this::hud);
        WorldRenderEvents.AFTER_ENTITIES.register(this::world);
    }

    private void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            ItemUser.finish(mc);
            for (ClientModule m : modules) m.onIdle();
            return;
        }
        ItemUser.tick(mc);
        handleKeybinds(mc);
        for (ClientModule m : modules) {
            if (m.isActive()) m.onTick(); else m.onIdle();
        }
    }

    private void handleKeybinds(MinecraftClient mc) {
        if (mc.currentScreen != null) return;
        long window = mc.getWindow().getHandle();
        for (ClientModule m : modules) {
            int key = m.getKeyCode();
            if (key == GLFW.GLFW_KEY_UNKNOWN || m.isAlwaysOn()) continue;
            boolean down = InputUtil.isKeyPressed(window, key);
            boolean was = keyState.getOrDefault(m, false);
            if (down && !was) m.toggle();
            keyState.put(m, down);
        }
    }

    private void hud(DrawContext context, RenderTickCounter counter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Anim.tick();
        SettingsModule s = PvpClient.settings();
        Anim.enabled = s.animations.get();
        Anim.speed = s.animSpeed.get();
        if (mc.player == null || mc.world == null) return;
        ClickTracker.update(mc);
        if (mc.options.hudHidden) return;
        float td = counter.getTickDelta(false);
        for (ClientModule m : modules) {
            if (m.isActive()) m.onHudRender(context, td);
        }
    }

    private void world(WorldRenderContext context) {
        for (ClientModule m : modules) {
            if (m.isActive()) m.onWorldRender(context);
        }
    }
}

package com.pvpclient;

import com.pvpclient.config.ConfigManager;
import com.pvpclient.gui.ClickGuiScreen;
import com.pvpclient.module.ModuleManager;
import com.pvpclient.module.SettingsModule;
import com.pvpclient.module.bot.DungeonBotModule;
import com.pvpclient.module.movement.FlyModule;
import com.pvpclient.module.movement.SpeedModule;
import com.pvpclient.module.combat.AutoBuffModule;
import com.pvpclient.module.combat.CrystalAuraModule;
import com.pvpclient.module.combat.AutoHealModule;
import com.pvpclient.module.combat.EnderPearlTrainerModule;
import com.pvpclient.module.combat.HitboxModule;
import com.pvpclient.module.combat.TargetLockModule;
import com.pvpclient.module.combat.KillAuraModule;
import com.pvpclient.module.combat.TriggerBotModule;
import com.pvpclient.module.combat.TargetStrafeModule;
import com.pvpclient.module.hud.CpsModule;
import com.pvpclient.module.hud.DirectionIndicatorsModule;
import com.pvpclient.module.hud.FpsModule;
import com.pvpclient.module.hud.KeystrokesModule;
import com.pvpclient.module.hud.PingModule;
import com.pvpclient.module.visual.BlockTrailModule;
import com.pvpclient.module.visual.ChestEspModule;
import com.pvpclient.module.visual.FreecamModule;
import com.pvpclient.module.visual.PlayerEspModule;
import com.pvpclient.module.visual.PlayerInfoModule;
import net.fabricmc.api.ClientModInitializer;
import com.pvpclient.module.visual.VanishDetectorModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PvpClient implements ClientModInitializer {
    public static final String MOD_ID = "pvpclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModuleManager modules;
    private static SettingsModule settings;
    private static KeyBinding openGuiKey;
    private static final java.util.List<KeyBinding> OPEN_KEYS = new java.util.ArrayList<>();

    public static ModuleManager modules() { return modules; }
    public static SettingsModule settings() { return settings; }
    public static KeyBinding openGuiKey() { return openGuiKey; }

    /** True if this key press matches any of the menu keys (default: =). */
    public static boolean isOpenKey(int keyCode, int scanCode) {
        for (KeyBinding k : OPEN_KEYS) if (k.matchesKey(keyCode, scanCode)) return true;
        return false;
    }

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        settings = new SettingsModule();

        // Combat (practice mode, private worlds only)
        modules.register(new KillAuraModule());
        modules.register(new CrystalAuraModule());
        modules.register(new TriggerBotModule());
        TargetLockModule targetLock = new TargetLockModule();
        modules.register(targetLock);
        modules.register(new HitboxModule());
        modules.register(new AutoHealModule());
        modules.register(new AutoBuffModule());
        modules.register(new EnderPearlTrainerModule());
        modules.register(new TargetStrafeModule());
        // Visuals
        modules.register(new PlayerEspModule());
        modules.register(new ChestEspModule());
        modules.register(new FreecamModule());
        modules.register(new BlockTrailModule());
        modules.register(new PlayerInfoModule());
        modules.register(new VanishDetectorModule());
        // HUD
        modules.register(new CpsModule());
        modules.register(new FpsModule());
        modules.register(new PingModule());
        modules.register(new KeystrokesModule());
        modules.register(new DirectionIndicatorsModule());
        // Automation (singleplayer / authorized servers only)
        modules.register(new DungeonBotModule());
        // Movement (singleplayer / authorized servers only)
        modules.register(new SpeedModule());
        modules.register(new FlyModule());
        // Settings
        modules.register(settings);

        ConfigManager.load(modules);

        // New key id on purpose: an old saved binding (Right Shift) in options.txt must not override the "=" default.
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvpclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "category.pvpclient"));
        OPEN_KEYS.add(openGuiKey);

        modules.init();

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) targetLock.onPlayerAttack(entity);
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean pressed = false;
            for (KeyBinding k : OPEN_KEYS) {
                while (k.wasPressed()) pressed = true;
            }
            if (pressed && client.currentScreen == null && client.world != null) {
                client.setScreen(new ClickGuiScreen());
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save(modules));

        LOGGER.info("Blip Client loaded. Press = in a world to open the menu.");
    }
}

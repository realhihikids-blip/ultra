package com.pvpclient.module.visual;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A free-flying camera. Your character stays where it is (its input is frozen) while the camera moves
 * in any direction, including through blocks. Disabling Freecam puts the camera back on the player.
 * The camera hooks live in CameraMixin and EntityMixin.
 */
public final class FreecamModule extends ClientModule {
    private static FreecamModule instance;

    private final NumberSetting speed = add(new NumberSetting("Speed", 10.0, 1.0, 40.0, 1.0));
    private final NumberSetting sprintMultiplier = add(new NumberSetting("Sprint Multiplier", 3.0, 1.0, 6.0, 0.5));

    private boolean running;
    private double x, y, z;
    private float yaw, pitch;
    private long lastNanos;
    private Input savedInput;
    private ClientPlayerEntity frozen;

    public FreecamModule() {
        super("Freecam", "Free-flying camera; your character stays where it is", Category.VISUALS);
        instance = this;
        // Runs before the game reads input: swallow attack/use clicks so the frozen character does nothing.
        ClientTickEvents.START_CLIENT_TICK.register(this::blockInteractions);
    }

    // ---------------------------------------------------------------- static access for the mixins

    public static boolean isRunning() { return instance != null && instance.running; }
    public static void step() { if (instance != null) instance.update(); }
    public static double camX() { return instance.x; }
    public static double camY() { return instance.y; }
    public static double camZ() { return instance.z; }
    public static float camYaw() { return instance.yaw; }
    public static float camPitch() { return instance.pitch; }

    public static void look(double dx, double dy) {
        if (instance == null) return;
        instance.yaw += (float) (dx * 0.15);
        instance.pitch = MathHelper.clamp(instance.pitch + (float) (dy * 0.15), -90.0f, 90.0f);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onEnable() { start(); }

    @Override
    protected void onDisable() { stop(); }

    /** Runs while no world is loaded: Freecam never stays on across worlds or restarts. */
    @Override
    public void onIdle() {
        if (isEnabled()) setEnabled(false);
        running = false;
        savedInput = null;
        frozen = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!running) { start(); return; }
        // respawn or dimension change creates a new player object: end Freecam instead of leaving it half-attached
        if (mc.player != frozen) setEnabled(false);
    }

    private void start() {
        if (running || mc.player == null || mc.world == null) return;
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d pos = camera.getPos();
        x = pos.x; y = pos.y; z = pos.z;
        yaw = camera.getYaw();
        pitch = camera.getPitch();

        frozen = mc.player;
        savedInput = frozen.input;
        frozen.input = new Input(); // an empty input: the real character stops moving
        lastNanos = System.nanoTime();
        running = true;
    }

    private void stop() {
        if (running && frozen != null && savedInput != null && mc.player == frozen) {
            frozen.input = savedInput;
        }
        running = false;
        savedInput = null;
        frozen = null;
    }

    // ---------------------------------------------------------------- per-frame camera movement

    private void update() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        if (mc.currentScreen != null) return;

        GameOptions o = mc.options;
        double forward = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double right = (o.rightKey.isPressed() ? 1 : 0) - (o.leftKey.isPressed() ? 1 : 0);
        double up = (o.jumpKey.isPressed() ? 1 : 0) - (o.sneakKey.isPressed() ? 1 : 0);
        if (forward == 0 && right == 0 && up == 0) return;

        double yawR = Math.toRadians(yaw), pitchR = Math.toRadians(pitch);
        Vec3d look = new Vec3d(-Math.sin(yawR) * Math.cos(pitchR), -Math.sin(pitchR), Math.cos(yawR) * Math.cos(pitchR));
        Vec3d side = new Vec3d(-Math.cos(yawR), 0, -Math.sin(yawR));
        Vec3d move = look.multiply(forward).add(side.multiply(right)).add(0, up, 0);
        if (move.lengthSquared() > 1.0E-6) move = move.normalize();

        double v = speed.get() * (o.sprintKey.isPressed() ? sprintMultiplier.get() : 1.0) * dt;
        x += move.x * v;
        y += move.y * v;
        z += move.z * v;
    }

    private void blockInteractions(MinecraftClient client) {
        if (!running || client.player == null) return;
        while (client.options.attackKey.wasPressed()) { /* drop queued clicks */ }
        while (client.options.useKey.wasPressed()) { /* drop queued clicks */ }
        client.options.attackKey.setPressed(false);
        client.options.useKey.setPressed(false);
    }
}

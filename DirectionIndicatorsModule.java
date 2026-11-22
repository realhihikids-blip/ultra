package com.pvpclient.module.hud;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ColorSetting;
import com.pvpclient.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Arrows around the crosshair pointing to the nearest players, with distances. Private worlds only. */
public final class DirectionIndicatorsModule extends ClientModule {
    private final NumberSetting maxPlayers = add(new NumberSetting("Max Players", 3, 1, 5, 1));
    private final NumberSetting radius = add(new NumberSetting("Radius (px)", 55, 25, 140, 1));
    private final NumberSetting arrowSize = add(new NumberSetting("Arrow Size", 9, 4, 20, 1));
    private final NumberSetting maxDistance = add(new NumberSetting("Max Distance", 100, 16, 256, 1));
    private final BooleanSetting showDistance = add(new BooleanSetting("Show Distance", true));
    private final ColorSetting arrowColor = add(new ColorSetting("Arrow Color", 0x8B5CF6));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", 0xFFFFFF));

    public DirectionIndicatorsModule() {
        super("Direction Indicators", "Arrows around the crosshair toward nearby players (private worlds only)", Category.HUD);
        markRestricted();
    }

    @Override
    public void onHudRender(DrawContext c, float tickDelta) {
        if (mc.world == null || mc.player == null) return;
        Camera camera = mc.gameRenderer.getCamera();
        double max = maxDistance.get();

        List<AbstractClientPlayerEntity> near = new ArrayList<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            if (p.distanceTo(mc.player) <= max) near.add(p);
        }
        near.sort(Comparator.comparingDouble(p -> p.distanceTo(mc.player)));

        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        float camYaw = camera.getYaw();
        int size = arrowSize.getInt();
        double r = radius.get();
        int count = Math.min(maxPlayers.getInt(), near.size());

        for (int i = 0; i < count; i++) {
            AbstractClientPlayerEntity p = near.get(i);
            double dx = p.getX() - camera.getPos().x;
            double dz = p.getZ() - camera.getPos().z;
            // 0 = straight ahead, positive = to the right
            float rel = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - camYaw);
            double rad = Math.toRadians(rel);
            float ax = cx + (float) (Math.sin(rad) * r);
            float ay = cy - (float) (Math.cos(rad) * r);

            drawArrow(c, ax, ay, rel, size, 0xFF000000 | arrowColor.get());

            if (showDistance.get()) {
                String text = String.format(Locale.ROOT, "%.0fm", (double) p.distanceTo(mc.player));
                float tx = ax + (float) Math.sin(rad) * (size + 9) - mc.textRenderer.getWidth(text) / 2f;
                float ty = ay - (float) Math.cos(rad) * (size + 9) - 4f;
                c.drawText(mc.textRenderer, text, Math.round(tx), Math.round(ty), 0xFF000000 | textColor.get(), true);
            }
        }
    }

    /** A filled triangle pointing up, rotated by {@code degrees}. */
    private static void drawArrow(DrawContext c, float x, float y, float degrees, int size, int argb) {
        MatrixStack m = c.getMatrices();
        m.push();
        m.translate(x, y, 0f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        int top = -size / 2;
        for (int row = 0; row < size; row++) {
            int half = (int) (row * 0.55);
            c.fill(-half, top + row, half + 1, top + row + 1, argb);
        }
        m.pop();
    }
}

package com.pvpclient.module.visual;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ColorSetting;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.RenderUtil;
import com.pvpclient.util.ScreenProjector;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Player boxes. "2D Box" is drawn on the screen and is visible through walls; "3D Box" is a world outline
 * that follows normal line of sight.
 */
public final class PlayerEspModule extends ClientModule {
    private final ModeSetting style = add(new ModeSetting("Style", 0, "2D Box (through walls)", "3D Box"));
    private final ColorSetting color = add(new ColorSetting("ESP Color", 0x8B5CF6));
    private final BooleanSetting teamColors = add(new BooleanSetting("Use Team Colors", true));
    private final BooleanSetting highlight = add(new BooleanSetting("Highlight", true));
    private final ColorSetting highlightColor = add(new ColorSetting("Highlight Color", 0x3B82F6));
    private final NumberSetting distance = add(new NumberSetting("Distance", 64, 8, 128, 1));

    private record Rect(float[] r, int outline, int fill) {}

    private final ScreenProjector projector = new ScreenProjector();
    private final List<Rect> rects = new ArrayList<>();

    public PlayerEspModule() {
        super("Player ESP", "Outline boxes around players (private worlds only)", Category.VISUALS);
        markRestricted();
    }

    private int baseRgb(AbstractClientPlayerEntity p) {
        AbstractTeam team = p.getScoreboardTeam();
        if (teamColors.get() && team != null && team.getColor().getColorValue() != null) {
            return team.getColor().getColorValue();
        }
        return color.get();
    }

    private Box lerpedBox(AbstractClientPlayerEntity p, float td) {
        double dx = MathHelper.lerp(td, p.prevX, p.getX()) - p.getX();
        double dy = MathHelper.lerp(td, p.prevY, p.getY()) - p.getY();
        double dz = MathHelper.lerp(td, p.prevZ, p.getZ()) - p.getZ();
        return p.getBoundingBox().offset(dx, dy, dz);
    }

    @Override
    public void onWorldRender(WorldRenderContext ctx) {
        rects.clear();
        if (mc.world == null || mc.player == null) return;
        float td = mc.getRenderTickCounter().getTickDelta(false);
        double max = distance.get();
        boolean flat = style.get() == 0;

        if (flat) projector.prepare(ctx, mc);

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        VertexConsumer lines = null;
        Vec3d cam = ctx.camera().getPos();
        boolean draw3d = !flat && ms != null && vcp != null;
        if (draw3d) {
            ms.push();
            ms.translate(-cam.x, -cam.y, -cam.z);
            lines = vcp.getBuffer(RenderLayer.getLines());
        }

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator() || !p.isAlive()) continue;
            if (p.distanceTo(mc.player) > max) continue;
            Box box = lerpedBox(p, td);
            int rgb = baseRgb(p);

            if (flat) {
                float[] r = projector.project(box);
                if (r == null) continue;
                int fill = highlight.get() ? RenderUtil.withAlpha(highlightColor.get(), 0.16) : 0;
                rects.add(new Rect(r, 0xFF000000 | rgb, fill));
            } else if (draw3d) {
                VertexRendering.drawBox(ms, lines, box, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1.0f);
                if (highlight.get()) {
                    VertexRendering.drawBox(ms, lines, box.expand(0.04), highlightColor.r(), highlightColor.g(), highlightColor.b(), 1.0f);
                }
            }
        }
        if (draw3d) {
            ms.pop();
            if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
        }
    }

    @Override
    public void onHudRender(DrawContext c, float tickDelta) {
        for (Rect r : rects) ScreenProjector.drawBox(c, r.r(), r.outline(), r.fill());
    }
}

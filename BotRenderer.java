package com.pvpclient.module.bot;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Подсветка действий бота прямо в мире:
 *  • красный контур у блоков, которые бот сломает/поставит (пропадает сразу, как блок изменился —
 *    список пересчитывается из реального состояния мира каждый кадр);
 *  • голубая линия маршрута;
 *  • жёлтые рамки — места, где бот прыгнет.
 */
final class BotRenderer {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    void render(WorldRenderContext ctx, PathFollower follower, BlockPos mineTarget, double radius) {
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null || mc.player == null || mc.world == null) return;

        List<BlockPos> red = new ArrayList<>();
        List<Vec3d> pts = new ArrayList<>();
        List<Vec3d> jumps = new ArrayList<>();
        pts.add(mc.player.getPos().add(0, 0.1, 0));

        int n = 0;
        for (PathFinder.Node nd : follower.remaining()) {
            if (n++ > 48) break;
            Vec3d c = Vec3d.ofBottomCenter(nd.pos);
            pts.add(c.add(0, 0.1, 0));
            if (nd.isJump()) jumps.add(c);
            for (BlockPos b : nd.breaks) if (!BotUtil.passable(mc.world, b)) red.add(b);
            if (nd.place != null && BotUtil.placeable(mc.world, nd.place)) red.add(nd.place);
        }
        if (mineTarget != null && !BotUtil.passable(mc.world, mineTarget)) red.add(mineTarget);
        if (red.isEmpty() && pts.size() < 2) return;

        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer lines = vcp.getBuffer(RenderLayer.getLines());

        Vec3d me = mc.player.getPos();
        for (BlockPos b : red) {
            if (Vec3d.ofCenter(b).distanceTo(me) > radius) continue;
            VertexRendering.drawBox(ms, lines, new Box(b).expand(0.003), 1.0f, 0.1f, 0.1f, 1.0f);
        }
        for (int i = 1; i < pts.size(); i++) {
            Vec3d a = pts.get(i - 1), b = pts.get(i);
            VertexRendering.drawVector(ms, lines, new Vector3f((float) a.x, (float) a.y, (float) a.z), b.subtract(a), 0xFF00E5FF);
        }
        for (Vec3d v : jumps) {
            VertexRendering.drawBox(ms, lines, new Box(v.x - 0.2, v.y + 0.05, v.z - 0.2, v.x + 0.2, v.y + 0.45, v.z + 0.2),
                    1.0f, 0.9f, 0.0f, 1.0f);
        }
        ms.pop();
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
    }
}

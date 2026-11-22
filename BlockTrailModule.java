package com.pvpclient.module.visual;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ColorSetting;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual-only ghost blocks under your feet while you walk, plus an optional preview of the blocks ahead.
 * Nothing is placed in the world.
 */
public final class BlockTrailModule extends ClientModule {
    private static final String[] NAMES = {"Glass", "Stone", "Obsidian", "Ice", "Slime Block", "Gold Block", "Diamond Block", "Iron Block"};
    private static final Block[] BLOCKS = {Blocks.GLASS, Blocks.STONE, Blocks.OBSIDIAN, Blocks.ICE,
            Blocks.SLIME_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.IRON_BLOCK};
    private static final int[] COLORS = {0xA8E6F0, 0x8A8A8A, 0x5B2E9E, 0x9DC8FF, 0x7FD64F, 0xFACC15, 0x4AEDD9, 0xD8D8D8};

    private final ModeSetting blockType = add(new ModeSetting("Block", 0, NAMES));
    private final ModeSetting appearance = add(new ModeSetting("Appearance", 1, "Outline", "Filled", "Block Model"));
    private final BooleanSetting useBlockColor = add(new BooleanSetting("Use Block Color", true));
    private final ColorSetting color = add(new ColorSetting("Color", 0x8B5CF6));
    private final NumberSetting opacity = add(new NumberSetting("Opacity", 0.55, 0.1, 1.0, 0.05));
    private final NumberSetting length = add(new NumberSetting("Trail Length", 12, 2, 48, 1));
    private final NumberSetting lifetime = add(new NumberSetting("Lifetime (s)", 4.0, 1.0, 15.0, 0.5));
    private final BooleanSetting preview = add(new BooleanSetting("Preview Ghost Blocks", true));
    private final NumberSetting previewCount = add(new NumberSetting("Preview Blocks", 3, 1, 6, 1));

    private record Trail(BlockPos pos, long born) {}
    private record Ghost(BlockPos pos, double alpha, float scale) {}

    private final ArrayDeque<Trail> trail = new ArrayDeque<>();
    private BlockPos last;

    public BlockTrailModule() {
        super("Block Trail", "Ghost blocks under your feet while you walk (visual only)", Category.VISUALS);
    }

    @Override protected void onDisable() { clear(); }
    @Override public void onIdle() { if (!trail.isEmpty() || last != null) clear(); }

    private void clear() {
        trail.clear();
        last = null;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) { clear(); return; }

        long now = System.currentTimeMillis();
        long life = (long) (lifetime.get() * 1000.0);
        while (!trail.isEmpty() && now - trail.peekFirst().born() > life) trail.pollFirst();

        BlockPos under = BlockPos.ofFloored(p.getX(), p.getY(), p.getZ()).down();
        if (!under.equals(last)) {
            trail.addLast(new Trail(under, now));
            last = under;
        }
        while (trail.size() > length.getInt()) trail.pollFirst(); // hard cap: nothing grows without limit
    }

    @Override
    public void onWorldRender(WorldRenderContext ctx) {
        ClientPlayerEntity p = mc.player;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (p == null || ms == null || vcp == null) return;

        long now = System.currentTimeMillis();
        double life = Math.max(1.0, lifetime.get() * 1000.0);
        double op = opacity.get();

        List<Ghost> ghosts = new ArrayList<>();
        for (Trail t : trail) {
            double left = 1.0 - (now - t.born()) / life;
            if (left <= 0) continue;
            ghosts.add(new Ghost(t.pos(), op * Math.max(0.12, left), (float) (0.35 + 0.65 * left)));
        }
        if (preview.get()) {
            double yawR = Math.toRadians(p.getYaw());
            double dx = -Math.sin(yawR), dz = Math.cos(yawR);
            double pulse = 0.6 + 0.4 * Math.sin(now / 250.0);
            for (int i = 1; i <= previewCount.getInt(); i++) {
                BlockPos ahead = BlockPos.ofFloored(p.getX() + dx * i, p.getY(), p.getZ() + dz * i).down();
                if (ahead.equals(last)) continue;
                ghosts.add(new Ghost(ahead, op * 0.5 * pulse, 0.8f));
            }
        }
        if (ghosts.isEmpty()) return;

        int rgb = useBlockColor.get() ? COLORS[blockType.get()] : color.get();
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        int mode = appearance.get();
        Vec3d cam = ctx.camera().getPos();
        VertexConsumerProvider.Immediate imm = vcp instanceof VertexConsumerProvider.Immediate i ? i : null;

        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);

        if (mode == 1) { // filled faces
            VertexConsumer fill = vcp.getBuffer(RenderLayer.getDebugFilledBox());
            for (Ghost gh : ghosts) {
                Box box = new Box(gh.pos()).expand(0.003);
                VertexRendering.drawFilledBox(ms, fill, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                        r, g, b, (float) (gh.alpha() * 0.55));
            }
            if (imm != null) imm.draw(RenderLayer.getDebugFilledBox());
        }
        if (mode <= 1) { // outlines
            VertexConsumer lines = vcp.getBuffer(RenderLayer.getLines());
            for (Ghost gh : ghosts) {
                VertexRendering.drawBox(ms, lines, new Box(gh.pos()).expand(0.003), r, g, b, (float) Math.min(1.0, gh.alpha() + 0.25));
            }
            if (imm != null) imm.draw(RenderLayer.getLines());
        }
        if (mode == 2) { // the real block model, shrinking as it ages
            BlockState state = BLOCKS[blockType.get()].getDefaultState();
            for (Ghost gh : ghosts) {
                BlockPos pos = gh.pos();
                ms.push();
                ms.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                ms.scale(gh.scale(), gh.scale(), gh.scale());
                ms.translate(-0.5, -0.5, -0.5);
                mc.getBlockRenderManager().renderBlockAsEntity(state, ms, vcp,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                ms.pop();
            }
            if (imm != null) imm.draw();
        }
        ms.pop();
    }
}

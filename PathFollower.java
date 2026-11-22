package com.pvpclient.module.bot;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Ведёт игрока по найденному пути «как человек»: зажимает W/Space/Ctrl, плавно крутит камеру,
 * ломает блоки (обычная добыча через interactionManager) и ставит блоки для моста.
 */
public final class PathFollower {
    public enum Status { RUNNING, DONE, FAILED }

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final BotInventory inv;

    private List<PathFinder.Node> path = List.of();
    private int idx, stuck, nodeTicks, mineTicks, pillarPhase;
    private Vec3d lastPos = Vec3d.ZERO;
    private BlockPos mining;

    public PathFollower(BotInventory inv) { this.inv = inv; }

    public boolean active() { return idx < path.size(); }

    public List<PathFinder.Node> remaining() { return active() ? path.subList(idx, path.size()) : List.of(); }

    public BlockPos miningPos() { return mining; }

    public void set(List<PathFinder.Node> p) {
        cancel();
        path = p;
        idx = 0;
        stuck = 0;
        nodeTicks = 0;
        pillarPhase = 0;
        lastPos = mc.player.getPos();
    }

    public void stopKeys() {
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    public void cancel() {
        path = List.of();
        idx = 0;
        pillarPhase = 0;
        mining = null;
        stopKeys();
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
    }

    // ------------------------------------------------------------------
    public Status tick() {
        if (!active()) return Status.DONE;
        ClientPlayerEntity p = mc.player;

        for (int guard = 0; guard < 3 && active(); guard++) {
            PathFinder.Node n = path.get(idx);
            Vec3d me = p.getPos();
            Vec3d c = Vec3d.ofBottomCenter(n.pos);
            double dx = c.x - me.x, dz = c.z - me.z, dy = c.y - me.y;
            double hd = Math.sqrt(dx * dx + dz * dz);

            if (hd < 0.4 && Math.abs(dy) < 0.8 && (n.breaks.isEmpty() || allClear(n)) ) {
                idx++; stuck = 0; nodeTicks = 0;
                continue;
            }
            return step(p, n, dx, dz, hd);
        }
        if (!active()) { stopKeys(); return Status.DONE; }
        return Status.RUNNING;
    }

    private boolean allClear(PathFinder.Node n) {
        for (BlockPos b : n.breaks) if (!BotUtil.passable(mc.world, b)) return false;
        return true;
    }

    private Status step(ClientPlayerEntity p, PathFinder.Node n, double dx, double dz, double hd) {
        if (++nodeTicks > 160) return fail();

        // Пилларинг — отдельная последовательность (прыжок → установка блока под ноги в полёте),
        // к обычному «ломаем/ставим/идём» не подходит: тут блок ставится ПОД игрока, а не перед ним.
        if (n.kind == PathFinder.Kind.PILLAR) return pillarStep(p, n);

        // 1) сначала ломаем всё, что мешает
        for (BlockPos b : n.breaks) {
            if (!BotUtil.passable(mc.world, b)) {
                stopKeys();
                if (!mineBlock(b)) return fail();
                return Status.RUNNING;
            }
        }
        mining = null;

        // 2) ставим блок (мост)
        if (n.place != null && BotUtil.placeable(mc.world, n.place)) {
            stopKeys();
            if (!placeBlock(n.place)) return fail();
            return Status.RUNNING;
        }

        // 3) идём
        Look.yaw(mc, dx, dz, 32f);
        boolean aligned = Look.yawError(mc, dx, dz) < 50f;
        mc.options.forwardKey.setPressed(aligned);
        mc.options.sprintKey.setPressed(aligned && n.kind == PathFinder.Kind.WALK && n.breaks.isEmpty());
        boolean jump = (n.isJump() && hd < 1.4 && p.isOnGround()) || (p.horizontalCollision && p.isOnGround());
        mc.options.jumpKey.setPressed(jump);

        // 4) защита от застревания
        Vec3d me = p.getPos();
        if (me.squaredDistanceTo(lastPos) < 0.0004) { if (++stuck > 40) return fail(); }
        else { stuck = 0; lastPos = me; }
        return Status.RUNNING;
    }

    private Status fail() { cancel(); return Status.FAILED; }

    /** Прыжок на месте с установкой блока под ноги в полёте — подъём без готовой ступени. */
    private Status pillarStep(ClientPlayerEntity p, PathFinder.Node n) {
        mining = null;
        mc.options.forwardKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        switch (pillarPhase) {
            case 0 -> { // отталкиваемся
                mc.options.jumpKey.setPressed(true);
                if (!p.isOnGround()) { pillarPhase = 1; mc.options.jumpKey.setPressed(false); }
            }
            case 1 -> { // в полёте — ставим блок под ноги, пока не приземлились обратно
                if (n.place != null && BotUtil.placeable(mc.world, n.place)) {
                    if (!placeBlock(n.place)) return fail();
                } else {
                    pillarPhase = 2;
                }
            }
            default -> { // стоим на новом блоке — узел закрыт
                if (p.isOnGround()) { idx++; pillarPhase = 0; nodeTicks = 0; stuck = 0; }
            }
        }
        return Status.RUNNING;
    }

    // ------------------------------------------------------------------
    /** Ломает блок обычной добычей. false — не достать или слишком долго. */
    public boolean mineBlock(BlockPos b) {
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        Vec3d c = Vec3d.ofCenter(b);
        if (eye.distanceTo(c) > 5.0) return false;
        BlockState st = mc.world.getBlockState(b);
        if (!b.equals(mining)) { mining = b; mineTicks = 0; inv.equipBestTool(st); }
        if (++mineTicks > 300) { mining = null; return false; }
        Look.at(mc, c, 45f);
        Direction face = Direction.getFacing(eye.x - c.x, eye.y - c.y, eye.z - c.z);
        mc.interactionManager.updateBlockBreakingProgress(b, face);
        p.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /** Ставит блок в позицию target, кликая по соседнему твёрдому блоку. */
    private boolean placeBlock(BlockPos target) {
        int slot = inv.scaffoldHotbar();
        if (slot < 0) return false;
        inv.select(slot);
        for (Direction d : Direction.values()) {
            BlockPos nb = target.offset(d);
            if (!mc.world.getBlockState(nb).isSolidBlock(mc.world, nb)) continue;
            Direction face = d.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(nb).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (mc.player.getEyePos().distanceTo(hitVec) > 4.5) continue;
            Look.at(mc, hitVec, 60f);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, face, nb, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }
}

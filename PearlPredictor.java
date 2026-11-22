package com.pvpclient.util;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/** Simulates a thrown ender pearl: gravity 0.03 and drag 0.99 per tick, stopping at the first block hit. */
public final class PearlPredictor {
    public static final double SPEED = 1.5;
    private static final double GRAVITY = 0.03;
    private static final double AIR_DRAG = 0.99;
    private static final double WATER_DRAG = 0.8;

    public record Result(Vec3d landing, int ticks, boolean hitBlock) {}

    private PearlPredictor() {}

    /** Launch velocity for a throw with the given look angles (the thrower's own motion is added, like in the game). */
    public static Vec3d launchVelocity(float yaw, float pitch, Vec3d throwerVelocity, boolean onGround) {
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        Vec3d dir = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp)).normalize();
        return dir.multiply(SPEED).add(throwerVelocity.x, onGround ? 0.0 : throwerVelocity.y, throwerVelocity.z);
    }

    /** Where a pearl starting at {@code eye} (thrower's eye position) would begin its flight. */
    public static Vec3d startPosition(Entity thrower) {
        return new Vec3d(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
    }

    /**
     * @param path optional list that receives the flight path points (capped at 200)
     */
    public static Result simulate(ClientWorld world, Entity context, Vec3d pos, Vec3d vel, int maxTicks, List<Vec3d> path) {
        Vec3d p = pos, v = vel;
        for (int t = 0; t < maxTicks; t++) {
            Vec3d next = p.add(v);
            BlockHitResult hit = world.raycast(new RaycastContext(p, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, context));
            if (hit.getType() != HitResult.Type.MISS) {
                if (path != null && path.size() < 200) path.add(hit.getPos());
                return new Result(hit.getPos(), t + 1, true);
            }
            p = next;
            if (path != null && path.size() < 200) path.add(p);
            boolean inWater = !world.getFluidState(BlockPos.ofFloored(p.x, p.y, p.z)).isEmpty();
            v = v.multiply(inWater ? WATER_DRAG : AIR_DRAG).subtract(0.0, GRAVITY, 0.0);
            if (p.y < world.getBottomY() - 16) break;
            double hx = p.x - pos.x, hz = p.z - pos.z;
            if (hx * hx + hz * hz > 100.0 * 100.0) break;
        }
        return new Result(p, maxTicks, false);
    }
}

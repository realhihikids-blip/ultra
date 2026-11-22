package com.pvpclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class CombatUtil {
    public static final String[] TARGET_MODES = {"Players", "Mobs", "Players + Mobs"};

    /** Optional extra rule set by Target Lock (null = no extra rule). */
    public static java.util.function.Predicate<Entity> extraFilter;

    private CombatUtil() {}

    public static boolean matches(Entity e, int mode) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator()) return false;
        if (le instanceof TameableEntity t && t.isTamed()) return false;
        if (extraFilter != null && !extraFilter.test(e)) return false;
        boolean player = le instanceof PlayerEntity;
        boolean mob = le instanceof Monster;
        return switch (mode) {
            case 0 -> player;
            case 1 -> mob;
            default -> player || mob;
        };
    }

    /** Nearest valid target inside range and inside your own field of view. No rotation changes are made. */
    public static LivingEntity findTarget(MinecraftClient mc, double range, double fov, int mode, boolean needSight) {
        var p = mc.player;
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getOtherEntities(p, p.getBoundingBox().expand(range + 1.0))) {
            if (!matches(e, mode)) continue;
            LivingEntity le = (LivingEntity) e;
            Box b = le.getBoundingBox();
            Vec3d closest = new Vec3d(
                    MathHelper.clamp(eye.x, b.minX, b.maxX),
                    MathHelper.clamp(eye.y, b.minY, b.maxY),
                    MathHelper.clamp(eye.z, b.minZ, b.maxZ));
            double dist = eye.distanceTo(closest);
            if (dist > range || dist >= bestDist) continue;

            Vec3d to = closest.subtract(eye);
            if (to.lengthSquared() > 1.0E-6) {
                double dot = MathHelper.clamp(look.dotProduct(to.normalize()), -1.0, 1.0);
                if (Math.toDegrees(Math.acos(dot)) > fov / 2.0) continue;
            }
            if (needSight && !p.canSee(le)) continue;

            best = le;
            bestDist = dist;
        }
        return best;
    }

    /** Distance from a point to the closest point of an entity's hitbox. */
    public static double distanceToBox(Vec3d from, LivingEntity e) {
        Box b = e.getBoundingBox();
        Vec3d closest = new Vec3d(
                MathHelper.clamp(from.x, b.minX, b.maxX),
                MathHelper.clamp(from.y, b.minY, b.maxY),
                MathHelper.clamp(from.z, b.minZ, b.maxZ));
        return from.distanceTo(closest);
    }

    public static void attack(MinecraftClient mc, Entity target) {
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}

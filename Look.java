package com.pvpclient.module.bot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Плавный поворот реальной камеры игрока (никаких подмен ротации в пакетах). */
final class Look {
    private Look() {}

    /** Повернуться к точке; maxStep — максимум градусов за тик. */
    static void at(MinecraftClient mc, Vec3d target, float maxStep) {
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        step(p, yaw, pitch, maxStep);
    }

    /** Повернуться по горизонтали в сторону движения, взгляд постепенно выравнивается к горизонту. */
    static void yaw(MinecraftClient mc, double dx, double dz, float maxStep) {
        ClientPlayerEntity p = mc.player;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        step(p, yaw, p.getPitch() * 0.8f, maxStep);
    }

    static float yawError(MinecraftClient mc, double dx, double dz) {
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
    }

    private static void step(ClientPlayerEntity p, float yaw, float pitch, float maxStep) {
        float dy = MathHelper.clamp(MathHelper.wrapDegrees(yaw - p.getYaw()), -maxStep, maxStep);
        float dp = MathHelper.clamp(pitch - p.getPitch(), -maxStep, maxStep);
        p.setYaw(p.getYaw() + dy);
        p.setPitch(MathHelper.clamp(p.getPitch() + dp, -90f, 90f));
    }
}

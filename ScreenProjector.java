package com.pvpclient.util;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Projects world boxes to screen rectangles (call prepare() in the world pass) so ESP boxes can be
 * drawn in 2D in the HUD pass. 2D drawing ignores the depth buffer, so boxes are visible through walls.
 */
public final class ScreenProjector {
    private final Matrix4f view = new Matrix4f();
    private Matrix4f proj = new Matrix4f();
    private final Vector4f v = new Vector4f();
    private Vec3d cam = Vec3d.ZERO;
    private int sw, sh;

    public void prepare(WorldRenderContext ctx, MinecraftClient mc) {
        Camera camera = ctx.camera();
        cam = camera.getPos();
        view.identity().rotation(camera.getRotation().conjugate(new Quaternionf()));
        proj = ctx.projectionMatrix();
        sw = mc.getWindow().getScaledWidth();
        sh = mc.getWindow().getScaledHeight();
    }

    /** @return {minX, minY, maxX, maxY} in scaled GUI pixels, or null if the box is behind the camera or off screen. */
    public float[] project(Box b) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            v.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1f);
            view.transform(v);
            proj.transform(v);
            if (v.w <= 0.05f) return null;
            float sx = (v.x / v.w * 0.5f + 0.5f) * sw;
            float sy = (1f - (v.y / v.w * 0.5f + 0.5f)) * sh;
            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
        }
        if (maxX < -50 || minX > sw + 50 || maxY < -50 || minY > sh + 50) return null;
        if (maxX - minX > sw * 3f || maxY - minY > sh * 3f) return null;
        return new float[]{minX, minY, maxX, maxY};
    }

    /** @return {x, y} in scaled GUI pixels, or null if the point is behind the camera or off screen. */
    public float[] projectPoint(Vec3d p) {
        v.set((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z), 1f);
        view.transform(v);
        proj.transform(v);
        if (v.w <= 0.05f) return null;
        float sx = (v.x / v.w * 0.5f + 0.5f) * sw;
        float sy = (1f - (v.y / v.w * 0.5f + 0.5f)) * sh;
        if (sx < -50 || sx > sw + 50 || sy < -50 || sy > sh + 50) return null;
        return new float[]{sx, sy};
    }

    /** 1px outline with an optional translucent fill. */
    public static void drawBox(DrawContext c, float[] r, int outlineArgb, int fillArgb) {
        int x1 = (int) Math.floor(r[0]), y1 = (int) Math.floor(r[1]);
        int x2 = Math.max((int) Math.ceil(r[2]), x1 + 2), y2 = Math.max((int) Math.ceil(r[3]), y1 + 2);
        if ((fillArgb >>> 24) > 4) c.fill(x1, y1, x2, y2, fillArgb);
        c.fill(x1, y1, x2, y1 + 1, outlineArgb);
        c.fill(x1, y2 - 1, x2, y2, outlineArgb);
        c.fill(x1, y1, x1 + 1, y2, outlineArgb);
        c.fill(x2 - 1, y1, x2, y2, outlineArgb);
    }
}

package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ColorSetting;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.AimSmoother;
import com.pvpclient.util.Anim;
import com.pvpclient.util.ItemUser;
import com.pvpclient.util.Lang;
import com.pvpclient.util.PearlPredictor;
import com.pvpclient.util.RenderUtil;
import com.pvpclient.util.ScreenProjector;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ender pearl training for private worlds: tracks pearls thrown by training targets and predicts their
 * landing, shows where your own throw would land, and can test aiming at stationary dummies (armor stands).
 */
public final class EnderPearlTrainerModule extends ClientModule {
    private final ModeSetting trackWho = add(new ModeSetting("Track Throwers", 0, "Other Players", "Nearest Player", "Including Me"));
    private final BooleanSetting predictMine = add(new BooleanSetting("Show My Aim Prediction", true));
    private final NumberSetting dummyRange = add(new NumberSetting("Dummy Range", 32, 8, 64, 1));
    private final BooleanSetting autoAim = add(new BooleanSetting("Auto Aim At Dummy", false));
    private final NumberSetting aimSpeed = add(new NumberSetting("Aim Speed (deg/s)", 360.0, 90.0, 1080.0, 30.0));
    private final BooleanSetting smoothTurning = add(new BooleanSetting("Smooth Camera", true));
    private final NumberSetting smoothRate = add(new NumberSetting("Smoothing Speed", 10.0, 2.0, 30.0, 1.0));
    private final BooleanSetting autoThrow = add(new BooleanSetting("Auto Throw", false));
    private final NumberSetting throwDelay = add(new NumberSetting("Throw Delay (ticks)", 40.0, 20.0, 100.0, 5.0));
    private final ColorSetting landingColor = add(new ColorSetting("Landing Color", 0x22D3EE));
    private final ColorSetting pathColor = add(new ColorSetting("Path Color", 0x8B5CF6));

    private static final int MAX_TRACKED = 8;

    private static final class Tracked {
        final String name;
        final List<Vec3d> path = new ArrayList<>();
        Vec3d landing;
        int ticks;
        Tracked(String name) { this.name = name; }
    }

    private record Dot(float x, float y, int argb) {}
    private record Marker(float[] rect, int rgb, String label) {}

    private final Map<Integer, Tracked> tracked = new HashMap<>();
    private final List<Vec3d> myPath = new ArrayList<>();
    private Vec3d myLanding;
    private int myTicks;

    private ArmorStandEntity dummy;
    private boolean solved;
    private float solvedYaw, solvedPitch;
    private double solvedError;
    private int solveTimer, cooldown;

    private final AimSmoother smoother = new AimSmoother();
    private String bannerName;
    private long bannerUntil;

    private final ScreenProjector projector = new ScreenProjector();
    private final List<Dot> dots = new ArrayList<>();
    private final List<Marker> markers = new ArrayList<>();

    public EnderPearlTrainerModule() {
        super("Ender Pearl Trainer", "Pearl landing prediction and dummy aiming test (private worlds only)", Category.COMBAT);
        markRestricted();
    }

    @Override protected void onDisable() { clearAll(); smoother.reset(); }
    @Override public void onIdle() { if (!tracked.isEmpty() || dummy != null || !dots.isEmpty()) clearAll(); }

    private void clearAll() {
        tracked.clear();
        myPath.clear();
        dots.clear();
        markers.clear();
        myLanding = null;
        dummy = null;
        solved = false;
        bannerName = null;
    }

    // ---------------------------------------------------------------- tick: tracking and solving

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) { clearAll(); return; }
        if (cooldown > 0) cooldown--;

        trackPearls(p);
        predictOwnThrow(p);
        updateDummy(p);
        maybeThrow(p);
    }

    private void trackPearls(ClientPlayerEntity p) {
        PlayerEntity nearest = trackWho.get() == 1 ? nearestOtherPlayer(p) : null;
        Set<Integer> seen = new HashSet<>();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl) || !pearl.isAlive()) continue;
            if (!(pearl.getOwner() instanceof PlayerEntity owner)) continue;
            boolean wanted = switch (trackWho.get()) {
                case 0 -> owner != p;
                case 1 -> owner == nearest;
                default -> true;
            };
            if (!wanted) continue;

            int id = pearl.getId();
            Tracked t = tracked.get(id);
            if (t == null) {
                if (tracked.size() >= MAX_TRACKED) continue;
                t = new Tracked(owner.getName().getString());
                tracked.put(id, t);
                bannerName = t.name;                       // "thrown by" notice
                bannerUntil = System.currentTimeMillis() + 4000;
            }
            t.path.clear();
            PearlPredictor.Result r = PearlPredictor.simulate(mc.world, p, pearl.getPos(), pearl.getVelocity(), 160, t.path);
            t.landing = r.landing();
            t.ticks = r.ticks();
            seen.add(id);
        }
        tracked.keySet().retainAll(seen); // pearls that landed or vanished are dropped right away
    }

    private PlayerEntity nearestOtherPlayer(ClientPlayerEntity p) {
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || o.isSpectator()) continue;
            double d = o.squaredDistanceTo(p);
            if (d < bestDist) { bestDist = d; best = o; }
        }
        return best;
    }

    private void predictOwnThrow(ClientPlayerEntity p) {
        myPath.clear();
        if (!predictMine.get()) { myLanding = null; return; }
        Vec3d vel = PearlPredictor.launchVelocity(p.getYaw(), p.getPitch(), p.getVelocity(), p.isOnGround());
        PearlPredictor.Result r = PearlPredictor.simulate(mc.world, p, PearlPredictor.startPosition(p), vel, 160, myPath);
        myLanding = r.landing();
        myTicks = r.ticks();
    }

    private void updateDummy(ClientPlayerEntity p) {
        dummy = null;
        double best = dummyRange.get() * dummyRange.get();
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof ArmorStandEntity stand && stand.isAlive()) {
                double d = stand.squaredDistanceTo(p);
                if (d <= best) { best = d; dummy = stand; }
            }
        }
        if (dummy == null) { solved = false; return; }
        if (solveTimer-- > 0) return;
        solveTimer = 5;
        solve(p, dummy.getPos());
    }

    /** Finds the pitch whose simulated landing is closest to the target (coarse search, then a fine one). */
    private void solve(ClientPlayerEntity p, Vec3d target) {
        Vec3d start = PearlPredictor.startPosition(p);
        Vec3d to = target.subtract(start);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));

        float bestPitch = 0f;
        double bestErr = Double.MAX_VALUE;
        for (float pitch = -80f; pitch <= 40f; pitch += 4f) {
            double err = error(p, start, yaw, pitch, target);
            if (err < bestErr) { bestErr = err; bestPitch = pitch; }
        }
        float center = bestPitch;
        for (float pitch = center - 4f; pitch <= center + 4f; pitch += 0.5f) {
            double err = error(p, start, yaw, pitch, target);
            if (err < bestErr) { bestErr = err; bestPitch = pitch; }
        }
        solvedYaw = yaw;
        solvedPitch = bestPitch;
        solvedError = bestErr;
        solved = bestErr < 2.0;
    }

    private double error(ClientPlayerEntity p, Vec3d start, float yaw, float pitch, Vec3d target) {
        Vec3d vel = PearlPredictor.launchVelocity(yaw, pitch, p.getVelocity(), p.isOnGround());
        return PearlPredictor.simulate(mc.world, p, start, vel, 120, null).landing().distanceTo(target);
    }

    private void maybeThrow(ClientPlayerEntity p) {
        if (!autoAim.get() || !autoThrow.get() || !solved || cooldown > 0) return;
        if (mc.currentScreen != null || ItemUser.busy() || p.isUsingItem()) return;
        if (Math.abs(MathHelper.wrapDegrees(solvedYaw - p.getYaw())) > 1.5f) return;
        if (Math.abs(solvedPitch - p.getPitch()) > 1.5f) return;

        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.ENDER_PEARL) && !p.getItemCooldownManager().isCoolingDown(s)) {
                if (ItemUser.beginOnce(mc, i)) cooldown = throwDelay.getInt();
                return;
            }
        }
    }

    // ---------------------------------------------------------------- frame: aim + project markers

    @Override
    public void onWorldRender(WorldRenderContext ctx) {
        dots.clear();
        markers.clear();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        projector.prepare(ctx, mc);

        int path = 0xFF000000 | pathColor.get();
        int land = landingColor.get();

        for (Tracked t : tracked.values()) {
            addPath(t.path, path);
            addMarker(p, t.landing, t.ticks, land, t.name);
        }
        if (predictMine.get() && myLanding != null) {
            addPath(myPath, path);
            addMarker(p, myLanding, myTicks, land, Lang.tr("My throw"));
        }
        if (solved && dummy != null) {
            Box b = dummy.getBoundingBox().expand(0.15);
            float[] r = projector.project(b);
            if (r != null) markers.add(new Marker(r, 0xFACC15, Lang.tr("Dummy")));
        }

        // visible camera turn toward the solved angles; still the real rotation, just eased
        if (autoAim.get() && solved && mc.currentScreen == null) {
            if (smoothTurning.get()) {
                if (Math.abs(MathHelper.wrapDegrees(solvedYaw - p.getYaw())) > 90.0) smoother.snap(p.getYaw(), p.getPitch());
                smoother.towards(solvedYaw, solvedPitch, smoothRate.get(), Anim.dt);
                p.setYaw(smoother.yaw());
                p.setPitch(smoother.pitch());
            } else {
                smoother.reset();
                float dYaw = MathHelper.wrapDegrees(solvedYaw - p.getYaw());
                float dPitch = solvedPitch - p.getPitch();
                float step = (float) (aimSpeed.get() * Anim.dt);
                p.setYaw(p.getYaw() + MathHelper.clamp(dYaw, -step, step));
                p.setPitch(MathHelper.clamp(p.getPitch() + MathHelper.clamp(dPitch, -step, step), -90.0f, 90.0f));
            }
        } else {
            smoother.reset();
        }
    }

    private void addPath(List<Vec3d> points, int argb) {
        for (int i = 0; i < points.size(); i += 2) {
            float[] pt = projector.projectPoint(points.get(i));
            if (pt != null) dots.add(new Dot(pt[0], pt[1], argb));
        }
    }

    private void addMarker(ClientPlayerEntity p, Vec3d landing, int ticks, int rgb, String who) {
        if (landing == null) return;
        float[] r = projector.project(new Box(landing, landing).expand(0.35));
        if (r == null) return;
        String label = String.format(Locale.ROOT, "%s  %.1fm  %.1fs", who, landing.distanceTo(p.getPos()), ticks / 20.0);
        markers.add(new Marker(r, rgb, label));
    }

    // ---------------------------------------------------------------- hud

    @Override
    public void onHudRender(DrawContext c, float tickDelta) {
        for (Dot d : dots) c.fill((int) d.x(), (int) d.y(), (int) d.x() + 2, (int) d.y() + 2, d.argb());
        for (Marker m : markers) {
            ScreenProjector.drawBox(c, m.rect(), 0xFF000000 | m.rgb(), RenderUtil.withAlpha(m.rgb(), 0.18));
            c.drawText(mc.textRenderer, m.label(), (int) m.rect()[0], (int) m.rect()[1] - 10, 0xFFFFFFFF, true);
        }

        int x = 6, y = mc.getWindow().getScaledHeight() / 2 - 20;
        if (bannerName != null && System.currentTimeMillis() < bannerUntil) {
            c.drawText(mc.textRenderer, Lang.tr("Pearl thrown by ") + bannerName, x, y, 0xFFFACC15, true);
            y += 11;
        }
        if (dummy != null) {
            String line = solved
                    ? String.format(Locale.ROOT, "%s%.1fm  yaw %.1f  pitch %.1f  (%.1f)", Lang.tr("Dummy: "),
                            (double) dummy.distanceTo(mc.player), solvedYaw, solvedPitch, solvedError)
                    : Lang.tr("Dummy out of reach");
            c.drawText(mc.textRenderer, line, x, y, solved ? 0xFF22D3EE : 0xFFEF4444, true);
        }
    }
}

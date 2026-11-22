package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.NumberSetting;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Enlarges other players' client-side hitboxes (affects your crosshair and KillAura reach checks).
 * Private worlds only. The Player ESP outline shows the enlarged box.
 */
public final class HitboxModule extends ClientModule {
    private final NumberSetting size = add(new NumberSetting("Hitbox Size", 0.3, 0.0, 4.0, 0.05));

    public HitboxModule() {
        super("Hitbox", "Enlarges other players' hitboxes (private worlds only)", Category.COMBAT);
        markRestricted();
    }

    private static Box baseBox(AbstractClientPlayerEntity p) {
        return p.getDimensions(p.getPose()).getBoxAt(p.getPos());
    }

    // Re-applied every tick: the game resets an entity's box whenever its position updates.
    @Override
    public void onTick() {
        if (mc.world == null) return;
        double grow = size.get();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            Box base = baseBox(p);
            p.setBoundingBox(grow > 0 ? base.expand(grow) : base);
        }
    }

    @Override
    protected void onDisable() { restore(); }

    private void restore() {
        if (mc.world == null) return;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player) p.setBoundingBox(baseBox(p));
        }
    }
}

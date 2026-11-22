package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.Lang;
import com.pvpclient.util.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

import java.util.UUID;

/**
 * While enabled, KillAura and TriggerBot only attack the one player you locked on: the player you
 * hit, or the one under your crosshair when you switched this module on.
 */
public final class TargetLockModule extends ClientModule {
    private final BooleanSetting lockOnHit = add(new BooleanSetting("Lock On Hit", true));
    private final BooleanSetting lockOnEnable = add(new BooleanSetting("Lock On Enable", true));
    private final BooleanSetting switchOnHit = add(new BooleanSetting("Switch On Hit", true));

    private UUID locked;

    public TargetLockModule() {
        super("Target Lock", "KillAura and TriggerBot only attack the player you locked on", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        CombatUtil.extraFilter = e -> locked != null && e.getUuid().equals(locked);
        locked = null;
        if (lockOnEnable.get() && mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerEntity pe && pe != mc.player) {
            locked = pe.getUuid();
        }
    }

    @Override
    protected void onDisable() {
        CombatUtil.extraFilter = null;
        locked = null;
    }

    /** Called from the attack event whenever you attack something on the client. */
    public void onPlayerAttack(Entity target) {
        if (!isEnabled() || !lockOnHit.get()) return;
        if (!(target instanceof PlayerEntity pe) || pe == mc.player) return;
        if (locked == null || switchOnHit.get()) locked = pe.getUuid();
    }

    private AbstractClientPlayerEntity lockedPlayer() {
        if (locked == null || mc.world == null) return null;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p.getUuid().equals(locked)) return p;
        }
        return null;
    }

    @Override
    public void onTick() {
        AbstractClientPlayerEntity p = lockedPlayer();
        if (p != null && !p.isAlive()) locked = null; // target died: release
    }

    @Override
    public void onHudRender(DrawContext c, float tickDelta) {
        AbstractClientPlayerEntity p = lockedPlayer();
        String name = p != null ? p.getName().getString() : (locked != null ? "?" : "-");
        String text = Lang.tr("Target: ") + name;
        int w = mc.textRenderer.getWidth(text) + 12;
        int x = (mc.getWindow().getScaledWidth() - w) / 2;
        RenderUtil.roundedRect(c, x, 4, w, 14, 4, 0xB00A0A14);
        c.drawText(mc.textRenderer, text, x + 6, 7, locked != null ? 0xFFFFFFFF : 0xFF9A9AB8, true);
    }
}

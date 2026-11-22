package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.ItemUser;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/** Practice trigger bot: attacks when your own crosshair is already on a valid target. */
public final class TriggerBotModule extends ClientModule {
    private final NumberSetting charge = add(new NumberSetting("Cooldown Charge", 0.95, 0.5, 1.0, 0.05));
    private final ModeSetting targets = add(new ModeSetting("Targets", 2, CombatUtil.TARGET_MODES));

    public TriggerBotModule() {
        super("TriggerBot", "Practice mode: attacks when the crosshair is on a target", Category.COMBAT);
        markRestricted();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || mc.player.isUsingItem() || ItemUser.busy()) return;
        if (mc.player.getAttackCooldownProgress(0.0f) < charge.get()) return;

        HitResult hit = mc.crosshairTarget;
        if (hit instanceof EntityHitResult ehr && CombatUtil.matches(ehr.getEntity(), targets.get())) {
            CombatUtil.attack(mc, ehr.getEntity());
        }
    }
}

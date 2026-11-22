package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.ItemUser;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;

/** Drinks a potion from your hotbar whenever one of the chosen effects has run out. */
public final class AutoBuffModule extends ClientModule {
    private final BooleanSetting speed = add(new BooleanSetting("Speed", true));
    private final BooleanSetting strength = add(new BooleanSetting("Strength", true));
    private final BooleanSetting fireRes = add(new BooleanSetting("Fire Resistance", false));
    private final BooleanSetting resistance = add(new BooleanSetting("Resistance", false));
    private final BooleanSetting regeneration = add(new BooleanSetting("Regeneration", false));
    private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 20.0, 0.0, 100.0, 5.0));
    private int cooldown;

    public AutoBuffModule() {
        super("Auto Buff", "Drinks potions to keep chosen effects active (private worlds only)", Category.COMBAT);
        markRestricted();
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (ItemUser.busy() || p.isUsingItem()) return;

        if (tryBuff(p, speed.get(), StatusEffects.SPEED)
                || tryBuff(p, strength.get(), StatusEffects.STRENGTH)
                || tryBuff(p, fireRes.get(), StatusEffects.FIRE_RESISTANCE)
                || tryBuff(p, resistance.get(), StatusEffects.RESISTANCE)
                || tryBuff(p, regeneration.get(), StatusEffects.REGENERATION)) {
            cooldown = delay.getInt();
        }
    }

    private boolean tryBuff(ClientPlayerEntity p, boolean wanted, RegistryEntry<StatusEffect> effect) {
        if (!wanted || p.hasStatusEffect(effect)) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.POTION) && ItemUser.hasEffect(s, effect)) return ItemUser.begin(mc, i);
        }
        return false;
    }
}

package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.ItemUser;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Eats a golden apple or drinks a healing potion from your hotbar when your health is low. */
public final class AutoHealModule extends ClientModule {
    private final NumberSetting below = add(new NumberSetting("Heal Below (HP)", 10.0, 2.0, 19.0, 1.0));
    private final ModeSetting with = add(new ModeSetting("Heal With", 2, "Golden Apple", "Healing Potion", "Any"));
    private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 20.0, 0.0, 100.0, 5.0));
    private int cooldown;

    public AutoHealModule() {
        super("Auto Heal", "Uses a golden apple or healing potion when health is low (private worlds only)", Category.COMBAT);
        markRestricted();
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (ItemUser.busy() || p.isUsingItem()) return;
        if (p.getHealth() > below.get()) return;

        int slot = findSlot(p);
        if (slot >= 0 && ItemUser.begin(mc, slot)) cooldown = delay.getInt();
    }

    private int findSlot(ClientPlayerEntity p) {
        int mode = with.get(); // 0 apple, 1 potion, 2 any (potion first)
        if (mode != 0) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.isOf(Items.POTION) && ItemUser.hasEffect(s, StatusEffects.INSTANT_HEALTH)) return i;
            }
        }
        if (mode != 1) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return i;
            }
        }
        return -1;
    }
}

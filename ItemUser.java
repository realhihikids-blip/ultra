package com.pvpclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Uses one hotbar item the way a player does: select the slot, hold "use" until the item is finished,
 * then switch back. Only one item is used at a time (Auto Heal and Auto Buff share this).
 */
public final class ItemUser {
    private static boolean active, started, once;
    private static int previousSlot, ticks;

    private ItemUser() {}

    public static boolean busy() { return active; }

    public static boolean begin(MinecraftClient mc, int slot) {
        ClientPlayerEntity p = mc.player;
        if (active || p == null || slot < 0 || slot > 8) return false;
        previousSlot = p.getInventory().selectedSlot;
        p.getInventory().setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
        active = true;
        started = false;
        once = false;
        ticks = 0;
        return true;
    }

    /** Like begin(), but only taps "use" briefly (for instant items such as ender pearls). */
    public static boolean beginOnce(MinecraftClient mc, int slot) {
        if (!begin(mc, slot)) return false;
        once = true;
        return true;
    }

    /** Call once per tick. */
    public static void tick(MinecraftClient mc) {
        if (!active) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null) { finish(mc); return; }
        ticks++;
        if (p.isUsingItem()) started = true;
        if (once ? ticks >= 2 : ((started && !p.isUsingItem()) || ticks > 80)) finish(mc);
    }

    public static void finish(MinecraftClient mc) {
        if (!active) return;
        mc.options.useKey.setPressed(false);
        if (mc.player != null && previousSlot >= 0 && previousSlot <= 8) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        active = false;
        once = false;
    }

    /** True if this potion item carries the given effect. */
    public static boolean hasEffect(ItemStack stack, RegistryEntry<StatusEffect> effect) {
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;
        for (StatusEffectInstance inst : contents.getEffects()) {
            if (inst.getEffectType().equals(effect)) return true;
        }
        return false;
    }
}

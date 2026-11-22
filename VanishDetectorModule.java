package com.pvpclient.module.visual;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class VanishDetectorModule extends ClientModule {

    private long lastNotifyTime = 0;

    public VanishDetectorModule() {
        // Меняем категорию на COMBAT, так как VISUAL в твоем чите называется иначе
        super("VanishDetector+", "Палит админов в Ванише + Авто-Тотем при опасности", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (!this.isActive() || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // Прямое получение тотема по его ID из реестра игры для обхода ошибок маппинга
        Item totemItem = Registries.ITEM.get(Identifier.of("minecraft", "totem_of_immortality"));

        // --- 1. ЛОГИКА АВТО-ТОТЕМА ---
        if (mc.player.getOffHandStack().getItem() != totemItem && mc.player.getHealth() <= 14) {
            PlayerInventory inv = mc.player.getInventory();
            for (int i = 0; i < 45; i++) {
                if (inv.getStack(i).getItem() == totemItem) {
                    int slot = i < 9 ? i + 36 : i;
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
                    break;
                }
            }
        }

        // --- 2. ЛОГИКА ДЕТЕКТОРА ВАНИША ---
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotifyTime > 7000) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player != mc.player && player.isInvisible() && mc.player.distanceTo(player) <= 25) {
                    mc.player.sendMessage(Text.of("§c[Радар] ВНИМАНИЕ! Рядом обнаружен скрытый АДМИН в ванише!"), false);
                    lastNotifyTime = currentTime;
                    break;
                }
            }
        }
    }
}
package com.pvpclient.module.movement;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.module.bot.BotGuard;
import com.pvpclient.setting.NumberSetting;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.text.Text;

/**
 * Полёт как в творческом режиме: пробел — вверх, Shift — вниз. Столкновения с блоками
 * по-прежнему работают (это не ноклип, сквозь стены не пролетишь — такой возможности
 * специально нет, у нормального пролёта сквозь блоки не бывает честного применения даже
 * в своём мире, а на чужом сервере это сразу заметно и расценивается как читерство).
 *
 * Только singleplayer / LAN / authorizedServers: настоящий survival-сервер периодически
 * сверяет твои способности сам и рано или поздно сбросит полёт, если сервер не ждёт его
 * от игрока в survival.
 */
public final class FlyModule extends ClientModule {
    private final NumberSetting speed = add(new NumberSetting("Fly Speed", 0.6, 0.1, 2.0, 0.05));

    private int warnTicks;
    private boolean appliedByUs;

    public FlyModule() {
        super("Fly", "Полёт как в креативе (singleplayer / свои серверы)", Category.MOVEMENT);
        markRestricted();
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        PlayerAbilities a = mc.player.getAbilities();
        a.allowFlying = true;
        a.flying = true;
        a.setFlySpeed((float) (speed.get() / 10.0));
        mc.player.sendAbilitiesUpdate();
        appliedByUs = true;
    }

    @Override
    protected void onDisable() {
        if (mc.player == null) return;
        PlayerAbilities a = mc.player.getAbilities();
        a.flying = false;
        if (!mc.player.getAbilities().creativeMode) a.allowFlying = false;
        a.setFlySpeed(0.05f);
        mc.player.sendAbilitiesUpdate();
        appliedByUs = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!BotGuard.allowed(mc)) {
            if (warnTicks++ % 60 == 0) mc.player.sendMessage(Text.literal("Fly: только singleplayer / LAN / authorizedServers"), true);
            setEnabled(false);
            return;
        }
        PlayerAbilities a = mc.player.getAbilities();
        if (appliedByUs && !a.flying) a.flying = true; // сервер/смерть сбросили — включаем обратно
        a.setFlySpeed((float) (speed.get() / 10.0));
    }
}

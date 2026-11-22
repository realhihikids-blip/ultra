package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import net.minecraft.entity.Entity;

public class TargetStrafeModule extends ClientModule {

    private final double radius = 2.5; 
    private boolean direction = true; 

    public TargetStrafeModule() {
        super("TargetStrafe+", "Кружение вокруг цели, Анти-отдача и Авто-спринт", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (!this.isActive() || mc.player == null) return;

        // 1. ФУНКЦИЯ АВТО-БЕГА (AutoSprint)
        if (mc.options.forwardKey.isPressed() && !mc.player.isSneaking()) {
            mc.player.setSprinting(true);
        }

        // 2. ФУНКЦИЯ АНТИ-ОТКАТА (NoVelocity)
        if (mc.player.hurtTime > 0) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        }

        // 3. ФУНКЦИЯ СТРЕЙФОВ (TargetStrafe)
        Entity target = mc.targetedEntity; 
        if (target == null) return;

        if (mc.player.horizontalCollision) {
            direction = !direction;
        }

        if (mc.player.distanceTo(target) <= radius + 1.5) {
            // ИСПРАВЛЕНО: берём текущий yaw игрока напрямую для стабильной работы
            float yaw = mc.player.getYaw();
            double moveYaw = Math.toRadians(yaw + (direction ? 90 : -90));
            
            mc.player.setVelocity(
                -Math.sin(moveYaw) * 0.23,
                mc.player.getVelocity().y,
                Math.cos(moveYaw) * 0.23
            );
        }
    }
}
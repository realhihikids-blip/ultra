package com.pvpclient.module.movement;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.module.bot.BotGuard;
import com.pvpclient.setting.NumberSetting;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Ускоряет ходьбу через модификатор атрибута MOVEMENT_SPEED (тот же механизм, что у зелья
 * Скорости), а не подменой пакетов позиции — так и сервер, и ты сам видите одинаковое движение.
 *
 * Только singleplayer / LAN / authorizedServers (BotGuard): настоящий survival-сервер не разрешает
 * игроку самому задирать себе скорость — он либо откатит тебя назад («rubber-banding»), либо
 * античит забанит за скорость выше ожидаемой. Это не «обход», а честный ускоритель для своего мира.
 */
public final class SpeedModule extends ClientModule {
    private static final Identifier MODIFIER_ID = Identifier.of("pvpclient", "speed_boost");

    private final NumberSetting multiplier = add(new NumberSetting("Multiplier", 1.5, 1.0, 3.0, 0.05));

    private int warnTicks;

    public SpeedModule() {
        super("Speed", "Ускоряет ходьбу (singleplayer / свои серверы)", Category.MOVEMENT);
        markRestricted();
    }

    private EntityAttributeInstance attr() {
        return mc.player == null ? null : mc.player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
    }

    @Override
    protected void onEnable() { apply(); }

    @Override
    protected void onDisable() {
        EntityAttributeInstance a = attr();
        if (a != null) a.removeModifier(MODIFIER_ID);
    }

    private void apply() {
        EntityAttributeInstance a = attr();
        if (a == null) return;
        a.removeModifier(MODIFIER_ID);
        // ADD_MULTIPLIED_TOTAL: итоговая скорость умножается на (1 + value), т.е. value=0.5 → ×1.5
        a.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID, multiplier.get() - 1.0,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!BotGuard.allowed(mc)) {
            if (warnTicks++ % 60 == 0) mc.player.sendMessage(Text.literal("Speed: только singleplayer / LAN / authorizedServers"), true);
            setEnabled(false);
            return;
        }
        EntityAttributeInstance a = attr();
        if (a != null && !a.hasModifier(MODIFIER_ID)) apply(); // модификатор могли сбросить (смерть/респавн)
    }
}

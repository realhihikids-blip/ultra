package com.pvpclient.module.bot;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Подходит к сундуку, открывает его и забирает ценные предметы (shift-клик, по одному слоту за тик). */
final class Looter {
    enum Status { RUNNING, DONE, FAILED }

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final PathFollower follower;
    private final WorldScanner scanner;
    private final Pattern keyRe;
    private int phase, ticks, attempts;
    private final Set<Integer> skip = new HashSet<>();

    Looter(PathFollower follower, WorldScanner scanner, Pattern keyRe) {
        this.follower = follower;
        this.scanner = scanner;
        this.keyRe = keyRe;
    }

    /** Открыт ли наш контейнер (чтобы модуль не считал экран «меню игрока»). */
    boolean active() { return phase >= 1; }

    void reset() {
        if (phase >= 2 && mc.player != null) mc.player.closeHandledScreen();
        phase = 0; ticks = 0; attempts = 0; skip.clear();
    }

    Status tick(BlockPos chest, Mover mover) {
        switch (phase) {
            case 0 -> { // подход
                Vec3d c = Vec3d.ofCenter(chest);
                if (mc.player.getEyePos().distanceTo(c) <= 4.2 && scanner.visible(chest)) {
                    follower.cancel();
                    phase = 1;
                    return Status.RUNNING;
                }
                Mover.Go g = mover.go(PathFinder.near(chest, 2.5), false);
                if (g == Mover.Go.FAILED) { reset(); return Status.FAILED; }
                return Status.RUNNING;
            }
            case 1 -> { // открыть
                BlockState st = mc.world.getBlockState(chest);
                if (!st.hasBlockEntity()) { reset(); return Status.FAILED; }
                Vec3d c = Vec3d.ofCenter(chest);
                Look.at(mc, c, 60f);
                Direction face = Direction.getFacing(mc.player.getEyePos().x - c.x, mc.player.getEyePos().y - c.y, mc.player.getEyePos().z - c.z);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(c, face, chest, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                phase = 2; ticks = 0;
                return Status.RUNNING;
            }
            case 2 -> { // ждём окно сундука
                if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) { phase = 3; ticks = 0; return Status.RUNNING; }
                if (++ticks > 40) {
                    phase = 1; // повторная попытка
                    if (++attempts >= 3) { reset(); return Status.FAILED; }
                }
                return Status.RUNNING;
            }
            case 3 -> { // забираем лут
                ScreenHandler h = mc.player.currentScreenHandler;
                if (!(h instanceof GenericContainerScreenHandler gh)) { reset(); return Status.FAILED; }
                int size = gh.getRows() * 9;
                for (int i = 0; i < size; i++) {
                    if (skip.contains(i)) continue;
                    Slot s = h.slots.get(i);
                    if (!s.hasStack() || !BotInventory.valuable(s.getStack(), keyRe)) continue;
                    mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    if (++ticks > 120) { phase = 4; return Status.RUNNING; }
                    skip.add(i); // каждый слот пробуем один раз (если инвентарь полон — не зацикливаемся)
                    return Status.RUNNING;
                }
                phase = 4;
                return Status.RUNNING;
            }
            default -> { // закрыть
                mc.player.closeHandledScreen();
                phase = 0; ticks = 0; attempts = 0; skip.clear();
                return Status.DONE;
            }
        }
    }
}

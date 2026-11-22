package com.pvpclient.module.bot;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

/**
 * Крафт новой кирки, когда все старые почти сломаны.
 * Порядок: [брёвна → доски] → [верстак] → поставить/открыть верстак → [палки] → кирка.
 * Материалы: железные слитки > булыжник > доски. Если не хватает — просит модуль добыть блоки
 * (NEED_GATHER + gatherPred()).
 * Все клики делаются обычными кликами по слотам (никаких подмен пакетов).
 */
final class Crafter {
    enum Status { RUNNING, NEED_GATHER, DONE, FAILED }
    private enum Op { GRID, PLACE_TABLE, OPEN_TABLE, CLOSE }
    private enum Mat { IRON, STONE, WOOD }

    private static final class Job {
        final Op op;
        final int[] cells;
        final List<Predicate<ItemStack>> need;
        int phase, wait;
        Job(Op op, int[] cells, List<Predicate<ItemStack>> need) { this.op = op; this.cells = cells; this.need = need; }
    }

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final BotInventory inv;
    private final Deque<Job> jobs = new ArrayDeque<>();
    private boolean planned;
    private Predicate<BlockState> gather;

    Crafter(BotInventory inv) { this.inv = inv; }

    boolean active() { return planned; }
    Predicate<BlockState> gatherPred() { return gather; }

    void reset() {
        if (planned && mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
        jobs.clear();
        planned = false;
    }

    // ---------- предикаты предметов ----------
    private static final Predicate<ItemStack> LOG = s -> s.isIn(ItemTags.LOGS);
    private static final Predicate<ItemStack> PLANK = s -> s.isIn(ItemTags.PLANKS);
    private static final Predicate<ItemStack> STICK = s -> s.isOf(Items.STICK);
    private static final Predicate<ItemStack> IRON = s -> s.isOf(Items.IRON_INGOT);
    private static final Predicate<ItemStack> STONE = s -> s.isOf(Items.COBBLESTONE) || s.isOf(Items.COBBLED_DEEPSLATE) || s.isOf(Items.BLACKSTONE);

    private BlockPos findTable(int r) {
        BlockPos c = mc.player.getBlockPos();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(c.add(-r, -r, -r), c.add(r, r, r))) {
            if (!mc.world.getBlockState(p).isOf(Blocks.CRAFTING_TABLE)) continue;
            double d = p.getSquaredDistance(c);
            if (d < bd) { bd = d; best = p.toImmutable(); }
        }
        return best;
    }

    // ---------- планирование ----------
    private Status plan() {
        int iron = inv.count(IRON), stone = inv.count(STONE), planks = inv.count(PLANK);
        int logs = inv.count(LOG), sticks = inv.count(STICK);
        BlockPos tbl = findTable(4);
        boolean haveTable = tbl != null || inv.find(s -> s.isOf(Items.CRAFTING_TABLE)) >= 0;

        int plankNeed = (sticks >= 2 ? 0 : 2) + (haveTable ? 0 : 4);
        int budget = planks + logs * 4;
        Mat kind = null;
        if (iron >= 3 && budget >= plankNeed) kind = Mat.IRON;
        else if (stone >= 3 && budget >= plankNeed) kind = Mat.STONE;
        else if (budget >= plankNeed + 3) kind = Mat.WOOD;

        if (kind == null) {
            boolean needWood = budget < plankNeed || !inv.hasAnyPick();
            gather = needWood
                    ? s -> s.isIn(BlockTags.LOGS)
                    : s -> s.isOf(Blocks.STONE) || s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.DEEPSLATE);
            return Status.NEED_GATHER;
        }

        int needPlanks = plankNeed + (kind == Mat.WOOD ? 3 : 0);
        for (int have = planks; have < needPlanks; have += 4) jobs.add(new Job(Op.GRID, new int[]{1}, List.of(LOG)));
        if (!haveTable) jobs.add(new Job(Op.GRID, new int[]{1, 2, 3, 4}, List.of(PLANK, PLANK, PLANK, PLANK)));
        if (tbl == null) jobs.add(new Job(Op.PLACE_TABLE, new int[0], List.of()));
        jobs.add(new Job(Op.OPEN_TABLE, new int[0], List.of()));
        if (sticks < 2) jobs.add(new Job(Op.GRID, new int[]{1, 4}, List.of(PLANK, PLANK)));
        Predicate<ItemStack> mat = kind == Mat.IRON ? IRON : kind == Mat.STONE ? STONE : PLANK;
        jobs.add(new Job(Op.GRID, new int[]{1, 2, 3, 5, 8}, List.of(mat, mat, mat, STICK, STICK)));
        jobs.add(new Job(Op.CLOSE, new int[0], List.of()));
        planned = true;
        return null;
    }

    // ---------- главный тик ----------
    Status tick() {
        if (!planned) {
            Status s = plan();
            if (s != null) return s;
        }
        Job j = jobs.peek();
        if (j == null) { planned = false; return Status.DONE; }
        int r = switch (j.op) {
            case GRID -> grid(j);
            case PLACE_TABLE -> placeTable(j);
            case OPEN_TABLE -> openTable(j);
            case CLOSE -> { mc.player.closeHandledScreen(); yield 1; }
        };
        if (r < 0) { reset(); return Status.FAILED; }
        if (r > 0) jobs.poll();
        return Status.RUNNING;
    }

    // ---------- работа с сеткой крафта ----------
    private void click(ScreenHandler h, int slot, int button, SlotActionType t) {
        mc.interactionManager.clickSlot(h.syncId, slot, button, t, mc.player);
    }

    private int source(ScreenHandler h, Predicate<ItemStack> p) {
        for (int i = 0; i < h.slots.size(); i++) {
            var s = h.slots.get(i);
            if (s.inventory == mc.player.getInventory() && s.hasStack() && p.test(s.getStack())) return i;
        }
        return -1;
    }

    /** 0 — идёт, 1 — готово, -1 — ошибка. */
    private int grid(Job j) {
        ScreenHandler h = mc.player.currentScreenHandler;
        switch (j.phase) {
            case 0 -> { // раскладываем по одному предмету в нужные клетки
                for (int c : j.cells) if (c > 4 && !(h instanceof CraftingScreenHandler)) return -1;
                for (int i = 0; i < j.cells.length; i++) {
                    int src = source(h, j.need.get(i));
                    if (src < 0) return -1;
                    click(h, src, 0, SlotActionType.PICKUP);         // взяли стак
                    click(h, j.cells[i], 1, SlotActionType.PICKUP);  // правый клик — положили 1 штуку
                    click(h, src, 0, SlotActionType.PICKUP);         // вернули остаток
                }
                j.phase = 1; j.wait = 0;
            }
            case 1 -> { // ждём результат от сервера
                if (h.getSlot(0).hasStack()) j.phase = 2;
                else if (++j.wait > 30) return -1;
            }
            case 2 -> { click(h, 0, 0, SlotActionType.QUICK_MOVE); j.phase = 3; }
            default -> { // убираем остатки из сетки
                for (int c : j.cells) if (h.getSlot(c).hasStack()) click(h, c, 0, SlotActionType.QUICK_MOVE);
                return 1;
            }
        }
        return 0;
    }

    private int placeTable(Job j) {
        if (j.phase == 0) {
            int slot = inv.toHotbar(inv.find(s -> s.isOf(Items.CRAFTING_TABLE)));
            if (slot < 0) return -1;
            inv.select(slot);
            BlockPos p = mc.player.getBlockPos();
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos n = p.offset(d);
                if (BotUtil.passable(mc.world, n) && BotUtil.canStand(mc.world, n.down())) {
                    Vec3d hit = Vec3d.ofCenter(n.down()).add(0, 0.5, 0);
                    Look.at(mc, hit, 80f);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, n.down(), false));
                    mc.player.swingHand(Hand.MAIN_HAND);
                    j.phase = 1; j.wait = 0;
                    return 0;
                }
            }
            return -1;
        }
        if (findTable(4) != null) return 1;
        return ++j.wait > 20 ? -1 : 0;
    }

    private int openTable(Job j) {
        if (mc.player.currentScreenHandler instanceof CraftingScreenHandler) return 1;
        if (j.phase == 0 || j.wait++ > 40) {
            BlockPos t = findTable(4);
            if (t == null) return -1;
            if (j.phase > 2) return -1;
            Vec3d c = Vec3d.ofCenter(t);
            Look.at(mc, c, 80f);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(c.add(0, 0.5, 0), Direction.UP, t, false));
            j.phase++;
            j.wait = 0;
        }
        return 0;
    }
}

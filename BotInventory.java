package com.pvpclient.module.bot;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Всё, что бот делает с инвентарём: еда, кирки и их прочность, выбор инструмента, ценный лут. */
public final class BotInventory {
    private static final Set<Item> BAD_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
            Items.PUFFERFISH, Items.CHICKEN, Items.CHORUS_FRUIT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.SUSPICIOUS_STEW);
    private static final List<Item> PICKS = List.of(Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE,
            Items.STONE_PICKAXE, Items.GOLDEN_PICKAXE, Items.WOODEN_PICKAXE);
    private static final Set<Item> SCAFFOLD = Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT,
            Items.NETHERRACK, Items.STONE);
    private static final Set<Item> VALUABLE = Set.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT,
            Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS, Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE, Items.ENCHANTED_BOOK, Items.NAME_TAG, Items.SADDLE, Items.TOTEM_OF_UNDYING,
            Items.TRIAL_KEY, Items.OMINOUS_TRIAL_KEY, Items.HEAVY_CORE, Items.EXPERIENCE_BOTTLE, Items.FILLED_MAP,
            Items.IRON_HORSE_ARMOR, Items.GOLDEN_HORSE_ARMOR, Items.DIAMOND_HORSE_ARMOR);

    private final MinecraftClient mc = MinecraftClient.getInstance();
    /** Кирка с долей прочности ниже этого порога считается «почти сломанной». */
    public double pickMinFrac = 0.15;

    // ---------------- базовые операции ----------------
    private ItemStack at(int i) { return mc.player.getInventory().getStack(i); }

    /** Индекс слота (0–35: хотбар 0–8 + основной инвентарь) или -1. */
    public int find(Predicate<ItemStack> p) {
        for (int i = 0; i < 36; i++) if (!at(i).isEmpty() && p.test(at(i))) return i;
        return -1;
    }

    public int count(Predicate<ItemStack> p) {
        int n = 0;
        for (int i = 0; i < 36; i++) if (!at(i).isEmpty() && p.test(at(i))) n += at(i).getCount();
        return n;
    }

    public void select(int hotbar) {
        if (hotbar >= 0 && hotbar <= 8) mc.player.getInventory().setSelectedSlot(hotbar);
    }

    /** Переносит предмет в хотбар (меняет местами с выбранным слотом) и возвращает номер хотбар-слота. */
    public int toHotbar(int idx) {
        if (idx < 0) return -1;
        if (idx < 9) return idx;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return -1;
        int target = mc.player.getInventory().selectedSlot;
        // в PlayerScreenHandler основной инвентарь = слоты 9–35
        mc.interactionManager.clickSlot(0, idx, target, SlotActionType.SWAP, mc.player);
        return target;
    }

    static double frac(ItemStack s) {
        int max = s.getMaxDamage();
        return max <= 0 ? 1.0 : 1.0 - (double) s.getDamage() / max;
    }

    // ---------------- кирки ----------------
    public static boolean isPick(ItemStack s) { return s.isIn(ItemTags.PICKAXES); }

    /** Лучшая кирка, у которой прочности больше порога (или -1). */
    public int bestUsablePick() {
        int best = -1, bestTier = Integer.MAX_VALUE;
        for (int i = 0; i < 36; i++) {
            ItemStack s = at(i);
            if (s.isEmpty() || !isPick(s) || frac(s) <= pickMinFrac) continue;
            int tier = PICKS.indexOf(s.getItem());
            if (tier < 0) tier = PICKS.size();
            if (tier < bestTier) { bestTier = tier; best = i; }
        }
        return best;
    }

    public boolean needsNewPick() { return bestUsablePick() < 0; }

    public boolean hasAnyPick() { return find(BotInventory::isPick) >= 0; }

    /** Доля прочности лучшей ГОДНОЙ кирки (0 — нет годной кирки вообще, значит «предельно изношена»). */
    public double pickWearFrac() {
        int i = bestUsablePick();
        return i < 0 ? 0.0 : frac(at(i));
    }

    public boolean equipBestPick() {
        int i = bestUsablePick();
        if (i < 0) return false;
        select(toHotbar(i));
        return true;
    }

    /** Берём в руку инструмент, который быстрее всего ломает этот блок (изношенные кирки — в последнюю очередь). */
    public void equipBestTool(BlockState state) {
        boolean haveGoodPick = !needsNewPick();
        int best = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 36; i++) {
            ItemStack s = at(i);
            if (s.isEmpty()) continue;
            if (isPick(s) && haveGoodPick && frac(s) <= pickMinFrac) continue;
            float sp = s.getMiningSpeedMultiplier(state);
            if (sp > bestSpeed) { bestSpeed = sp; best = i; }
        }
        if (best >= 0 && best != mc.player.getInventory().selectedSlot) select(toHotbar(best));
    }

    // ---------------- еда ----------------
    /** Лучшая еда (по восстановлению сытости) или -1. Ядовитую и ценную еду не берём. */
    public int findFood() {
        int best = -1, bestN = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = at(i);
            if (s.isEmpty() || BAD_FOOD.contains(s.getItem())) continue;
            FoodComponent f = s.get(DataComponentTypes.FOOD);
            if (f != null && f.nutrition() > bestN) { bestN = f.nutrition(); best = i; }
        }
        return best;
    }

    public int foodToHotbar() { return toHotbar(findFood()); }

    // ---------------- меч, блоки для моста ----------------
    public void equipSword() {
        if (mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) return;
        int best = -1, bestDur = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = at(i);
            if (!s.isEmpty() && s.isIn(ItemTags.SWORDS) && s.getMaxDamage() > bestDur) { bestDur = s.getMaxDamage(); best = i; }
        }
        if (best >= 0) select(toHotbar(best));
    }

    public boolean hasScaffold() { return find(s -> SCAFFOLD.contains(s.getItem())) >= 0; }

    public int scaffoldHotbar() { return toHotbar(find(s -> SCAFFOLD.contains(s.getItem()))); }

    // ---------------- ценный лут ----------------
    /** Ценный предмет: по списку, по алмазной/незеритовой экипировке и по ключам/карточкам (имя + NBT). */
    public static boolean valuable(ItemStack s, Pattern keyRe) {
        if (s.isEmpty()) return false;
        if (VALUABLE.contains(s.getItem())) return true;
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        if (id.startsWith("diamond_") || id.startsWith("netherite_") || id.contains("music_disc")) return true;
        StringBuilder labels = new StringBuilder(id).append('|').append(s.getName().getString());
        NbtComponent custom = s.get(DataComponentTypes.CUSTOM_DATA);
        if (custom != null) labels.append('|').append(custom.copyNbt());
        return keyRe.matcher(labels).find();
    }
}

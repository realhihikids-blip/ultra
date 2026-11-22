package com.pvpclient.module.bot;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.CombatUtil;
import com.pvpclient.util.ItemUser;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Автономный бот-исследователь (аналог моего Node.js-бота, но внутри клиента Fabric 1.21.4).
 *
 * Конечный автомат, приоритет сверху вниз:
 *   EAT     — сытость ниже порога: стоп, лучшая еда из инвентаря;
 *   DEFEND  — враждебный моб рядом: меч и удар;
 *   REPAIR  — кирка < N% прочности: запасная → иначе собрать материалы и скрафтить;
 *   MINE    — видимая открытая руда в радиусе 10–15 блоков;
 *   LOOT    — найденный данж/шахта: подойти к сундуку, забрать ценное;
 *   DESCEND — копать лестницу вниз до заданной высоты;
 *   EXPLORE — идти в неисследованную сторону.
 * Работает только в singleplayer/LAN или на серверах из authorizedServers (см. BotGuard).
 */
public final class DungeonBotModule extends ClientModule {
    private final NumberSetting scanRadius = add(new NumberSetting("Scan Radius", 96, 64, 128, 8));
    private final NumberSetting oreRadius = add(new NumberSetting("Ore Radius", 12, 10, 15, 1));
    private final NumberSetting eatBelow = add(new NumberSetting("Eat Below", 16, 4, 19, 1));
    private final NumberSetting pickMin = add(new NumberSetting("Pickaxe Min %", 15, 5, 50, 1));
    private final NumberSetting descendY = add(new NumberSetting("Descend To Y", 12, -50, 60, 1));
    private final BooleanSetting overlay = add(new BooleanSetting("Show Overlay", true));
    private final NumberSetting overlayRadius = add(new NumberSetting("Overlay Radius", 24, 8, 64, 1));
    private final BooleanSetting oreOnlyLowDurability = add(new BooleanSetting("Mine Ore Only If Low Durability", true));
    private final NumberSetting oreDurabilityThreshold = add(new NumberSetting("Low Durability Threshold %", 50, 10, 100, 5));

    private enum S { EAT, DEFEND, REPAIR, MINE, LOOT, DESCEND, EXPLORE }

    private final BotInventory inv = new BotInventory();
    private final WorldScanner scanner = new WorldScanner();
    private final PathFollower follower = new PathFollower(inv);
    private final Looter looter = new Looter(follower, scanner,
            Pattern.compile("ключ|key|карточк|card|ticket|билет|пропуск|pass", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    private final Crafter crafter = new Crafter(inv);
    private final BotRenderer renderer = new BotRenderer();
    private final Mover mover = this::goTo;

    private S state = S.EXPLORE;
    private final Set<Long> visited = new HashSet<>();
    private BlockPos exploreGoal, gatherTarget, pickupPos;
    private int exploreCount, repairCooldown, descendCooldown, pickupTicks, mineTicks;

    public DungeonBotModule() {
        super("Dungeon Bot", "Автономный бот: поиск данжей, добыча руды, лут сундуков (singleplayer / свои серверы)", Category.AUTOMATION);
        markRestricted();
    }

    @Override
    protected void onDisable() {
        follower.cancel();
        looter.reset();
        crafter.reset();
        ItemUser.finish(mc);
        scanner.reset();
        exploreGoal = null; gatherTarget = null; pickupPos = null; pickupTicks = 0;
    }

    // =====================================================================
    //  Главный тик
    // =====================================================================
    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        if (!BotGuard.allowed(mc)) {
            p.sendMessage(Text.literal("Dungeon Bot: работает только в одиночной игре / LAN или на сервере из authorizedServers"), true);
            setEnabled(false);
            return;
        }
        if (!p.isAlive()) { follower.cancel(); return; }
        // Игрок сам открыл меню — не мешаем (экраны бота: сундук и верстак — разрешены)
        if (mc.currentScreen != null && !looter.active() && !crafter.active()) { follower.stopKeys(); return; }

        inv.pickMinFrac = pickMin.get() / 100.0;
        if (repairCooldown > 0) repairCooldown--;
        if (descendCooldown > 0) descendCooldown--;
        scanner.tick(scanRadius.getInt(), oreRadius.getInt());

        S want = decide();
        if (want != state) leave(state);
        state = want;

        switch (state) {
            case EAT -> tickEat();
            case DEFEND -> tickDefend();
            case REPAIR -> tickRepair();
            case MINE -> tickMine();
            case LOOT -> tickLoot();
            case DESCEND -> tickDescend();
            case EXPLORE -> tickExplore();
        }
    }

    /** Очистка при смене состояния. */
    private void leave(S old) {
        follower.cancel();
        if (old == S.EAT) ItemUser.finish(mc);
        if (old == S.LOOT) looter.reset();
        if (old == S.REPAIR) { crafter.reset(); gatherTarget = null; }
        exploreGoal = null;
        mineTicks = 0;
    }

    private S decide() {
        ClientPlayerEntity p = mc.player;
        if (state == S.EAT && ItemUser.busy()) return S.EAT;
        if (p.getHungerManager().getFoodLevel() < eatBelow.getInt() && inv.findFood() >= 0) return S.EAT;
        if (nearestHostile(5.0) != null) return S.DEFEND;
        if (repairCooldown <= 0 && inv.needsNewPick()) return S.REPAIR;
        boolean pickLowEnough = !oreOnlyLowDurability.get() || inv.pickWearFrac() <= oreDurabilityThreshold.get() / 100.0;
        if (pickupTicks > 0 || (pickLowEnough && scanner.ore() != null)) return S.MINE;
        if (!scanner.targets().isEmpty()) return S.LOOT;
        if (p.getBlockY() > descendY.getInt() + 8 && exploreCount % 3 == 2 && descendCooldown <= 0) return S.DESCEND;
        return S.EXPLORE;
    }

    // =====================================================================
    //  Передвижение к цели (общий помощник)
    // =====================================================================
    private Mover.Go goTo(PathFinder.Goal goal, boolean dig) {
        BlockPos start = mc.player.getBlockPos();
        if (goal.reached(start) && !follower.active()) return Mover.Go.ARRIVED;
        if (!follower.active()) {
            List<PathFinder.Node> path = PathFinder.find(mc.world, start, goal, dig, inv.hasScaffold(), 3500);
            if (path.isEmpty()) return goal.reached(start) ? Mover.Go.ARRIVED : Mover.Go.FAILED;
            follower.set(path);
        }
        PathFollower.Status s = follower.tick();
        if (s == PathFollower.Status.FAILED) return Mover.Go.FAILED;
        return Mover.Go.MOVING;
    }

    // =====================================================================
    //  Состояния
    // =====================================================================
    /** EAT: остановиться, взять лучшую еду в хотбар и удерживать «использовать». */
    private void tickEat() {
        follower.cancel();
        if (!ItemUser.busy()) {
            int slot = inv.foodToHotbar();
            if (slot >= 0) ItemUser.begin(mc, slot);
        }
    }

    /** DEFEND: меч в руку, повернуться к мобу, подойти и бить, когда откатился кулдаун. */
    private void tickDefend() {
        LivingEntity m = nearestHostile(6.0);
        if (m == null) return;
        follower.cancel();
        inv.equipSword();
        Vec3d c = m.getBoundingBox().getCenter();
        Look.at(mc, c, 40f);
        double d = CombatUtil.distanceToBox(mc.player.getEyePos(), m);
        mc.options.forwardKey.setPressed(d > 2.6);
        if (d <= 3.0 && mc.player.getAttackCooldownProgress(0.5f) >= 1.0f) CombatUtil.attack(mc, m);
    }

    private LivingEntity nearestHostile(double r) {
        LivingEntity best = null;
        double bd = r * r;
        for (Entity e : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(r))) {
            if (!(e instanceof Monster) || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
            double d = mc.player.squaredDistanceTo(e);
            if (d < bd) { bd = d; best = le; }
        }
        return best;
    }

    /** REPAIR: запасная кирка → иначе собрать материалы (блоки) и скрафтить новую. */
    private void tickRepair() {
        if (handlePickup()) return;
        if (!inv.needsNewPick()) { inv.equipBestPick(); crafter.reset(); gatherTarget = null; return; }

        if (gatherTarget != null) {
            if (mineStep(gatherTarget)) { pickupPos = gatherTarget; pickupTicks = 30; gatherTarget = null; }
            return;
        }
        switch (crafter.tick()) {
            case NEED_GATHER -> {
                gatherTarget = scanner.findNearest(crafter.gatherPred(), 24);
                if (gatherTarget == null) { repairCooldown = 1200; crafter.reset(); }
            }
            case FAILED -> repairCooldown = 1200;
            default -> { }
        }
    }

    /** MINE: добыть видимую руду. */
    private void tickMine() {
        if (handlePickup()) return;
        BlockPos ore = scanner.ore();
        if (ore == null) return;
        if (mineStep(ore)) { pickupPos = ore; pickupTicks = 30; scanner.clearOre(); }
    }

    /**
     * Один шаг добычи блока t. Возвращает true, когда задача закончена
     * (блок сломан или признан недостижимым).
     */
    private boolean mineStep(BlockPos t) {
        if (BotUtil.passable(mc.world, t) && mc.world.getBlockState(t).isAir()) { follower.cancel(); return true; }
        if (++mineTicks > 700) { scanner.badOre(t); mineTicks = 0; follower.cancel(); return true; }

        boolean inReach = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(t)) <= 4.3 && scanner.visible(t);
        if (inReach) {
            if (follower.active()) follower.cancel();
            mc.options.forwardKey.setPressed(false);
            if (!follower.mineBlock(t)) { scanner.badOre(t); mineTicks = 0; return true; }
            return false;
        }
        Mover.Go g = goTo(PathFinder.near(t, 3.0), true);
        if (g == Mover.Go.FAILED) { scanner.badOre(t); mineTicks = 0; return true; }
        return false;
    }

    /** После добычи — шагнуть к месту блока, чтобы подобрать выпавший предмет. */
    private boolean handlePickup() {
        if (pickupTicks <= 0 || pickupPos == null) return false;
        pickupTicks--;
        Mover.Go g = goTo(PathFinder.near(pickupPos, 1.0), false);
        if (g != Mover.Go.MOVING || pickupTicks == 0) { follower.cancel(); pickupTicks = 0; }
        return pickupTicks > 0;
    }

    /** LOOT: идём к ближайшему данжу/сундуку и забираем ценное. */
    private void tickLoot() {
        WorldScanner.Target t = scanner.nearest();
        if (t == null) return;
        if (t.chest()) {
            Looter.Status s = looter.tick(t.pos(), mover);
            if (s == Looter.Status.DONE) scanner.markDone(t.pos());
            else if (s == Looter.Status.FAILED) scanner.fail(t.pos());
        } else { // спавнер без сундука — просто подойти, чтобы увидеть содержимое данжа
            Mover.Go g = goTo(PathFinder.near(t.pos(), 6.0), false);
            if (g == Mover.Go.ARRIVED) scanner.markDone(t.pos());
            else if (g == Mover.Go.FAILED) scanner.fail(t.pos());
        }
    }

    /** DESCEND: копаем «лестницу» вниз до нужной высоты (A* с прокапыванием и безопасными проверками). */
    private void tickDescend() {
        Mover.Go g = goTo(PathFinder.belowY(descendY.getInt()), true);
        if (g != Mover.Go.MOVING) { descendCooldown = 600; exploreCount++; follower.cancel(); }
    }

    /** EXPLORE: идём в соседнюю неисследованную клетку 32×32. */
    private void tickExplore() {
        if (exploreGoal == null) {
            exploreGoal = pickExploreGoal();
            if (exploreGoal == null) { visited.clear(); return; }
        }
        Mover.Go g = goTo(PathFinder.xz(exploreGoal.getX(), exploreGoal.getZ()), true);
        if (g != Mover.Go.MOVING) {
            visited.add(cell(exploreGoal.getX(), exploreGoal.getZ()));
            visited.add(cell(mc.player.getBlockX(), mc.player.getBlockZ()));
            exploreGoal = null;
            exploreCount++;
        }
    }

    private static long cell(int x, int z) { return ((long) Math.floorDiv(x, 32) << 32) | (Math.floorDiv(z, 32) & 0xFFFFFFFFL); }

    private BlockPos pickExploreGoal() {
        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < 8; i++) angles.add(i * Math.PI / 4);
        Collections.shuffle(angles);
        BlockPos me = mc.player.getBlockPos();
        for (int dist : new int[]{48, 96}) {
            for (double a : angles) {
                int tx = me.getX() + (int) Math.round(Math.cos(a) * dist);
                int tz = me.getZ() + (int) Math.round(Math.sin(a) * dist);
                if (!visited.contains(cell(tx, tz))) return new BlockPos(tx, me.getY(), tz);
            }
        }
        return null;
    }

    // =====================================================================
    //  Визуализация
    // =====================================================================
    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (!overlay.get()) return;
        BlockPos mt = follower.miningPos();
        if (mt == null) mt = scanner.ore();
        renderer.render(context, follower, mt, overlayRadius.get());
    }
}

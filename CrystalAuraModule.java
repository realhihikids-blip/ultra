package com.pvpclient.module.combat;

import com.pvpclient.module.Category;
import com.pvpclient.module.ClientModule;
import com.pvpclient.module.bot.BotGuard;
import com.pvpclient.module.bot.BotInventory;
import com.pvpclient.setting.BooleanSetting;
import com.pvpclient.setting.ModeSetting;
import com.pvpclient.setting.NumberSetting;
import com.pvpclient.util.CombatUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Crystal Aura: ставит обсидиан рядом с противником, на него — кристалл, и взрывает его,
 * ТОЛЬКО если расчёт урона говорит, что тебе это безопасно.
 *
 * Расчёт: ванильная формула взрыва (сила 6): дистанция, экспозиция (лучи через блоки),
 * броня и стойкость брони — без надуманных запасов, иначе модуль почти никогда не находит
 * «безопасное» место (раньше тут был множитель ×1.5, из-за которого самоурон всегда казался
 * завышенным и кристалл почти никогда не ставился/не взрывался).
 * Условия безопасности: урон по тебе ≤ Max Self Damage и после взрыва остаётся ≥ Safety HP.
 *
 * ВАЖНО: чтобы дотянуться до кристалла, ты обычно стоишь так же близко к нему, как и противник,
 * так что какой-то самоурон — это нормально, не баг. Если модуль «ничего не делает», это почти
 * всегда означает, что на вас нет брони и честный расчёт не находит ни одного безопасного места —
 * наденьте броню или поднимите Max Self Damage/Safety HP.
 *
 * Работает только в одиночной игре / LAN / на серверах из authorizedServers (BotGuard).
 * Предметы (обсидиан, кристаллы) должны быть в инвентаре; в хотбар переносятся автоматически.
 */
public final class CrystalAuraModule extends ClientModule {
    private final ModeSetting targets = add(new ModeSetting("Targets", 0, CombatUtil.TARGET_MODES));
    private final NumberSetting targetRange = add(new NumberSetting("Target Range", 10, 4, 16, 1));
    private final NumberSetting placeRange = add(new NumberSetting("Place Range", 4.5, 3.0, 5.0, 0.1));
    private final NumberSetting minTarget = add(new NumberSetting("Min Target Damage", 5, 1, 36, 0.5));
    private final NumberSetting maxSelf = add(new NumberSetting("Max Self Damage", 9, 0, 20, 0.5));
    private final NumberSetting safetyHp = add(new NumberSetting("Safety HP", 6, 1, 20, 0.5));
    private final NumberSetting placeDelay = add(new NumberSetting("Place Delay (ticks)", 2, 0, 10, 1));
    private final NumberSetting breakDelay = add(new NumberSetting("Break Delay (ticks)", 1, 0, 10, 1));
    private final BooleanSetting placeObsidian = add(new BooleanSetting("Place Obsidian", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate Camera", true));

    private record Spot(BlockPos base, boolean needsObsidian, float target, float self, BlockPos clickBlock, Direction clickFace) {}

    private final BotInventory inv = new BotInventory();
    private int placeTicks, breakTicks, planTicks;
    private Spot plan;

    public CrystalAuraModule() {
        super("Crystal Aura", "Обсидиан + кристалл рядом с врагом с расчётом урона (singleplayer / свои серверы)", Category.COMBAT);
        markRestricted();
    }

    @Override protected void onDisable() { plan = null; }

    // ------------------------------------------------------------------
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!BotGuard.allowed(mc)) return;
        placeTicks++; breakTicks++;

        LivingEntity target = CombatUtil.findTarget(mc, targetRange.get(), 360.0, targets.get(), false);
        if (target == null) { plan = null; return; }

        boolean haveCrystal = inv.find(s -> s.isOf(Items.END_CRYSTAL)) >= 0;
        if (!haveCrystal) { warn("нет кристаллов в инвентаре"); return; }

        // 1) Взорвать кристалл, если это безопасно
        if (breakTicks >= breakDelay.getInt() && tryBreak(target)) { breakTicks = 0; return; }

        // 2) Раз в 3 тика пересчитываем лучшее место
        if (planTicks++ % 3 == 0) {
            plan = computePlan(target);
            if (plan == null) warn("нет безопасного места (нужна броня или подними Max Self Damage)");
        }
        if (plan == null || placeTicks < placeDelay.getInt()) return;

        if (plan.needsObsidian()) {
            if (!placeObsidian.get() || !placeBlock(plan)) return;
        } else if (!placeCrystal(plan.base())) return;
        placeTicks = 0;
    }

    // ---------------------------- диагностика ----------------------------
    private int warnTicks;
    private void warn(String msg) {
        if (warnTicks++ % 40 == 0) mc.player.sendMessage(net.minecraft.text.Text.literal("Crystal Aura: " + msg), true);
    }

    // ---------------------------- действия ----------------------------
    private boolean tryBreak(LivingEntity target) {
        Vec3d eye = mc.player.getEyePos();
        EndCrystalEntity best = null;
        float bestScore = -999;
        List<EndCrystalEntity> list = mc.world.getEntitiesByClass(EndCrystalEntity.class,
                mc.player.getBoundingBox().expand(10.0), c -> c.isAlive());
        for (EndCrystalEntity c : list) {
            if (eye.distanceTo(c.getPos()) > 4.5) continue;
            Vec3d center = c.getPos().add(0, 0.06, 0);
            if (c.squaredDistanceTo(target) > 64) continue; // чужие кристаллы не трогаем
            float self = damage(mc.player, center), tgt = damage(target, center);
            if (!safe(self)) continue;
            float score = tgt - self;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        if (best == null) return false;
        if (rotate.get()) aim(best.getPos().add(0, 0.5, 0));
        CombatUtil.attack(mc, best);
        return true;
    }

    private boolean placeCrystal(BlockPos base) {
        if (!selectItem(Items.END_CRYSTAL)) return false;
        Vec3d hit = Vec3d.ofCenter(base).add(0, 0.5, 0);
        if (mc.player.getEyePos().distanceTo(hit) > placeRange.get()) return false;
        if (rotate.get()) aim(hit);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, base, false));
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private boolean placeBlock(Spot s) {
        if (!selectItem(Items.OBSIDIAN)) return false;
        Vec3d hit = Vec3d.ofCenter(s.clickBlock()).add(s.clickFace().getOffsetX() * 0.5, s.clickFace().getOffsetY() * 0.5, s.clickFace().getOffsetZ() * 0.5);
        if (rotate.get()) aim(hit);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hit, s.clickFace(), s.clickBlock(), false));
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /** Берёт предмет в руку (переносит в хотбар при необходимости). */
    private boolean selectItem(Item item) {
        if (mc.player.getMainHandStack().isOf(item)) return true;
        int idx = inv.find(s -> s.isOf(item));
        if (idx < 0) return false;
        int slot = inv.toHotbar(idx);
        if (slot < 0) return false;
        inv.select(slot);
        return true;
    }

    // ---------------------------- планирование ----------------------------
    private Spot computePlan(LivingEntity target) {
        Vec3d eye = mc.player.getEyePos();
        double reach = placeRange.get();
        BlockPos tp = target.getBlockPos();
        Box tBox = target.getBoundingBox(), pBox = mc.player.getBoundingBox();
        boolean haveCrystal = inv.find(s -> s.isOf(Items.END_CRYSTAL)) >= 0;
        boolean haveObsidian = placeObsidian.get() && inv.find(s -> s.isOf(Items.OBSIDIAN)) >= 0;
        if (!haveCrystal) return null;

        List<Spot> rough = new ArrayList<>();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = -2; y <= 1; y++) {
            BlockPos p = tp.add(x, y, z);
            BlockState st = mc.world.getBlockState(p);
            boolean isBase = st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK);
            boolean canPlaceObs = haveObsidian && st.isAir();
            if (!isBase && !canPlaceObs) continue;
            if (!mc.world.getBlockState(p.up()).isAir() || !mc.world.getBlockState(p.up(2)).isAir()) continue;
            Box crystalBox = new Box(p.getX(), p.getY() + 1, p.getZ(), p.getX() + 1, p.getY() + 3, p.getZ() + 1);
            if (!mc.world.getOtherEntities(null, crystalBox).isEmpty()) continue;
            if (!isBase && (tBox.intersects(new Box(p)) || pBox.intersects(new Box(p)))) continue;
            if (eye.distanceTo(Vec3d.ofCenter(p)) > reach) continue;

            Vec3d center = new Vec3d(p.getX() + 0.5, p.getY() + 1.0625, p.getZ() + 0.5);
            float tEst = estimate(target, center), sEst = estimate(mc.player, center);
            if (tEst < minTarget.get() || sEst > maxSelf.get() + 4) continue; // грубый отсев
            BlockPos cb = null; Direction cf = null;
            if (!isBase) { // нужен сосед, по которому кликнем, чтобы поставить обсидиан
                for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    BlockPos nb = p.offset(d);
                    if (!mc.world.getBlockState(nb).isSolidBlock(mc.world, nb)) continue;
                    Vec3d hv = Vec3d.ofCenter(nb).add(d.getOpposite().getOffsetX() * 0.5, d.getOpposite().getOffsetY() * 0.5, d.getOpposite().getOffsetZ() * 0.5);
                    if (eye.distanceTo(hv) > reach) continue;
                    cb = nb; cf = d.getOpposite();
                    break;
                }
                if (cb == null) continue;
            }
            rough.add(new Spot(p, !isBase, tEst, sEst, cb, cf));
        }
        rough.sort(Comparator.comparingDouble((Spot s) -> -(s.target() - s.self())));

        Spot best = null;
        float bestScore = -999;
        for (int i = 0; i < Math.min(10, rough.size()); i++) { // точный расчёт только для лучших кандидатов
            Spot s = rough.get(i);
            Vec3d center = new Vec3d(s.base().getX() + 0.5, s.base().getY() + 1.0625, s.base().getZ() + 0.5);
            float t = damage(target, center), self = damage(mc.player, center);
            if (t < minTarget.get() || !safe(self)) continue;
            float score = t - self * 1.5f + (s.needsObsidian() ? -2f : 0f); // готовая база предпочтительнее
            if (score > bestScore) { bestScore = score; best = new Spot(s.base(), s.needsObsidian(), t, self, s.clickBlock(), s.clickFace()); }
        }
        return best;
    }

    private boolean safe(float selfDamage) {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return selfDamage <= maxSelf.get() && hp - selfDamage >= safetyHp.get();
    }

    // ---------------------------- расчёт урона ----------------------------
    /** Быстрая оценка без лучей (экспозиция = 1). */
    private float estimate(LivingEntity e, Vec3d center) { return calc(e, center, 1.0); }

    /** Точный расчёт с экспозицией. */
    private float damage(LivingEntity e, Vec3d center) {
        if (e.getPos().distanceTo(center) > 12.0) return 0;
        return calc(e, center, exposure(center, e));
    }

    private float calc(LivingEntity e, Vec3d center, double exposure) {
        double dist = e.getPos().distanceTo(center) / 12.0; // сила 6 → радиус 12
        if (dist > 1.0) return 0;
        double impact = (1.0 - dist) * exposure;
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
        float armor = e.getArmor();
        float tough = (float) e.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float g = MathHelper.clamp(armor - dmg / (2.0f + tough / 4.0f), armor * 0.2f, 20.0f);
        return Math.max(0f, dmg * (1.0f - g / 25.0f));
    }

    /** Как в ваниле: доля точек хитбокса, до которых от центра взрыва долетает луч без препятствий. */
    private double exposure(Vec3d center, LivingEntity e) {
        Box b = e.getBoundingBox();
        double dx = 1.0 / ((b.maxX - b.minX) * 2.0 + 1.0), dy = 1.0 / ((b.maxY - b.minY) * 2.0 + 1.0), dz = 1.0 / ((b.maxZ - b.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0, oz = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
        int hit = 0, total = 0;
        for (double x = 0; x <= 1.0; x += dx) for (double y = 0; y <= 1.0; y += dy) for (double z = 0; z <= 1.0; z += dz) {
            Vec3d p = new Vec3d(MathHelper.lerp(x, b.minX, b.maxX) + ox, MathHelper.lerp(y, b.minY, b.maxY), MathHelper.lerp(z, b.minZ, b.maxZ) + oz);
            var r = mc.world.raycast(new RaycastContext(p, center, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
            if (r.getType() == HitResult.Type.MISS) hit++;
            total++;
        }
        return total == 0 ? 0 : (double) hit / total;
    }

    // ---------------------------- камера ----------------------------
    private void aim(Vec3d t) {
        Vec3d eye = mc.player.getEyePos();
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - mc.player.getYaw()), -70f, 70f));
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(pitch - mc.player.getPitch(), -70f, 70f), -90f, 90f));
    }
}

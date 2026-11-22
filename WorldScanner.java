package com.pvpclient.module.bot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.function.Predicate;

/**
 * Сканер мира: данжи (spawner / chest / mossy_cobblestone, шахты по рельсам) и видимые открытые руды.
 * Данжи ищутся только в загруженных клиентом чанках (зависит от view distance).
 * Чанки сканируются порциями (по 3 за тик) и кэшируются.
 */
public final class WorldScanner {
    public record Target(BlockPos pos, String kind, boolean chest) {}
    private record ChunkInfo(long at, List<BlockPos> spawners, List<BlockPos> chests, List<BlockPos> mossy, List<BlockPos> rails) {}

    private static final Set<Block> ORES = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Map<Long, ChunkInfo> chunks = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> done = new HashSet<>();
    private final Map<Long, Integer> fails = new HashMap<>();
    private final Set<Long> badOres = new HashSet<>();
    private List<Target> targets = List.of();
    private BlockPos ore;
    private long tick;

    public List<Target> targets() { return targets; }
    public Target nearest() { return targets.isEmpty() ? null : targets.get(0); }
    public BlockPos ore() { return ore; }
    public void clearOre() { ore = null; }
    public void markDone(BlockPos p) { done.add(p.asLong()); }
    public void fail(BlockPos p) { if (fails.merge(p.asLong(), 1, Integer::sum) >= 3) done.add(p.asLong()); }
    public void badOre(BlockPos p) { badOres.add(p.asLong()); if (p.equals(ore)) ore = null; }

    public void reset() { chunks.clear(); queue.clear(); done.clear(); fails.clear(); badOres.clear(); targets = List.of(); ore = null; }

    private static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    public void tick(int radius, int oreRadius) {
        if (mc.world == null || mc.player == null) return;
        tick++;
        if (tick % 40 == 1) refill(radius);
        for (int i = 0; i < 3 && !queue.isEmpty(); i++) scan(queue.poll());
        if (tick % 20 == 0) rebuild(radius);
        // Цель руды «липкая»: не переключаемся на более близкую руду, пока не добыли текущую —
        // иначе в жилах руды (несколько блоков рядом) бот дёргается между ними и никогда не долбивает до конца.
        if (ore != null && !ORES.contains(mc.world.getBlockState(ore).getBlock())) ore = null;
        if (tick % 10 == 0 && ore == null) ore = findOre(oreRadius);
    }

    // ---------------- чанки ----------------
    private void refill(int radius) {
        queue.clear();
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        int r = radius / 16 + 1;
        List<long[]> stale = new ArrayList<>();
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                ChunkInfo ci = chunks.get(key(cx, cz));
                if (ci == null || tick - ci.at() > 600) {
                    stale.add(new long[]{key(cx, cz), (long) (cx - pcx) * (cx - pcx) + (long) (cz - pcz) * (cz - pcz)});
                }
            }
        }
        stale.sort(Comparator.comparingLong(a -> a[1]));
        for (long[] s : stale) queue.add(s[0]);
    }

    private void scan(long k) {
        int cx = (int) (k >> 32), cz = (int) k;
        WorldChunk ch = mc.world.getChunkManager().getWorldChunk(cx, cz);
        if (ch == null) return;
        List<BlockPos> sp = new ArrayList<>(), chests = new ArrayList<>(), mossy = new ArrayList<>(), rails = new ArrayList<>();
        for (BlockEntity be : ch.getBlockEntities().values()) {
            if (be instanceof MobSpawnerBlockEntity) sp.add(be.getPos());
            else if (be instanceof ChestBlockEntity) chests.add(be.getPos());
        }
        ChunkSection[] sections = ch.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            boolean hasMossy = sec.hasAny(s -> s.isOf(Blocks.MOSSY_COBBLESTONE));
            boolean hasRail = sec.hasAny(s -> s.isOf(Blocks.RAIL));
            if (!hasMossy && !hasRail) continue;
            int baseY = ch.sectionIndexToCoord(i) << 4;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                BlockState s = sec.getBlockState(x, y, z);
                if (hasMossy && s.isOf(Blocks.MOSSY_COBBLESTONE) && mossy.size() < 600) mossy.add(new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z));
                else if (hasRail && s.isOf(Blocks.RAIL) && rails.size() < 300) rails.add(new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z));
            }
        }
        chunks.put(k, new ChunkInfo(tick, sp, chests, mossy, rails));
    }

    private void rebuild(int radius) {
        Vec3d me = mc.player.getPos();
        List<BlockPos> sp = new ArrayList<>(), ch = new ArrayList<>(), ms = new ArrayList<>(), rl = new ArrayList<>();
        for (ChunkInfo ci : chunks.values()) { sp.addAll(ci.spawners()); ch.addAll(ci.chests()); ms.addAll(ci.mossy()); rl.addAll(ci.rails()); }

        List<Target> out = new ArrayList<>();
        for (BlockPos c : ch) {
            if (done.contains(c.asLong()) || Vec3d.ofCenter(c).distanceTo(me) > radius) continue;
            boolean spawner = near(c, sp, 10) > 0;
            int moss = near(c, ms, 8), rail = near(c, rl, 8);
            if (spawner) out.add(new Target(c, "spawner-dungeon", true));
            else if (moss >= 3) out.add(new Target(c, "mossy-dungeon", true));
            else if (rail >= 3) out.add(new Target(c, "mineshaft", true));
        }
        for (BlockPos s : sp) { // спавнер без сундука рядом — просто ориентир для подхода
            if (done.contains(s.asLong()) || Vec3d.ofCenter(s).distanceTo(me) > radius) continue;
            if (near(s, ch, 10) == 0) out.add(new Target(s, "spawner", false));
        }
        out.sort(Comparator.comparingDouble(t -> Vec3d.ofCenter(t.pos()).distanceTo(me)));
        targets = out;
    }

    private static int near(BlockPos p, List<BlockPos> list, double r) {
        int n = 0;
        double r2 = r * r;
        for (BlockPos q : list) if (p.getSquaredDistance(q) <= r2) n++;
        return n;
    }

    // ---------------- руды и видимость ----------------
    public boolean visible(BlockPos p) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult r = mc.world.raycast(new RaycastContext(eye, Vec3d.ofCenter(p),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(p);
    }

    private boolean lavaNear(BlockPos p) {
        for (Direction d : Direction.values()) {
            if (mc.world.getFluidState(p.offset(d)).isIn(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /** Ближайшая ВИДИМАЯ (есть прямая видимость) руда в радиусе, без лавы рядом. */
    private BlockPos findOre(int r) {
        BlockPos c = mc.player.getBlockPos();
        List<BlockPos> cand = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
            if (!ORES.contains(mc.world.getBlockState(m).getBlock())) continue;
            if (badOres.contains(m.asLong())) continue;
            cand.add(m.toImmutable());
        }
        cand.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(c)));
        int checked = 0;
        for (BlockPos p : cand) {
            if (checked++ > 12) break;
            if (!lavaNear(p) && visible(p)) return p;
        }
        return null;
    }

    /** Ближайший блок по условию (для сбора материалов на крафт); блок должен касаться свободной клетки. */
    public BlockPos findNearest(Predicate<BlockState> pred, int r) {
        BlockPos c = mc.player.getBlockPos();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
            double d = m.getSquaredDistance(c);
            if (d >= bd || !pred.test(mc.world.getBlockState(m))) continue;
            if (badOres.contains(m.asLong()) || lavaNear(m)) continue;
            boolean open = false;
            for (Direction dir : Direction.values()) if (BotUtil.passable(mc.world, m.offset(dir))) { open = true; break; }
            if (open) { bd = d; best = m.toImmutable(); }
        }
        return best;
    }
}

package com.pvpclient.module.bot;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Простой A* по сетке блоков (позиция = клетка «ног» игрока).
 * Ходы: шаг, подъём на 1 блок (прыжок), спуск до 3 блоков, мост (постановка блока).
 * Может прокапываться сквозь блоки (с повышенной стоимостью).
 * Если цель недостижима за лимит узлов — возвращает лучший частичный путь.
 */
public final class PathFinder {
    private PathFinder() {}

    public enum Kind { WALK, ASCEND, DESCEND, BRIDGE, PILLAR }

    public static final class Node {
        public final BlockPos pos;
        Node parent;
        double g, f;
        public Kind kind = Kind.WALK;
        public List<BlockPos> breaks = List.of(); // блоки, которые надо сломать перед этим шагом
        public BlockPos place;                    // блок, который надо поставить перед этим шагом

        Node(BlockPos pos) { this.pos = pos; }

        public boolean isJump() { return kind == Kind.ASCEND; }
    }

    public interface Goal {
        boolean reached(BlockPos p);
        double h(BlockPos p);
    }

    // ---------- Цели ----------
    /** Подойти на расстояние r к точке. */
    public static Goal near(BlockPos t, double r) {
        return new Goal() {
            public boolean reached(BlockPos p) { return p.getSquaredDistance(t) <= r * r; }
            public double h(BlockPos p) {
                return Math.max(0, Math.abs(p.getX() - t.getX()) + Math.abs(p.getZ() - t.getZ())
                        + Math.abs(p.getY() - t.getY()) * 1.5 - r);
            }
        };
    }

    /** Дойти до колонны (x, z). */
    public static Goal xz(int x, int z) {
        return new Goal() {
            public boolean reached(BlockPos p) { return Math.abs(p.getX() - x) + Math.abs(p.getZ() - z) <= 2; }
            public double h(BlockPos p) { return Math.abs(p.getX() - x) + Math.abs(p.getZ() - z); }
        };
    }

    /** Спуститься на высоту y или ниже. */
    public static Goal belowY(int y) {
        return new Goal() {
            public boolean reached(BlockPos p) { return p.getY() <= y; }
            public double h(BlockPos p) { return Math.max(0, p.getY() - y) * 3.0; }
        };
    }

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    /** Ищем путь. Результат не включает стартовую клетку; пустой список — пути нет. */
    public static List<Node> find(World w, BlockPos start, Goal goal, boolean dig, boolean place, int maxNodes) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, Node> best = new HashMap<>();
        Node s = new Node(start);
        s.f = goal.h(start);
        open.add(s);
        best.put(start.asLong(), s);

        Node partial = s;
        double bestH = s.f;
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxNodes) {
            Node cur = open.poll();
            if (best.get(cur.pos.asLong()) != cur) continue; // устаревшая запись
            if (goal.reached(cur.pos)) return build(cur);
            expanded++;
            double h = goal.h(cur.pos);
            if (h < bestH) { bestH = h; partial = cur; }
            for (int i = 0; i < 4; i++) expand(w, cur, DX[i], DZ[i], dig, place, goal, open, best);
            expandPillar(w, cur, place, goal, open, best);
        }
        return partial == s ? List.of() : build(partial);
    }

    private static List<Node> build(Node end) {
        LinkedList<Node> out = new LinkedList<>();
        for (Node n = end; n != null && n.parent != null; n = n.parent) out.addFirst(n);
        return new ArrayList<>(out);
    }

    /** Стоимость освобождения клетки: 0 — свободна, >0 — надо ломать, -1 — невозможно. */
    private static double cell(World w, BlockPos c, boolean dig, List<BlockPos> breaks) {
        if (BotUtil.passable(w, c)) return 0;
        if (dig && BotUtil.safeToBreak(w, c)) { breaks.add(c); return BotUtil.breakCost(w, c); }
        return -1;
    }

    /**
     * «Пилларинг»: прыжок вверх на месте с установкой блока под ноги в полёте.
     * Нужен, когда подъём на 1 блок невозможен штатным шагом (нет готовой ступени —
     * впереди стена высотой 2+ или обрыв), но есть из чего строить. Без него бот застревает
     * перед любой стенкой выше шага и просто прыгает на месте.
     */
    private static void expandPillar(World w, Node cur, boolean place, Goal goal, PriorityQueue<Node> open, Map<Long, Node> best) {
        if (!place) return; // нечем пилларить
        BlockPos p = cur.pos;
        if (!BotUtil.canStand(w, p.down())) return; // стоим уверенно — опора под ногами уже есть
        BlockPos up = p.up();
        if (!BotUtil.passable(w, up.up()) || !BotUtil.passable(w, up.up(2))) return; // нет запаса по высоте сверху
        if (!BotUtil.placeable(w, p)) return; // клетка, где сейчас ноги, должна быть свободна под установку
        push(cur, up, Kind.PILLAR, 3.2, List.of(), p, goal, open, best);
    }

    private static void expand(World w, Node cur, int dx, int dz, boolean dig, boolean place,
                               Goal goal, PriorityQueue<Node> open, Map<Long, Node> best) {
        BlockPos p = cur.pos;
        BlockPos f = p.add(dx, 0, dz);

        // ---- ПОДЪЁМ на 1 блок: впереди ступень, над головой и над ступенью свободно ----
        if (BotUtil.canStand(w, f)) {
            List<BlockPos> br = new ArrayList<>();
            double a = cell(w, p.up(2), dig, br);
            double b = cell(w, f.up(), dig, br);
            double c = cell(w, f.up(2), dig, br);
            if (a >= 0 && b >= 0 && c >= 0) push(cur, f.up(), Kind.ASCEND, 2 + a + b + c, br, null, goal, open, best);
        }

        // ---- ШАГ вперёд / СПУСК до 3 блоков ----
        List<BlockPos> br = new ArrayList<>();
        double c1 = cell(w, f.up(), dig, br);
        double c2 = c1 < 0 ? -1 : cell(w, f, dig, br);
        if (c1 < 0 || c2 < 0) return;
        double cost = 1.0 + c1 + c2;

        boolean noSupportAtLevel = !BotUtil.canStand(w, f.down());
        int k = 0;
        while (true) {
            BlockPos feet = f.down(k);
            if (BotUtil.canStand(w, feet.down())) {
                push(cur, feet, k == 0 ? Kind.WALK : Kind.DESCEND, cost + 0.5 * k, new ArrayList<>(br), null, goal, open, best);
                break;
            }
            if (k == 3) break;
            k++;
            BlockPos next = f.down(k);
            if (!BotUtil.passable(w, next)) {
                if (k == 1 && dig) { // «лестница вниз» — копаем клетку под ступенькой
                    double c3 = cell(w, next, true, br);
                    if (c3 < 0) break;
                    cost += c3;
                } else break;
            }
        }

        // ---- МОСТ: опоры нет — ставим блок под следующую клетку ----
        if (place && noSupportAtLevel && br.isEmpty() && BotUtil.placeable(w, f.down()) && BotUtil.canStand(w, p.down())) {
            push(cur, f, Kind.BRIDGE, 1.0 + 5.0, List.of(), f.down(), goal, open, best);
        }
    }

    private static void push(Node cur, BlockPos pos, Kind kind, double cost, List<BlockPos> br, BlockPos place,
                             Goal goal, PriorityQueue<Node> open, Map<Long, Node> best) {
        double g = cur.g + cost;
        Node old = best.get(pos.asLong());
        if (old != null && old.g <= g) return;
        Node n = new Node(pos);
        n.parent = cur;
        n.g = g;
        n.f = g + goal.h(pos);
        n.kind = kind;
        n.breaks = br;
        n.place = place;
        best.put(pos.asLong(), n);
        open.add(n);
    }
}

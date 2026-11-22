package com.pvpclient.module.bot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Set;

/** Проверки блоков для поиска пути: можно ли пройти, встать, сломать, поставить. */
final class BotUtil {
    private BotUtil() {}

    static final Set<Block> DANGER = Set.of(
            Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.MAGMA_BLOCK, Blocks.CACTUS, Blocks.SWEET_BERRY_BUSH,
            Blocks.COBWEB, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.WITHER_ROSE, Blocks.POWDER_SNOW,
            Blocks.TNT, Blocks.NETHER_PORTAL, Blocks.END_PORTAL);

    /** Клетка свободна для тела игрока (нет коллизии, нет жидкости и опасных блоков). */
    static boolean passable(World w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        if (!s.getFluidState().isEmpty()) return false;
        if (DANGER.contains(s.getBlock())) return false;
        return s.getCollisionShape(w, p).isEmpty();
    }

    /** На этот блок можно встать (полная твёрдая верхняя грань). */
    static boolean canStand(World w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        if (!s.getFluidState().isEmpty() || DANGER.contains(s.getBlock())) return false;
        return s.isSideSolidFullSquare(w, p, Direction.UP);
    }

    /**
     * Безопасно ли ломать блок: не воздух, не неразрушимый, не блок с инвентарём/спавнер,
     * рядом нет жидкости (иначе затопит/зальёт лавой), сверху не падающий блок.
     */
    static boolean safeToBreak(World w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || DANGER.contains(s.getBlock())) return false;
        if (s.getHardness(w, p) < 0) return false;
        for (Direction d : Direction.values()) {
            if (!w.getBlockState(p.offset(d)).getFluidState().isEmpty()) return false;
        }
        return !(w.getBlockState(p.up()).getBlock() instanceof FallingBlock);
    }

    static double breakCost(World w, BlockPos p) {
        return 3.0 + Math.min(w.getBlockState(p).getHardness(w, p), 25.0f);
    }

    /** Сюда можно поставить блок (воздух/заменяемый, не жидкость). */
    static boolean placeable(World w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        return s.isReplaceable() && s.getFluidState().isEmpty();
    }
}

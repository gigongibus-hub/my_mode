package com.gigongibus.hungrycows.fell;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Дерево, що стоїть над зарубкою (Fij, 19.11: «поки дуб»; 20.11: «додай ще сосну» — у грі сосни немає, тож ялина, і велика 2×2): колоди (стовбур і гілки великого дуба), з'єднані між собою й зі
 * стовбуром, і ПРИРОДНЕ листя довкола них (поставлене гравцем не рахується). Без листя — не дерево (стовп будинку не падає).
 */
public final class TreeShape {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MAX_LOGS = 256;          // більше — не дерево (будівля з колод)
    public static final int MAX_LEAVES = 2500;
    public static final int RADIUS = 10;             // убік від стовбура не далі
    public static final int HEIGHT = 40;             // угору не вище
    public static final int LEAF_STEPS = 5;          // листя — не далі 5 кроків від своїх колод (сусіднє дерево не чіпаємо)
    public static final int MIN_LEAVES = 6;          // менше — не дерево

    public final List<BlockPos> logs = new ArrayList<>();
    /** Вид дерева: «oak» чи «spruce» */
    public String wood = "oak";
    /** Колоди на рівні зарубки, що ще тримають дерево (сусідні стовбури ялини 2×2, не підрубані) */
    public final List<BlockPos> holders = new ArrayList<>();
    /** Зарубки під деревом (на рівні base): у великої ялини — до 4 */
    public final List<BlockPos> cuts = new ArrayList<>();
    public final List<BlockPos> leaves = new ArrayList<>();      // і ліани на ньому

    public static boolean isOakLog(BlockState s) {
        return s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_WOOD);
    }

    /** Вид дерева колоди-стовбура: «oak», «spruce»; null — такі не валимо */
    public static String woodOf(BlockState s) {
        if (s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_WOOD)) return "oak";
        if (s.is(Blocks.SPRUCE_LOG) || s.is(Blocks.SPRUCE_WOOD)) return "spruce";
        return null;
    }

    public static boolean isLog(BlockState s, String wood) {
        return wood.equals(woodOf(s));
    }

    public static boolean isLeaf(BlockState s, String wood) {
        Block leaves = "spruce".equals(wood) ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES;
        return s.is(leaves) && !s.getValue(LeavesBlock.PERSISTENT);
    }

    /** Дерево над колодою base (сама base — зарубка, у дерево не входить); null — це не дерево */
    public static TreeShape scan(BlockGetter level, BlockPos base) {
        TreeShape t = new TreeShape();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        BlockPos first = base.above();
        String wood = woodOf(level.getBlockState(first));
        if (wood == null) return null;
        t.wood = wood;
        q.add(first);
        seen.add(first);
        seen.add(base);
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            t.logs.add(p);
            if (t.logs.size() > MAX_LOGS) return null;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (n.getY() <= base.getY() || n.getY() > base.getY() + HEIGHT) continue;
                        if (Math.abs(n.getX() - base.getX()) > RADIUS || Math.abs(n.getZ() - base.getZ()) > RADIUS) continue;
                        if (seen.contains(n) || !isLog(level.getBlockState(n), wood)) continue;
                        seen.add(n);
                        q.add(n);
                    }
        }
        // листя: від колод уздовж граней, не далі LEAF_STEPS кроків
        ArrayDeque<long[]> lq = new ArrayDeque<>();
        for (BlockPos l : t.logs) lq.add(new long[]{ l.asLong(), 0 });
        while (!lq.isEmpty()) {
            long[] e = lq.poll();
            BlockPos p = BlockPos.of(e[0]);
            if (e[1] >= LEAF_STEPS) continue;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || n.getY() <= base.getY()) continue;
                BlockState s = level.getBlockState(n);
                boolean leaf = isLeaf(s, wood);
                boolean vine = s.is(Blocks.VINE) || s.is(Blocks.COCOA);
                if (!leaf && !vine) continue;
                seen.add(n);
                t.leaves.add(n);
                if (t.leaves.size() > MAX_LEAVES) return null;
                if (leaf) lq.add(new long[]{ n.asLong(), e[1] + 1 });
            }
        }
        if (t.leaves.size() < MIN_LEAVES) return null;
        // що під деревом на рівні зарубки: інші зарубки (велика ялина) або ще цілі стовбури
        for (BlockPos l : t.logs) {
            if (l.getY() != base.getY() + 1) continue;
            BlockPos under = l.below();
            if (under.equals(base)) { if (!t.cuts.contains(base)) t.cuts.add(base); continue; }
            if (level.getBlockEntity(under) instanceof FellingCutBlockEntity be && !be.felled()) { if (!t.cuts.contains(under)) t.cuts.add(under); }
            else if (isLog(level.getBlockState(under), wood)) t.holders.add(under);
        }
        if (!t.cuts.contains(base)) t.cuts.add(base);
        return t;
    }
}

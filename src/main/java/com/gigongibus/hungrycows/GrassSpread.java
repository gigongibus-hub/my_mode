package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Трава потихеньку розростається (Fij, 14.11). Де трави немає — сама не з'являється; де вона є (виросла, поставили), там
 * повільно переходить на сусідні трав'яні блоки. Паркан (і хвіртка) не заважає — трава «пролазить» під ним на блок далі;
 * стіна (будь-який інший блок) — заважає. Стежки, витоптані коровами, яких 3 доби не топтали, так само заростають
 * трав'яним блоком від сусіднього (варіант А). Як випадкові тіки гри: щосекунди кілька випадкових місць біля гравців.
 */
public final class GrassSpread {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int SAMPLES_PER_PLAYER = 16;     // щосекунди 16 випадкових місць біля кожного гравця ...
    public static final int RADIUS = 48;                 // ... до 48 бл. (97 × 97 місць — кожне приблизно раз на 10 хв = пів доби)
    public static final float SPREAD_CHANCE = 1.0F;      // Fij: трава розростається в середньому раз на пів доби
    public static final int SAMPLES_MAX = 4096;          // з прискоренням — не більше стількох місць за секунду
    public static final float PATH_CHANCE = 0.30F;       // стежка біля трави заростає з шансом 30%
    public static final int EVERY_TICKS = 20;

    private GrassSpread() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % EVERY_TICKS != 0 || !WorldClock.grassGrowth(level)) return;   // ріст трави вимкнено
        RandomSource r = level.getRandom();
        int samples = Math.min(SAMPLES_MAX, SAMPLES_PER_PLAYER * WorldClock.speed(level));   // прискорення
        for (ServerPlayer p : level.players()) {
            for (int i = 0; i < samples; i++) {
                int x = p.getBlockX() + r.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = p.getBlockZ() + r.nextInt(RADIUS * 2 + 1) - RADIUS;
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;   // найвищий не-повітряний блок
                BlockPos top = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(top);
                if (s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS)) {
                    if (s.is(Blocks.TALL_GRASS) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) top = top.below();
                    if (r.nextFloat() < SPREAD_CHANCE) spread(level, top, r);
                } else if (s.is(Blocks.DIRT_PATH) && r.nextFloat() < PATH_CHANCE) {
                    regrowPath(level, top);
                }
            }
        }
    }

    // Куди трава може перейти в напрямку d: сусідня клітинка, а якщо там паркан — на клітинку за ним
    private static BlockPos reach(ServerLevel level, BlockPos from, Direction d) {
        BlockPos next = from.relative(d);
        BlockState n = level.getBlockState(next);
        if (n.is(BlockTags.FENCES) || n.is(BlockTags.FENCE_GATES)) return next.relative(d);   // під парканом пролазить
        return next;
    }

    /** Трава з plant переходить на випадковий сусідній трав'яний блок (той самий рівень) */
    public static boolean spread(ServerLevel level, BlockPos plant, RandomSource r) {
        return spread(level, plant, Direction.Plane.HORIZONTAL.getRandomDirection(r));
    }

    public static boolean spread(ServerLevel level, BlockPos plant, Direction d) {
        BlockPos to = reach(level, plant, d);
        if (!level.getBlockState(to).isAir() || !level.getBlockState(to.below()).is(Blocks.GRASS_BLOCK)) return false;   // стіна, зайнято
        level.setBlockAndUpdate(to, Blocks.GRASS.defaultBlockState());
        return true;
    }

    /** Стежка корів, яку давно не топтали, заростає, якщо поруч (або за парканом) трав'яний блок */
    public static boolean regrowPath(ServerLevel level, BlockPos path) {
        if (!Trails.canRegrow(level, path) || !level.getBlockState(path.above()).isAir()) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = path.relative(d);
            BlockState ns = level.getBlockState(n);
            boolean grassNear = ns.is(Blocks.GRASS_BLOCK)
                    || (level.getBlockState(n.above()).is(BlockTags.FENCES) || level.getBlockState(n.above()).is(BlockTags.FENCE_GATES))
                       && level.getBlockState(n.relative(d)).is(Blocks.GRASS_BLOCK);   // за парканом (паркан стоїть на землі)
            if (grassNear) {
                level.setBlockAndUpdate(path, Blocks.GRASS_BLOCK.defaultBlockState());
                Trails.regrown(level, path);
                return true;
            }
        }
        return false;
    }
}

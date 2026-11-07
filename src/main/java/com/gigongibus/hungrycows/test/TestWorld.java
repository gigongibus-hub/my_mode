package com.gigongibus.hungrycows.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * Спільне для тестів: тиха погода й без лави довкола майданчика.
 * Лава: у плаcкому тестовому світі є лавові озера; майданчики стоять поруч і лава часом розливається на них (корови гинули
 * «у лаві»). Перед тестом лава й вогонь довкола майданчика (LAVA_MARGIN блоків) стають каменем (нижче підлоги) чи повітрям. Тестовий світ щоразу новий (build.gradle), а в новому світі починаються дощ і гроза —
 * блискавка підпалює траву (корови панікують від вогню), дощ змінює поведінку. Тести перевіряють корів, а не погоду.
 */
final class TestWorld {
    private static final int CLEAR_TICKS = 1_000_000;   // ясно надовго
    private static final int LAVA_MARGIN = 8;            // прибрати лаву на 8 блоків довкола майданчика
    private static final int LAVA_BELOW = 3, LAVA_ABOVE = 10;
    private static final int PLOT_SIZE = 81;             // найбільший майданчик (bigbarn); менші — те саме, із запасом

    private TestWorld() {
    }

    static void calm(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setWeatherParameters(CLEAR_TICKS, 0, false, false);
        BlockPos a = h.absolutePos(new BlockPos(0, 1, 0)), c = h.absolutePos(new BlockPos(PLOT_SIZE, 1, PLOT_SIZE));
        int floor = a.getY();   // y=1 майданчика — підлога
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = Math.min(a.getX(), c.getX()) - LAVA_MARGIN; x <= Math.max(a.getX(), c.getX()) + LAVA_MARGIN; x++) {
            for (int z = Math.min(a.getZ(), c.getZ()) - LAVA_MARGIN; z <= Math.max(a.getZ(), c.getZ()) + LAVA_MARGIN; z++) {
                for (int y = floor - LAVA_BELOW; y <= floor + LAVA_ABOVE; y++) {
                    p.set(x, y, z);
                    BlockState st = level.getBlockState(p);
                    if (st.is(Blocks.LAVA) || st.is(Blocks.FIRE) || st.is(Blocks.MAGMA_BLOCK)) {
                        level.setBlock(p, y <= floor ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}

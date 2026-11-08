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
    private static final int LAVA_MARGIN = 20;           // прибрати лаву на 20 блоків довкола майданчика
    private static final int LAVA_BELOW = 3, LAVA_ABOVE = 30;   // корови тонули у воді на 23 блоки вище підлоги
    private static final int PLOT_SIZE = 81;
    private static final int BIOME_MARGIN = 16;          // рівнини ще на 16 бл. довкола майданчика
    private static final int TICK_MARGIN = 32;           // ділянки світу (чанки) на 32 бл. довкола майданчика роблять активними             // найбільший майданчик (bigbarn); менші — те саме, із запасом

    private TestWorld() {
    }

    static void calm(GameTestHelper h) {
        calm(h, true);
    }

    // clearWater = false — для тестів, що самі будують ставок до виклику (плавання, спека)
    static void calm(GameTestHelper h, boolean clearWater) {
        ServerLevel level = h.getLevel();
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());   // вогонь від лави не поширюється
        level.setWeatherParameters(CLEAR_TICKS, 0, false, false);
        BlockPos a = h.absolutePos(new BlockPos(0, 1, 0)), c = h.absolutePos(new BlockPos(PLOT_SIZE, 1, PLOT_SIZE));
        int floor = a.getY();   // y=1 майданчика — підлога
        // Без гравця тестовий сервер обробляє тварин лише в чанках самого майданчика; корова, що перейшла межу чанка
        // (напр. z=64), «застигала» назавжди (лічильник тіків стояв). Примусово активні чанки з запасом довкола
        for (int cx = (Math.min(a.getX(), c.getX()) - TICK_MARGIN) >> 4; cx <= (Math.max(a.getX(), c.getX()) + TICK_MARGIN) >> 4; cx++) {
            for (int cz = (Math.min(a.getZ(), c.getZ()) - TICK_MARGIN) >> 4; cz <= (Math.max(a.getZ(), c.getZ()) + TICK_MARGIN) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        // Біом: тестовий світ має випадкові біоми, і майданчик буває в жаркому (там корови вдень сплять — HungryCow.inHotBiome).
        // Тести перевіряють звичайний розклад — майданчик і запас довкола стають рівнинами (тест жаркого біому міняє сам)
        var server = level.getServer();
        var source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        for (int x = Math.min(a.getX(), c.getX()) - BIOME_MARGIN; x <= Math.max(a.getX(), c.getX()) + BIOME_MARGIN; x += 16) {
            for (int z = Math.min(a.getZ(), c.getZ()) - BIOME_MARGIN; z <= Math.max(a.getZ(), c.getZ()) + BIOME_MARGIN; z += 16) {
                server.getCommands().performPrefixedCommand(source, "fillbiome " + x + " " + (floor - LAVA_BELOW) + " " + z + " "
                        + (x + 15) + " " + (floor + LAVA_ABOVE) + " " + (z + 15) + " minecraft:plains");
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = Math.min(a.getX(), c.getX()) - LAVA_MARGIN; x <= Math.max(a.getX(), c.getX()) + LAVA_MARGIN; x++) {
            for (int z = Math.min(a.getZ(), c.getZ()) - LAVA_MARGIN; z <= Math.max(a.getZ(), c.getZ()) + LAVA_MARGIN; z++) {
                for (int y = floor - LAVA_BELOW; y <= floor + LAVA_ABOVE; y++) {
                    p.set(x, y, z);
                    BlockState st = level.getBlockState(p);
                    // лава, вогонь, а над підлогою й вода (тести, яким вода потрібна, будують її вже після calm)
                    if (st.is(Blocks.LAVA) || st.is(Blocks.FIRE) || st.is(Blocks.MAGMA_BLOCK) || (clearWater && y > floor && st.is(Blocks.WATER))) {
                        level.setBlock(p, y <= floor ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}

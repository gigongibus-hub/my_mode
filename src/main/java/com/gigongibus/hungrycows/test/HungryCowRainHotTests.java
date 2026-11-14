package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Дощ (ген «любов до дощу») і жаркий біом (удень спить). Ігрова логіка не змінюється: тест лише задає ген, погоду й біом.
 * Погода спільна для всього світу — тому окремі партії.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowRainHotTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String ARENA = "arena";
    private static final int RAIN_TIMEOUT = 1200;     // 60 с, щоб сховатись
    private static final int HOT_TIMEOUT = 200;       // 10 с
    private static final long NOON = 6000;

    // Навіс з каменю 3×3 у куті (стовпи по кутах, дах на висоті 3)
    private static void shelter(GameTestHelper h, boolean place) {
        for (int x = 10; x <= 12; x++) for (int z = 10; z <= 12; z++) {
            h.setBlock(new BlockPos(x, 5, z), place ? Blocks.STONE : Blocks.AIR);
        }
        for (int[] c : new int[][]{{9, 9}, {9, 13}, {13, 9}, {13, 13}}) {
            for (int y = 2; y <= 5; y++) h.setBlock(new BlockPos(c[0], y, c[1]), place ? Blocks.STONE : Blocks.AIR);
        }
        for (int x = 9; x <= 13; x++) for (int z = 9; z <= 13; z++) h.setBlock(new BlockPos(x, 5, z), place ? Blocks.STONE : Blocks.AIR);
    }

    // 1. Не любить дощу — ховається під навіс; любителька лишається на дощі
    @GameTest(template = ARENA, batch = "rain", timeoutTicks = RAIN_TIMEOUT)
    public static void rainHaterHidesLoverStays(GameTestHelper h) {
        h.getLevel().setDayTime(NOON);
        TestWorld.calm(h);
        shelter(h, true);
        HungryCow hater = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(3, 2, 3));
        HungryCow lover = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(3, 2, 6));
        for (HungryCow c : new HungryCow[]{hater, lover}) c.setAge(0);
        hater.genes.set(CowGenes.Gene.RAIN_LIKING, 10);
        lover.genes.set(CowGenes.Gene.RAIN_LIKING, 90);
        h.getLevel().setWeatherParameters(0, 100_000, true, false);   // дощ
        int[] loverDry = { 0 };
        boolean[] haterWet = { false };
        h.onEachTick(() -> {
            hater.hunger = 0;   // ситі: голодна піде шукати їжі й під дощем (голод важливіший), а тут перевіряємо лише дощ
            lover.hunger = 0;
            if (!lover.rainedOn()) loverDry[0]++;
            if (hater.rainedOn()) haterWet[0] = true;
            if (h.getTick() % 100 == 0) LOG.info("[тест] дощ: тік {}, дощить {}, небо {}, на неї {}, на любительку {}", h.getTick(),
                    h.getLevel().isRaining(), h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, hater.getBlockX(), hater.getBlockZ()) + "/" + hater.getBlockY()
                    + " " + h.getLevel().getBiome(hater.blockPosition()).value().getPrecipitationAt(hater.blockPosition()) + " " + h.getLevel().getBiome(hater.blockPosition()).unwrapKey(), hater.rainedOn(), lover.rainedOn());
        });
        Runnable clear = () -> {
            hater.discard();
            lover.discard();
            shelter(h, false);
            TestWorld.calm(h);   // знову ясно
        };
        h.runAfterDelay(RAIN_TIMEOUT - 1, () -> {
            LOG.info("[тест] rainHaterHidesLoverStays: час вийшов; та, що не любить, ({} {} {}) під дощем {}",
                    String.format("%.1f", hater.getX() - h.absoluteVec(net.minecraft.world.phys.Vec3.ZERO).x), String.format("%.1f", hater.getY()),
                    String.format("%.1f", hater.getZ() - h.absoluteVec(net.minecraft.world.phys.Vec3.ZERO).z), hater.rainedOn());
            clear.run();
        });
        h.succeedWhen(() -> {
            h.assertTrue(haterWet[0], "дощ ще не падав на корову");
            h.assertTrue(hater.isAlive() && !hater.rainedOn(), "та, що не любить дощу, ще під дощем");
            h.assertTrue(loverDry[0] < h.getTick() / 2, "любителька дощу теж ховалась (суха " + loverDry[0] + " тіків з " + h.getTick() + ")");
            LOG.info("[тест] rainHaterHidesLoverStays: сховалась за {} с; любителька не під дощем {} тіків з {}",
                    String.format("%.1f", h.getTick() / 20.0), loverDry[0], h.getTick());
            clear.run();
        });
    }

    // 2. Жаркий біом (пустеля): опівдні для корови «ніч» — спатиме; повернули рівнини — знову день
    @GameTest(template = ARENA, batch = "hot", timeoutTicks = HOT_TIMEOUT)
    public static void hotBiomeSwapsDayAndNight(GameTestHelper h) {
        h.getLevel().setDayTime(NOON);
        TestWorld.calm(h);
        BlockPos a = h.absolutePos(new BlockPos(0, 0, 0)), b = h.absolutePos(new BlockPos(14, 6, 14));
        String box = Math.min(a.getX(), b.getX()) + " " + a.getY() + " " + Math.min(a.getZ(), b.getZ()) + " "
                + Math.max(a.getX(), b.getX()) + " " + b.getY() + " " + Math.max(a.getZ(), b.getZ());
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "fillbiome " + box + " minecraft:desert");
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 7));
        cow.setAge(0);
        boolean[] swapped = { false };
        Runnable clear = () -> {
            cow.discard();
            server.getCommands().performPrefixedCommand(source, "fillbiome " + box + " minecraft:plains");
        };
        h.runAfterDelay(HOT_TIMEOUT - 1, clear);
        h.succeedWhen(() -> {
            if (!swapped[0]) {
                h.assertTrue(cow.inHotBiome() && cow.isNightTime(), "у пустелі опівдні корова не вважає, що «ніч»");
                swapped[0] = true;
                server.getCommands().performPrefixedCommand(source, "fillbiome " + box + " minecraft:plains");
                h.fail("ще перевіряю рівнини");
            }
            h.assertTrue(!cow.inHotBiome() && !cow.isNightTime(), "на рівнинах опівдні корова все ще вважає, що «ніч»");
            LOG.info("[тест] hotBiomeSwapsDayAndNight: пустеля → «ніч», рівнини → день (тік {})", h.getTick());
            clear.run();
        });
    }
}

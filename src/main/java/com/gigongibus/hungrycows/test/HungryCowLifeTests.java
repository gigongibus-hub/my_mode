package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Заміри «живості» (Fij: поведінка топорна, лялькова): скільки разів за хвилину корова міняє дію (мозок і ціль руху),
 * скільки разів різко розвертається (понад 90° за пів секунди), скільки мікро-дій. Числа — для порівняння до/після;
 * остаточно оцінює Fij у грі.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowLifeTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int WARMUP = 100;        // перші 5 с не рахуємо (корова щойно з'явилась)
    private static final int MEASURE = 1200;      // міряємо 60 с
    private static final int TURN_WINDOW = 10;    // «різкий поворот» — понад 90° за пів секунди
    private static final float SHARP_TURN = 90F;

    private static String moveGoal(HungryCow c) {
        return c.goalSelector.getRunningGoals().filter(w -> w.getFlags().contains(Goal.Flag.MOVE))
                .map(w -> w.getGoal().getClass().getSimpleName()).findFirst().orElse("-");
    }

    @GameTest(template = "arena", batch = "life", timeoutTicks = WARMUP + MEASURE + 40)
    public static void lifeMetrics(GameTestHelper h) {
        h.getLevel().setDayTime(3000);
        TestWorld.calm(h);
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z <= 13; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                if ((x + z) % 3 == 0) h.setBlock(new BlockPos(x, 2, z), Blocks.GRASS);
            }
        }
        List<HungryCow> cows = List.of(TestWorld.cow(h, new BlockPos(3, 2, 3)), TestWorld.cow(h, new BlockPos(11, 2, 4)),
                TestWorld.cow(h, new BlockPos(7, 2, 11)));
        for (HungryCow c : cows) c.setAge(0);
        int n = cows.size();
        int[] brainSw = new int[n], goalSw = new int[n], sharp = new int[n], micro0 = new int[n];
        String[] lastBrain = new String[n], lastGoal = new String[n];
        StringBuilder[] seq = new StringBuilder[n];
        for (int i = 0; i < n; i++) seq[i] = new StringBuilder();
        float[] lastYaw = new float[n];
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t < WARMUP) return;
            for (int i = 0; i < n; i++) {
                HungryCow c = cows.get(i);
                if (!c.isAlive()) continue;
                String b = c.brain.current().name(), g = moveGoal(c);
                if (t == WARMUP) {
                    seq[i].append(b);
                    lastBrain[i] = b; lastGoal[i] = g; lastYaw[i] = c.yBodyRot; micro0[i] = c.microCount();
                    continue;
                }
                if (!b.equals(lastBrain[i])) { brainSw[i]++; seq[i].append(" → ").append(b).append(" (").append((t - WARMUP) / 20).append(" с)"); }
                if (!g.equals(lastGoal[i])) goalSw[i]++;
                lastBrain[i] = b; lastGoal[i] = g;
                if (i == 0 && (t - WARMUP) % 300 == 0) LOG.info("[тест] lifeMetrics: шанси {}", c.brain.debugLine());
                if (t % TURN_WINDOW == 0) {
                    if (Math.abs(Mth.wrapDegrees(c.yBodyRot - lastYaw[i])) > SHARP_TURN) sharp[i]++;
                    lastYaw[i] = c.yBodyRot;
                }
            }
        });
        h.runAtTickTime(WARMUP + MEASURE, () -> {
            double bs = 0, gs = 0, sh = 0, mi = 0;
            for (int i = 0; i < n; i++) {
                HungryCow c = cows.get(i);
                int micro = c.microCount() - micro0[i];
                LOG.info("[тест] lifeMetrics: {} — мозок змінив дію {} р/хв, ціль руху {} р/хв, різких поворотів {}, мікро-дій {}",
                        c.family.name, brainSw[i], goalSw[i], sharp[i], micro);
                LOG.info("[тест] lifeMetrics: {} — дії мозку: {}", c.family.name, seq[i]);
                bs += brainSw[i]; gs += goalSw[i]; sh += sharp[i]; mi += micro;
            }
            LOG.info("[тест] lifeMetrics: СЕРЕДНЄ за хвилину — мозок {}, ціль руху {}, різкі повороти {}, мікро-дії {}",
                    String.format("%.1f", bs / n), String.format("%.1f", gs / n), String.format("%.1f", sh / n), String.format("%.1f", mi / n));
            cows.forEach(HungryCow::discard);
            for (int x = 1; x <= 13; x++) {
                for (int z = 1; z <= 13; z++) {
                    h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    h.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                }
            }
            h.succeed();
        });
    }
}

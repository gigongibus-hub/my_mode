package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Слух (CowHearing). Ігрова логіка не змінюється: тест сам грає звуки (як їх грала б гра) і ставить стіни.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowHearingTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";

    private static HungryCow still(GameTestHelper h, BlockPos at) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), at);
        cow.setAge(0);
        return cow;
    }

    private static void play(GameTestHelper h, BlockPos at, net.minecraft.sounds.SoundEvent sound) {
        Vec3 p = h.absoluteVec(Vec3.atCenterOf(at));
        h.getLevel().playSound(null, p.x, p.y, p.z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // 1. Вибух за межами огорожі (не видно, хто) — корова лякається й відбігає
    @GameTest(template = ARENA, batch = "hearing", timeoutTicks = 200)
    public static void explosionStartles(GameTestHelper h) {
        HungryCow cow = still(h, new BlockPos(7, 2, 7));
        cow.fear = 0;
        int[] fear = { 0 };
        boolean[] ran = { false };
        h.runAtTickTime(20, () -> play(h, new BlockPos(7, 2, 30), SoundEvents.GENERIC_EXPLODE));
        h.onEachTick(() -> {
            fear[0] = Math.max(fear[0], cow.fear);
            if (cow.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof com.gigongibus.hungrycows.ai.StartleGoal)) ran[0] = true;
        });
        h.runAtTickTime(100, () -> {
            LOG.info("[тест] explosionStartles: страх {}, відбігала {}", fear[0], ran[0]);
            cow.discard();
            h.assertTrue(fear[0] >= 20, "вибух за 23 бл. не налякав (страх " + fear[0] + ")");
            h.assertTrue(ran[0], "не відбігала від вибуху");
            h.succeed();
        });
    }

    // 2. Звикання: ламання блока поруч 12 разів без лиха — лякає дедалі менше
    @GameTest(template = ARENA, batch = "hearing", timeoutTicks = 4000)
    public static void getsUsedToSafeSound(GameTestHelper h) {
        HungryCow cow = still(h, new BlockPos(7, 2, 7));
        int[] first = { -1 }, last = { -1 };
        for (int i = 0; i < 12; i++) {
            int n = i;
            int t = 20 + i * 300;   // раз на 15 с (звикання — коли за 10 с після звуку нічого не сталося)
            h.runAtTickTime(t, () -> {
                cow.fear = 0;
                play(h, new BlockPos(9, 2, 7), SoundEvents.STONE_BREAK);
            });
            h.runAtTickTime(t + 15, () -> {
                if (n == 0) first[0] = cow.fear;
                last[0] = cow.fear;
            });
        }
        h.runAtTickTime(20 + 12 * 300, () -> {
            double habit = cow.hearing.habituation("minecraft:block.stone.break");
            LOG.info("[тест] getsUsedToSafeSound: страх від першого звуку {}, від останнього {}, звикання {}", first[0], last[0], habit);
            cow.discard();
            h.assertTrue(first[0] > 0, "перший звук не налякав");
            h.assertTrue(last[0] < first[0], "не звикла: " + first[0] + " -> " + last[0]);
            h.succeed();
        });
    }

    // 3. Дзвін перед частуванням: після кількох разів корова йде на дзвін з 12 бл.
    @GameTest(template = ARENA, batch = "hearing", timeoutTicks = 1200)
    public static void bellBeforeFoodCallsCow(GameTestHelper h) {
        HungryCow cow = still(h, new BlockPos(2, 2, 2));
        for (int i = 0; i < 4; i++) {
            int t = 10 + i * 40;
            h.runAtTickTime(t, () -> play(h, new BlockPos(2, 2, 3), SoundEvents.BELL_BLOCK));
            h.runAtTickTime(t + 20, () -> cow.chem.onTreat());   // «частування» за 1 с після дзвону
        }
        BlockPos bell = new BlockPos(12, 2, 12);
        h.runAtTickTime(200, () -> {
            LOG.info("[тест] bellBeforeFoodCallsCow: приємність дзвону {}", cow.hearing.liking("minecraft:block.bell.use"));
            play(h, bell, SoundEvents.BELL_BLOCK);
        });
        boolean[] cleared = { false };
        h.runAfterDelay(1199, () -> { cleared[0] = true; cow.discard(); });
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов");
            h.assertTrue(h.getTick() > 200, "ще не дзвонили");
            h.assertTrue(cow.distanceToSqr(h.absoluteVec(Vec3.atCenterOf(bell))) < 4 * 4, "не прийшла на дзвін");
            LOG.info("[тест] bellBeforeFoodCallsCow: прийшла на дзвін за {} с", (h.getTick() - 200) / 20.0);
            cow.discard();
        });
    }

    // 4. Стіна глушить: удар за 10 бл. чути відкрито (дальність 16), за кам'яною стіною — ні (8)
    @GameTest(template = ARENA, batch = "hearing", timeoutTicks = 200)
    public static void wallMufflesSound(GameTestHelper h) {
        HungryCow cow = still(h, new BlockPos(7, 2, 2));
        cow.goalSelector.removeAllGoals(g -> true);
        int[] open = { 0 }, walled = { 0 };
        h.runAtTickTime(20, () -> { cow.fear = 0; play(h, new BlockPos(7, 2, 12), SoundEvents.PLAYER_ATTACK_STRONG); });
        h.runAtTickTime(40, () -> {
            open[0] = cow.fear;
            cow.fear = 0;
            for (int x = 1; x <= 13; x++) for (int y = 2; y <= 6; y++) h.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
        });
        h.runAtTickTime(60, () -> play(h, new BlockPos(7, 2, 12), SoundEvents.PLAYER_ATTACK_STRONG));
        h.runAtTickTime(80, () -> {
            walled[0] = cow.fear;
            LOG.info("[тест] wallMufflesSound: страх відкрито {}, за стіною {}", open[0], walled[0]);
            for (int x = 1; x <= 13; x++) for (int y = 2; y <= 6; y++) h.setBlock(new BlockPos(x, y, 7), Blocks.AIR);
            cow.discard();
            h.assertTrue(open[0] > 0, "удар за 10 бл. не почула");
            h.assertTrue(walled[0] == 0, "почула крізь стіну");
            h.succeed();
        });
    }
}

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

    // 1. Вибух (справжній, як TNT) за межами огорожі — корова лякається й відбігає
    // окрема партія: вибух чути на 64 бл. — він лякав корів у сусідніх тестах (дзвін)
    @GameTest(template = ARENA, batch = "hearing_boom", timeoutTicks = 200)
    public static void explosionStartles(GameTestHelper h) {
        HungryCow cow = still(h, new BlockPos(7, 2, 7));
        cow.fear = 0;
        int[] fear = { 0 };
        boolean[] ran = { false };
        // справжній вибух (як TNT), а не лише його звук: у грі звук вибуху чує тільки гравець, сервер його не грає
        h.runAtTickTime(20, () -> {
            Vec3 p = h.absoluteVec(Vec3.atCenterOf(new BlockPos(7, 2, 30)));
            h.getLevel().explode(null, p.x, p.y, p.z, 4.0F, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        });
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
    // окрема партія: удари й ламання в сусідніх тестах лякали корову (і вона не йшла на дзвін)
    @GameTest(template = ARENA, batch = "hearing_bell", timeoutTicks = 1200)
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
        h.runAfterDelay(1199, () -> { cleared[0] = true; });
        h.runAfterDelay(1199, cow::discard);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов; корова робить: " + cow.goalSelector.getRunningGoals()
                    .map(g -> g.getGoal().getClass().getSimpleName()).collect(java.util.stream.Collectors.joining(",")) + ", страх " + cow.fear);
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

    // 5. Гравець ламає блок поруч (справжнє ламання: звук гра грає лише гравцю — корова «чує» саму подію)
    @GameTest(template = ARENA, batch = "hearing", timeoutTicks = 200)
    public static void hearsPlayerBreakingBlock(GameTestHelper h) {
        HungryCow cow = still(h, new BlockPos(7, 2, 7));
        cow.goalSelector.removeAllGoals(g -> true);
        BlockPos stone = new BlockPos(9, 2, 7);
        h.setBlock(stone, Blocks.STONE);
        int[] fear = { 0 };
        h.runAtTickTime(20, () -> {
            cow.fear = 0;
            net.minecraftforge.common.util.FakePlayer p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
            p.gameMode.destroyBlock(h.absolutePos(stone));
        });
        h.onEachTick(() -> fear[0] = Math.max(fear[0], cow.fear));
        h.runAtTickTime(60, () -> {
            boolean broken = h.getLevel().getBlockState(h.absolutePos(stone)).isAir();
            LOG.info("[тест] hearsPlayerBreakingBlock: блок зламано {}, страх {}", broken, fear[0]);
            h.setBlock(stone, Blocks.AIR);
            cow.discard();
            h.assertTrue(broken, "тест не зламав блок");
            h.assertTrue(fear[0] > 0, "не почула, як гравець зламав камінь за 2 бл.");
            h.succeed();
        });
    }

    // 6. Теля далеко від матері кличе, мати чує й іде до нього
    @GameTest(template = ARENA, batch = "hearing", timeoutTicks = 600)
    public static void calfCallsMotherComes(GameTestHelper h) {
        HungryCow mother = still(h, new BlockPos(2, 2, 2));
        HungryCow calf = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(12, 2, 12));
        calf.setAge(-24000);
        calf.family.setupNewborn(mother, null);
        calf.goalSelector.removeAllGoals(g -> true);   // лише для тесту: теля стоїть на місці (як на повідку)
        boolean[] came = { false };
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; mother.discard(); calf.discard(); };
        h.onEachTick(() -> {
            if (mother.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof com.gigongibus.hungrycows.ai.ComeToSoundGoal)) came[0] = true;
        });
        h.runAfterDelay(599, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов (мати йшла на клич: " + came[0] + ")");
            h.assertTrue(came[0], "мати не пішла на клич");
            h.assertTrue(mother.distanceTo(calf) < 6, "мати ще не біля теляти");
            LOG.info("[тест] calfCallsMotherComes: мати біля теляти на {} с", h.getTick() / 20.0);
            clear.run();
        });
    }

    // 7. Fij: «мама частіше ходить за телям, ніж теля за нею» — теля (звичайне, з усіма цілями) за 12 бл. саме йде до матері;
    //    мати проходить менше за теля
    @GameTest(template = ARENA, batch = "hearing", timeoutTicks = 600)
    public static void calfGoesToMother(GameTestHelper h) {
        HungryCow mother = still(h, new BlockPos(2, 2, 2));
        HungryCow calf = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(11, 2, 11));
        calf.setAge(-24000);
        calf.family.setupNewborn(mother, null);
        Vec3 m0 = mother.position(), c0 = calf.position();
        double[] mWalk = { 0 }, cWalk = { 0 };
        Vec3[] mLast = { m0 }, cLast = { c0 };
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; mother.discard(); calf.discard(); };
        h.onEachTick(() -> {
            mWalk[0] += mother.position().distanceTo(mLast[0]);
            cWalk[0] += calf.position().distanceTo(cLast[0]);
            mLast[0] = mother.position();
            cLast[0] = calf.position();
        });
        h.runAfterDelay(599, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов: теля не прийшло до матері (відстань " + Math.round(mother.distanceTo(calf)) + ")");
            h.assertTrue(mother.distanceTo(calf) < 5, "ще не разом");
            LOG.info("[тест] calfGoesToMother: разом на {} с; теля пройшло {} бл., мати {} бл.", h.getTick() / 20.0,
                    String.format("%.1f", cWalk[0]), String.format("%.1f", mWalk[0]));
            h.assertTrue(cWalk[0] > mWalk[0], "мати пройшла більше за теля: мати " + Math.round(mWalk[0]) + ", теля " + Math.round(cWalk[0]));
            clear.run();
        });
    }
}

package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowSmell;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.gigongibus.hungrycows.Wind;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Нюх (CowSmell) і вітер (Wind). Ігрова логіка не змінюється: тест задає вітер (Wind.set) і ставить блоки й тварин.
 * Вітер спільний для світу — окрема партія.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowSmellTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";

    // 1. Дальність від вітру: за вітром 48, штиль 16, проти вітру 4
    @GameTest(template = "platform", batch = "smell", timeoutTicks = 20)
    public static void windRange(GameTestHelper h) {
        Wind.set(h.getLevel(), 0, false);   // дме на південь (+z)
        Vec3 src = new Vec3(0, 0, 0);
        double with = Wind.range(h.getLevel(), src, new Vec3(0, 0, 10));     // корова на півдні: вітер несе до неї
        double against = Wind.range(h.getLevel(), src, new Vec3(0, 0, -10)); // корова на півночі
        double side = Wind.range(h.getLevel(), src, new Vec3(10, 0, 0));
        Wind.set(h.getLevel(), 0, true);
        double calm = Wind.range(h.getLevel(), src, new Vec3(0, 0, 10));
        LOG.info("[тест] windRange: за вітром {}, проти {}, збоку {}, штиль {}", with, against, side, calm);
        h.assertTrue(Math.abs(with - 48) < 1e-6 && Math.abs(against - 4) < 1e-6 && Math.abs(side - 16) < 1e-6 && calm == 16, "дальність не та");
        h.succeed();
    }

    // 2. Запах свині проходить крізь дошки, але не крізь камінь (штиль, 6 блоків)
    @GameTest(template = ARENA, batch = "smell", timeoutTicks = 200)
    public static void smellThroughWoodNotStone(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        Wind.set(h.getLevel(), 0, true);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 3));
        cow.setAge(0);
        cow.goalSelector.removeAllGoals(g -> true);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(7, 2, 9));
        pig.setNoAi(true);
        boolean[] ok = new boolean[3];
        // коробка довкола свині: спершу з дощок, потім з каменю
        h.runAtTickTime(40, () -> {
            ok[0] = cow.smell.smells(pig);
            wall(h, Blocks.OAK_PLANKS);
        });
        h.runAtTickTime(80, () -> {
            ok[1] = cow.smell.smells(pig);
            wall(h, Blocks.STONE);
        });
        h.runAtTickTime(140, () -> {   // запах «свіжий» ще 2 с після останнього нюху — тому через 3 с після стіни
            ok[2] = !cow.smell.smells(pig);
            LOG.info("[тест] smellThroughWoodNotStone: відкрито {}, крізь дошки {}, крізь камінь не чує {}", ok[0], ok[1], ok[2]);
            wall(h, Blocks.AIR);
            cow.discard();
            pig.discard();
            h.assertTrue(ok[0], "не чує свиню у відкритому полі за 6 бл. (штиль 16)");
            h.assertTrue(ok[1], "не чує крізь дошки");
            h.assertTrue(ok[2], "чує крізь камінь");
            h.succeed();
        });
    }

    private static void wall(GameTestHelper h, net.minecraft.world.level.block.Block b) {
        for (int x = 5; x <= 9; x++) for (int z = 7; z <= 11; z++) for (int y = 2; y <= 4; y++) {
            boolean edge = x == 5 || x == 9 || z == 7 || z == 11 || y == 4;
            if (edge) h.setBlock(new BlockPos(x, y, z), b);
        }
    }

    // 3. Вчиться: свиня вдарила корову — тепер запах свиней страшний; корова чує свиню за дошками (не бачить) і насторожується
    @GameTest(template = ARENA, batch = "smell", timeoutTicks = 200)
    public static void learnsToFearSmell(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        Wind.set(h.getLevel(), 0, true);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 3));
        cow.setAge(0);
        cow.goalSelector.removeAllGoals(g -> true);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(7, 2, 9));
        pig.setNoAi(true);
        wall(h, Blocks.OAK_PLANKS);
        int[] fearBefore = { -1 };
        h.runAtTickTime(40, () -> {
            fearBefore[0] = cow.fear;
            cow.hurt(cow.damageSources().mobAttack(pig), 1.0F);   // «свиня вдарила»
            cow.fear = 0;
        });
        h.runAtTickTime(140, () -> {
            double v = cow.smell.value(CowSmell.keyOf(pig));
            LOG.info("[тест] learnsToFearSmell: страх до {}, значення запаху свині {}, страх після {}, бачить {}", fearBefore[0], v, cow.fear, cow.vision.canSee(pig));
            wall(h, Blocks.AIR);
            cow.discard();
            pig.discard();
            h.assertTrue(v < 0, "запах свині не став страшним: " + v);
            h.assertTrue(!cow.vision.canSee(pig), "бачить свиню крізь дошки");
            h.assertTrue(cow.fear > 0, "чує страшний запах, а страх 0");
            h.succeed();
        });
    }

    // 4. Їжа за дерев'яною стінкою: не бачить, чує сіно (вітер від сіна до корови) й іде до нього
    @GameTest(template = ARENA, batch = "smell", timeoutTicks = 1200)
    public static void cowFollowsFoodSmell(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        Wind.set(h.getLevel(), 180, false); // дме на північ (−z): від сіна (z 12) до корови (z 3)
        BlockPos hay = new BlockPos(12, 2, 12);
        h.setBlock(hay, Blocks.HAY_BLOCK);
        for (int i = 9; i <= 13; i++) for (int y = 2; y <= 4; y++) {
            h.setBlock(new BlockPos(i, y, 10), Blocks.OAK_PLANKS);
            h.setBlock(new BlockPos(10, y, i), Blocks.OAK_PLANKS);
        }
        h.setBlock(new BlockPos(10, 2, 13), Blocks.AIR);   // вузький прохід у куток
        h.setBlock(new BlockPos(10, 3, 13), Blocks.AIR);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(3, 2, 3));
        cow.setAge(0);
        int[] smelledAt = { -1 };
        boolean[] cleared = { false };
        Runnable clear = () -> {
            cleared[0] = true;
            cow.discard();
            for (int i = 9; i <= 13; i++) for (int y = 2; y <= 4; y++) {
                h.setBlock(new BlockPos(i, y, 10), Blocks.AIR);
                h.setBlock(new BlockPos(10, y, i), Blocks.AIR);
            }
            h.setBlock(hay, Blocks.AIR);
        };
        h.onEachTick(() -> {
            cow.hunger = Math.max(cow.hunger, 60);
            if (smelledAt[0] < 0 && cow.smelledFood != null) smelledAt[0] = (int) h.getTick();
        });
        h.runAfterDelay(1199, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов");
            h.assertTrue(smelledAt[0] >= 0, "не почула сіна");
            h.assertTrue(cow.distanceToSqr(Vec3.atCenterOf(h.absolutePos(hay))) < 3 * 3, "не дійшла до сіна");
            LOG.info("[тест] cowFollowsFoodSmell: почула на {} с, біля сіна на {} с", smelledAt[0] / 20.0, h.getTick() / 20.0);
            clear.run();
        });
    }

    // 5. Лабіринт (Fij, скріншот): сіно за дощаною стінкою за 2 бл., а дорога — довкола через прохід у кінці стінки.
    //    Корова має обійти, а не стояти біля стінки й спамити «іде туди»
    @GameTest(template = ARENA, batch = "smell", timeoutTicks = 1200)
    public static void cowWalksAroundWallToSmelledFood(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        Wind.set(h.getLevel(), 0, true);   // штиль: чує на 16 бл.
        BlockPos hay = new BlockPos(7, 2, 3);   // 2 бл. від корови, але за стінкою (було: «дійшла» по прямій і спам)
        h.setBlock(hay, Blocks.HAY_BLOCK);
        for (int z = 1; z <= 12; z++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(6, y, z), Blocks.OAK_PLANKS);   // прохід при z 13
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(5, 2, 3));
        cow.setAge(0);
        int[] starts = { 0 };
        boolean[] cleared = { false };
        Runnable clear = () -> {
            cleared[0] = true;
            cow.discard();
            for (int z = 1; z <= 12; z++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(6, y, z), Blocks.AIR);
            h.setBlock(hay, Blocks.AIR);
        };
        BlockPos[] lastTarget = { null };
        h.onEachTick(() -> {
            cow.hunger = Math.max(cow.hunger, 60);
            boolean searching = cow.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof com.gigongibus.hungrycows.ai.SearchFoodGoal);
            if (searching && lastTarget[0] == null) starts[0]++;
            lastTarget[0] = searching ? hay : null;
        });
        h.runAfterDelay(1199, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов (почала шукати " + starts[0] + " разів)");
            boolean sameSide = Math.signum(cow.getX() - h.absoluteVec(new Vec3(6.5, 0, 0)).x) == Math.signum(h.absoluteVec(new Vec3(7.5, 0, 0)).x - h.absoluteVec(new Vec3(6.5, 0, 0)).x);
            h.assertTrue(sameSide && cow.distanceToSqr(Vec3.atCenterOf(h.absolutePos(hay))) < 3 * 3, "не обійшла стінку до сіна");
            h.assertTrue(starts[0] <= 10, "смикається: пошук починався " + starts[0] + " разів");
            LOG.info("[тест] cowWalksAroundWallToSmelledFood: біля сіна на {} с, пошук починався {} разів", h.getTick() / 20.0, starts[0]);
            clear.run();
        });
    }
}

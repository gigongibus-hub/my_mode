package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowEpisodes;
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
 * Психологічні стани (етап 9). Ігрова логіка не змінюється: тест створює події (як їх створила б гра) і дивиться на наслідки.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowPsycheTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private static HungryCow cow(GameTestHelper h, BlockPos at) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), at);
        c.setAge(0);
        return c;
    }

    // 1. Тричі ледь не втопилась — фобія води; біля води страх росте
    @GameTest(template = "arena", batch = "psyche", timeoutTicks = 300)
    public static void waterPhobia(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(7, 2, 7));
        c.goalSelector.removeAllGoals(g -> true);
        for (int i = 0; i < 3; i++) c.episodes.remember(CowEpisodes.Kind.NEAR_DROWN, "вода");
        BlockPos water = new BlockPos(8, 1, 7);
        h.setBlock(water, Blocks.WATER);
        c.fear = 0;
        int[] maxFear = { 0 };
        h.onEachTick(() -> maxFear[0] = Math.max(maxFear[0], c.fear));
        h.runAtTickTime(200, () -> {
            double p = c.psyche.phobia("вода");
            LOG.info("[тест] waterPhobia: фобія води {}, страх біля води до {}", p, maxFear[0]);
            h.setBlock(water, Blocks.STONE);
            c.discard();
            h.assertTrue(p > 0.5, "немає фобії води");
            h.assertTrue(maxFear[0] > 0, "біля води не страшно");
            h.succeed();
        });
    }

    // 2. Загинуло теля — мати тужить: гірше їсть
    @GameTest(template = "arena", batch = "psyche", timeoutTicks = 100)
    public static void motherGrievesCalf(GameTestHelper h) {
        HungryCow mother = cow(h, new BlockPos(5, 2, 7));
        HungryCow calf = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(8, 2, 7));
        calf.setAge(-24000);
        calf.family.setupNewborn(mother, null);
        double appetiteBefore = mother.psyche.appetiteFactor();
        h.runAtTickTime(20, () -> calf.kill());
        h.runAtTickTime(60, () -> {
            LOG.info("[тест] motherGrievesCalf: туга {}, апетит ×{} -> ×{}", String.format("%.2f", mother.psyche.grief),
                    String.format("%.2f", appetiteBefore), String.format("%.2f", mother.psyche.appetiteFactor()));
            boolean ok = mother.psyche.grief > 0.5 && mother.psyche.appetiteFactor() < appetiteBefore && mother.psyche.griefPlace != null;
            mother.discard();
            h.assertTrue(ok, "мати не тужить за телям");
            h.succeed();
        });
    }

    // 3. Двоє ситих спокійних телят поруч — граються
    @GameTest(template = "arena", batch = "psyche", timeoutTicks = 1200)
    public static void calvesPlay(GameTestHelper h) {
        HungryCow a = cow(h, new BlockPos(5, 2, 7)), b = cow(h, new BlockPos(9, 2, 7));
        a.setAge(-24000);
        b.setAge(-24000);
        boolean[] played = { false }, cleared = { false };
        Runnable clear = () -> { cleared[0] = true; a.discard(); b.discard(); };
        h.onEachTick(() -> {
            a.hunger = 0; b.hunger = 0; a.fear = 0; b.fear = 0;
            if (a.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof com.gigongibus.hungrycows.ai.PlayGoal)
                    || b.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof com.gigongibus.hungrycows.ai.PlayGoal)) played[0] = true;
        });
        h.runAfterDelay(1199, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "за 60 с не гралися");
            h.assertTrue(played[0], "ще не гралися");
            LOG.info("[тест] calvesPlay: почали гратися на {} с", h.getTick() / 20.0);
            clear.run();
        });
    }

    // 4. Травма: дуже сильний напад — травма (з шансом); на тому місці знову страшно
    @GameTest(template = "platform", batch = "psyche", timeoutTicks = 400)
    public static void traumaAtPlace(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(4, 2, 4));
        c.goalSelector.removeAllGoals(g -> true);
        for (int i = 0; i < 20 && !c.psyche.lines().toString().contains("Травма"); i++) {
            for (int k = 0; k < 6; k++) c.chem.onAlarm();
            c.episodes.remember(CowEpisodes.Kind.ATTACK, "m:minecraft:zombie");
        }
        boolean trauma = c.psyche.lines().toString().contains("Травма");
        c.fear = 0;
        int[] maxFear = { 0 };
        h.onEachTick(() -> maxFear[0] = Math.max(maxFear[0], c.fear));
        h.runAtTickTime(300, () -> {
            LOG.info("[тест] traumaAtPlace: травма {}, страх на тому місці до {}", trauma, maxFear[0]);
            c.discard();
            h.assertTrue(trauma, "дуже сильні напади не дали травми");
            h.assertTrue(maxFear[0] > 0, "на місці травми не страшно");
            h.succeed();
        });
    }

    // 5. Етап 11: напис над головою — стан (туга) і подія (злякалась звуку)
    @GameTest(template = "platform", batch = "psyche", timeoutTicks = 40)
    public static void labelOverHead(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(4, 2, 4));
        c.goalSelector.removeAllGoals(g -> true);
        c.episodes.remember(CowEpisodes.Kind.FRIEND_DEATH, "Ромашка");
        String grief = c.stateLabel();
        c.status("нюхає повітря");
        String sniff = c.stateLabel();
        LOG.info("[тест] labelOverHead: «{}», потім «{}»", grief, sniff);
        c.discard();
        h.assertTrue(grief.toLowerCase().contains("тужить"), "немає напису про тугу: " + grief);
        h.assertTrue(sniff.equals("Нюхає повітря"), "немає напису про дію: " + sniff);
        h.succeed();
    }
}

package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Біль і приємне (етап 7). Ігрова логіка не змінюється: тест завдає урону й лякає корову так, як це робила б гра.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowPainTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private static HungryCow cow(GameTestHelper h, BlockPos at) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), at);
        c.setAge(0);
        return c;
    }

    // 1. Ендорфіни: налякана (адреналін) відчуває біль слабше; заспокоїлась — біль «наздоганяє»; сильно болить — кульгає
    @GameTest(template = "platform", batch = "pain", timeoutTicks = 1400)
    public static void endorphinsAndLimp(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(4, 2, 4));
        c.goalSelector.removeAllGoals(g -> true);
        double[] r = new double[3];   // біль у мить удару, відчутий, найбільша частка відчутого пізніше
        h.onEachTick(() -> {
            if (h.getTick() < 60) c.chem.onAlarm();   // 3 с переляку — адреналін і ендорфіни
            if (h.getTick() > 80 && c.chem.rawPain() > 10) r[2] = Math.max(r[2], c.chem.feltPain() / c.chem.rawPain());
        });
        h.runAtTickTime(60, () -> {
            c.hurt(c.damageSources().generic(), 8.0F);
            r[0] = c.chem.rawPain();
            r[1] = c.chem.feltPain();
        });
        h.runAtTickTime(1300, () -> {
            LOG.info("[тест] endorphinsAndLimp: у переляку біль {} відчуває {} (частка {}); пізніше, коли заспокоїлась, частка до {}",
                    Math.round(r[0]), Math.round(r[1]), String.format("%.2f", r[1] / r[0]), String.format("%.2f", r[2]));
            boolean dulled = r[1] < r[0] * 0.8, caughtUp = r[2] > r[1] / r[0] + 0.1;
            c.discard();
            h.assertTrue(dulled, "ендорфіни не приглушили біль");
            h.assertTrue(caughtUp, "біль не «наздогнав» після заспокоєння");
            h.succeed();
        });
    }

    // 2. Сильний біль вчить з одного разу (мозок прив'язує його до дії, яку робила)
    @GameTest(template = "platform", batch = "pain", timeoutTicks = 200)
    public static void strongPainOneShot(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(4, 2, 4));
        h.runAtTickTime(60, () -> c.hurt(c.damageSources().generic(), 6.0F));
        h.runAtTickTime(80, () -> {
            LOG.info("[тест] strongPainOneShot: уроків з одного разу {}", c.brain.oneShotLessons);
            int n = c.brain.oneShotLessons;
            c.discard();
            h.assertTrue(n >= 1, "сильний біль не дав уроку");
            h.succeed();
        });
    }

    // 3. Нове трохи приємне; нове виявилось небезпечним — до схожого (того ж роду) цікавість менша
    @GameTest(template = "platform", batch = "pain", timeoutTicks = 40)
    public static void dangerousNovelty(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(2, 2, 2));
        c.goalSelector.removeAllGoals(g -> true);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(6, 2, 6));
        Sheep sheep = h.spawn(EntityType.SHEEP, new BlockPos(6, 2, 2));
        pig.setNoAi(true);
        sheep.setNoAi(true);
        boolean novelBefore = c.memory.isNovel(sheep);
        c.memory.onInvestigate(pig);
        c.hurt(c.damageSources().generic(), 1.0F);   // одразу після роздивляння — погано
        boolean novelAfter = c.memory.isNovel(sheep);
        LOG.info("[тест] dangerousNovelty: вівця нова до {}, після того як свиня «виявилась небезпечною» {}", novelBefore, novelAfter);
        c.discard(); pig.discard(); sheep.discard();
        h.assertTrue(novelBefore && !novelAfter, "схоже на небезпечне все ще цікаве");
        h.succeed();
    }
}

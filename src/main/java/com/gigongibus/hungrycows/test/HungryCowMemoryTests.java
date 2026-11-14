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
 * Пам'ять подій і місць (CowEpisodes). Ігрова логіка не змінюється: тест сам кличе «сталося» й «настав ранок».
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowMemoryTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private static HungryCow cow(GameTestHelper h, BlockPos at) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), at);
        c.setAge(0);
        c.goalSelector.removeAllGoals(g -> true);
        return c;
    }

    // 1. Сила спогаду: удар у переляку (адреналін) сильніший; добрий сон закріплює, поганий — дрібне забуває
    @GameTest(template = "platform", batch = "memory", timeoutTicks = 40)
    public static void strengthAndSleep(GameTestHelper h) {
        HungryCow calm = cow(h, new BlockPos(2, 2, 2)), scared = cow(h, new BlockPos(6, 2, 6));
        CowEpisodes.Episode a = calm.episodes.remember(CowEpisodes.Kind.HIT, "тест");
        for (int i = 0; i < 5; i++) scared.chem.onAlarm();   // адреналін
        CowEpisodes.Episode b = scared.episodes.remember(CowEpisodes.Kind.HIT, "тест");
        double before = a.strength;
        calm.episodes.morning(true);
        double after = a.strength;
        // спокійна корова: 10 дрібних перелякав за день, потім погана ніч — частину забуває (кожен з шансом 50%)
        HungryCow c2 = cow(h, new BlockPos(4, 2, 6));
        for (int i = 0; i < 10; i++) c2.episodes.remember(CowEpisodes.Kind.FRIGHT, "звук");
        int sizeBefore = c2.episodes.episodes().size();
        c2.episodes.morning(false);
        int sizeAfter = c2.episodes.episodes().size();
        LOG.info("[тест] strengthAndSleep: удар спокійна {} / налякана {}; після сну {} -> {}; дрібні після поганих ночей: було {}, лишилось {}",
                r(a.strength), r(b.strength), r(before), r(after), sizeBefore, sizeAfter);
        boolean ok1 = b.strength > a.strength, ok2 = after > before, ok3 = sizeAfter < sizeBefore;
        calm.discard(); scared.discard(); c2.discard();
        h.assertTrue(ok1, "удар у переляку не сильніший");
        h.assertTrue(ok2, "сон не закріпив спогад");
        h.assertTrue(ok3, "погані ночі нічого не забули");
        h.succeed();
    }

    // 2. Корисні місця: пам'ятає воду; вода зникла — місце не годиться
    @GameTest(template = "platform", batch = "memory", timeoutTicks = 40)
    public static void remembersUsefulPlaces(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(2, 2, 2));
        BlockPos water = new BlockPos(6, 1, 6);
        h.setBlock(water, Blocks.WATER);
        BlockPos abs = h.absolutePos(water);
        c.episodes.visit(CowEpisodes.Place.WATER, abs);
        BlockPos found = c.episodes.nearest(CowEpisodes.Place.WATER, p -> c.level().getFluidState(p).is(net.minecraft.tags.FluidTags.WATER));
        h.setBlock(water, Blocks.STONE);
        BlockPos gone = c.episodes.nearest(CowEpisodes.Place.WATER, p -> c.level().getFluidState(p).is(net.minecraft.tags.FluidTags.WATER));
        LOG.info("[тест] remembersUsefulPlaces: знайшла {}, після того як вода зникла {}", found, gone);
        c.discard();
        h.assertTrue(abs.equals(found), "не пам'ятає воду");
        h.assertTrue(gone == null, "вважає воду там, де її вже немає");
        h.succeed();
    }

    private static String r(double v) {
        return String.format("%.2f", v);
    }
}

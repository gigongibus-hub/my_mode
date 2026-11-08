package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Дотик (CowTouch, GroomGoal). Ігрова логіка не змінюється: тест «гладить» корову так само, як гравець (присів + ПКМ порожньою рукою).
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowTouchTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private static HungryCow cow(GameTestHelper h, BlockPos at) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), at);
        c.setAge(0);
        return c;
    }

    // Гравець, що присів, з порожньою рукою, стоїть з боку side (0 — перед мордою, 180 — позаду)
    private static FakePlayer player(GameTestHelper h, HungryCow c, float side) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "touch_test"));
        double rad = Math.toRadians(c.yBodyRot + side);
        p.moveTo(c.getX() - Math.sin(rad) * 1.5, c.getY(), c.getZ() + Math.cos(rad) * 1.5, 0, 0);
        p.setShiftKeyDown(true);
        return p;
    }

    private static void freeze(HungryCow c) {
        c.goalSelector.removeAllGoals(g -> true);   // лише для тесту: корова стоїть і не крутиться
        c.setYRot(0);
        c.yBodyRot = 0;
        c.setYHeadRot(0);
    }

    // 1. Гладять спереду (друг): окситоцин і довіра ростуть; ззаду — лякається; чужого не підпускає
    @GameTest(template = "arena", batch = "touch", timeoutTicks = 100)
    public static void pettingFrontBackStranger(GameTestHelper h) {
        HungryCow a = cow(h, new BlockPos(3, 2, 3)), b = cow(h, new BlockPos(7, 2, 7)), s = cow(h, new BlockPos(11, 2, 11));
        for (HungryCow c : new HungryCow[]{ a, b, s }) freeze(c);
        FakePlayer front = player(h, a, 0), back = player(h, b, 180), stranger = player(h, s, 0);
        a.addTrust(front, 60, "тест", false);
        b.addTrust(back, 30, "тест", false);
        double oxy0 = a.chem.oxytocinLevel();
        int trust0 = a.trustOf(front);
        a.fear = 0; b.fear = 0; s.fear = 0;
        a.mobInteract(front, InteractionHand.MAIN_HAND);
        b.mobInteract(back, InteractionHand.MAIN_HAND);
        s.mobInteract(stranger, InteractionHand.MAIN_HAND);
        LOG.info("[тест] pettingFrontBackStranger: спереду окситоцин {} -> {}, довіра {} -> {}; ззаду страх {}; чужий страх {}, довіра {}",
                Math.round(oxy0), Math.round(a.chem.oxytocinLevel()), trust0, a.trustOf(front), b.fear, s.fear, s.trustOf(stranger));
        boolean ok1 = a.chem.oxytocinLevel() > oxy0 && a.trustOf(front) > trust0;
        boolean ok2 = b.fear >= 20, ok3 = s.fear > 0 && s.trustOf(stranger) == 0;
        a.discard(); b.discard(); s.discard();
        h.assertTrue(ok1, "погладили спереду — ні окситоцину, ні довіри");
        h.assertTrue(ok2, "торкнулись ззаду — не злякалась");
        h.assertTrue(ok3, "чужого підпустила");
        h.succeed();
    }

    // 2. Мати й теля поруч, спокійні — вилизують одна одну (дружба росте)
    @GameTest(template = "arena", batch = "touch", timeoutTicks = 2400)
    public static void motherAndCalfGroom(GameTestHelper h) {
        HungryCow mother = cow(h, new BlockPos(6, 2, 7));
        HungryCow calf = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(8, 2, 7));
        calf.setAge(-24000);
        calf.family.setupNewborn(mother, null);
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; mother.discard(); calf.discard(); };
        h.onEachTick(() -> { mother.hunger = 0; calf.hunger = 0; mother.fear = 0; calf.fear = 0; });
        h.runAfterDelay(2399, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "за 2 хв не вилизали одна одну");
            h.assertTrue(mother.touch.bond(calf) > 0 && calf.touch.bond(mother) > 0, "ще не вилизували");
            LOG.info("[тест] motherAndCalfGroom: вилизали на {} с, дружба {} / {}", h.getTick() / 20.0,
                    Math.round(mother.touch.bond(calf)), Math.round(calf.touch.bond(mother)));
            clear.run();
        });
    }

    // 3. Вкололась ягідним кущем — місце стає поганим (обходитиме)
    @GameTest(template = "platform", batch = "touch", timeoutTicks = 40)
    public static void thornsMakeBadPlace(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(4, 2, 4));
        freeze(c);
        double before = c.memory.placeValue(c.blockPosition());
        c.hurt(c.damageSources().sweetBerryBush(), 1.0F);
        double after = c.memory.placeValue(c.blockPosition());
        LOG.info("[тест] thornsMakeBadPlace: місце {} -> {}", before, after);
        c.discard();
        h.assertTrue(after < before, "колючки не зробили місце поганим");
        h.succeed();
    }
}

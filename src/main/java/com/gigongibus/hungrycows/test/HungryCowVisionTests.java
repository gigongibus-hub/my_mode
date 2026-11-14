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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Зір (CowVision). Ігрова логіка не змінюється: тест знімає з корови цілі (щоб голова дивилась у заданий бік),
 * ставить нерухому свиню й блоки між ними.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowVisionTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";

    // 1. Спереду бачить; за каменем — ні; крізь скло — так; нерухому позаду — ні
    @GameTest(template = ARENA, batch = "vision", timeoutTicks = 200)
    public static void seesThroughGlassNotStone(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 3));
        cow.setAge(0);
        cow.goalSelector.removeAllGoals(g -> true);   // лише для тесту: голова дивиться, куди поставили
        Pig front = h.spawn(EntityType.PIG, new BlockPos(7, 2, 9));
        front.setNoAi(true);
        Pig back = h.spawn(EntityType.PIG, new BlockPos(7, 2, 1));
        back.setNoAi(true);
        Vec3 f = h.absoluteVec(new Vec3(7.5, 2, 9.5)), c = h.absoluteVec(new Vec3(7.5, 2, 3.5));
        float yaw = (float) (Math.atan2(f.z - c.z, f.x - c.x) * 180 / Math.PI) - 90F;   // обличчям до передньої свині
        String[] stage = { "відкрито" };
        boolean[] ok = new boolean[3];
        h.onEachTick(() -> {
            cow.setYRot(yaw);
            cow.setYHeadRot(yaw);
            cow.yBodyRot = yaw;
            int t = (int) h.getTick();
            if (t == 40) {
                ok[0] = cow.vision.seesNow(front) && !cow.vision.seesNow(back);
                LOG.info("[тест] зір відкрито: спереду {}, позаду {}", cow.vision.seesNow(front), cow.vision.seesNow(back));
                for (int x = 5; x <= 9; x++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
            }
            if (t == 80) {
                ok[1] = !cow.vision.seesNow(front);
                LOG.info("[тест] зір за каменем: спереду {}", cow.vision.seesNow(front));
                for (int x = 5; x <= 9; x++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.GLASS);
            }
            if (t == 120) {
                ok[2] = cow.vision.seesNow(front);
                LOG.info("[тест] зір крізь скло: спереду {}", cow.vision.seesNow(front));
            }
        });
        h.runAtTickTime(130, () -> {
            for (int x = 5; x <= 9; x++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.AIR);
            cow.discard();
            front.discard();
            back.discard();
            h.assertTrue(ok[0], "відкрито: не бачить свині спереду або бачить нерухому позаду");
            h.assertTrue(ok[1], "бачить крізь камінь");
            h.assertTrue(ok[2], "не бачить крізь скло");
            h.succeed();
        });
    }

    // 2. Їжа за стіною: голодна корова її не бачить, іде шукати, обходить стінку й з'їдає
    @GameTest(template = ARENA, batch = "vision", timeoutTicks = 1800)
    public static void cowSearchesForHiddenFood(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        // трав'яний блок у підлозі за кам'яною стінкою (3 бл. заввишки, 7 завширшки) посеред загону: з місця корови не видно,
        // обійде стінку — побачить
        BlockPos grass = new BlockPos(7, 1, 10);
        h.setBlock(grass, Blocks.GRASS_BLOCK);
        for (int i = 4; i <= 10; i++) {
            for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(i, y, 8), Blocks.STONE);
        }
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 3));
        cow.setAge(0);
        cow.hunger = 60;
        LOG.info("[тест] cowSearchesForHiddenFood: корова {}", cow.family.name);
        int[] notSeenAt = { -1 };
        boolean[] cleared = { false };
        Runnable clear = () -> {
            cleared[0] = true;
            cow.discard();
            for (int i = 4; i <= 10; i++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(i, y, 8), Blocks.AIR);
            h.setBlock(grass, Blocks.STONE);
        };
        h.onEachTick(() -> {
            if (notSeenAt[0] < 0 && cow.foodNotSeenTick > 0) notSeenAt[0] = (int) h.getTick();
            cow.hunger = Math.max(cow.hunger, 60);
        });
        h.runAfterDelay(1799, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов");
            h.assertTrue(notSeenAt[0] >= 0, "корова одразу «побачила» їжу за стіною");
            h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(grass)).is(Blocks.GRASS_BLOCK), "ще не знайшла й не з'їла траву за стіною");
            LOG.info("[тест] cowSearchesForHiddenFood: не бачила їжі на {} с, з'їла на {} с", notSeenAt[0] / 20.0, h.getTick() / 20.0);
            clear.run();
        });
    }
}

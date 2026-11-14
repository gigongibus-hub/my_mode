package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Тести ходьби за шляхом (nav/CowNavigation.followThePath). Щоб перевіряти саму ходьбу, з корови на час тесту знімаються
 * всі цілі (інакше мозок, стадо й сон самі кличуть moveTo), і тест сам веде її до точки. Ігрова логіка не змінюється.
 * Перевіряє: дійшла за відведений час і скільки разів уперлась у блок (удари об стіну).
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowNavTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String ARENA = "arena";
    private static final String BATCH = "nav";
    private static final int TIMEOUT = 400;          // 20 с на дорогу
    private static final double ARRIVED = 1.5;       // дійшла = ближче 1,5 блока (moveTo будує шлях «з точністю 1 блок»)
    private static final int REPATH = 20;            // тест кличе moveTo раз на секунду (як цілі корови)

    private static HungryCow cowWithoutGoals(GameTestHelper h, BlockPos at) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), at);
        cow.setAge(0);
        cow.goalSelector.removeAllGoals(g -> true);   // лише для тесту: ніхто, крім тесту, не веде корову
        return cow;
    }

    // Вести корову до точки й перевірити, що дійшла; скільки разів уперлась у блок
    // Прибрати збудоване тестом (споруди лишаються у світі під майданчиками наступних партій)
    private static void clear(GameTestHelper h) {
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z <= 13; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                h.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                h.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
            }
        }
    }

    private static void walk(GameTestHelper h, HungryCow cow, BlockPos target, String name, int maxBumps) {
        h.runAfterDelay(TIMEOUT - 1, () -> { cow.discard(); clear(h); });
        Vec3 t = h.absoluteVec(Vec3.atBottomCenterOf(target));
        int[] bumps = { 0 };
        boolean[] was = { false };
        h.onEachTick(() -> {
            if (h.getTick() % REPATH == 1) cow.getNavigation().moveTo(t.x, t.y, t.z, 1.0);
            boolean b = cow.horizontalCollision;
            if (b && !was[0]) bumps[0]++;
            was[0] = b;
        });
        h.succeedWhen(() -> {
            double d = cow.position().distanceTo(t);
            var p = cow.getNavigation().getPath();
            h.assertTrue(d < ARRIVED, String.format("%s: не дійшла (до цілі %.2f бл.), корова (%.2f %.2f %.2f), шлях %s, ударів %d", name, d,
                    cow.getX() - h.absoluteVec(Vec3.ZERO).x, cow.getY(), cow.getZ() - h.absoluteVec(Vec3.ZERO).z,
                    p == null ? "немає" : p.getNextNodeIndex() + "/" + p.getNodeCount(), bumps[0]));
            h.assertTrue(bumps[0] <= maxBumps, name + ": ударів об стіну " + bumps[0]);
            LOG.info("[тест] {}: дійшла за {} с, ударів об стіну {}", name, String.format("%.1f", h.getTick() / 20.0), bumps[0]);
            cow.discard();
            clear(h);
        });
    }

    // 1. Навскіс через усе поле, з центру клітинки (корова з'являється точно в центрі блока)
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void walksDiagonallyFromBlockCenter(GameTestHelper h) {
        HungryCow cow = cowWithoutGoals(h, new BlockPos(2, 2, 2));
        walk(h, cow, new BlockPos(12, 2, 12), "walksDiagonallyFromBlockCenter", 0);
    }

    // 2. Старт на утоптаній стежці (вона нижча за повний блок на 1/16)
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void walksFromDirtPath(GameTestHelper h) {
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 13; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.DIRT_PATH);
        }
        HungryCow cow = cowWithoutGoals(h, new BlockPos(2, 2, 2));
        walk(h, cow, new BlockPos(11, 2, 12), "walksFromDirtPath", 1);   // крок зі стежки (на 1/16 нижча) на камінь гра теж рахує «упертись»
    }

    // 3. Через прохід завширшки 1 блок у стіні, з поворотом перед ним і після нього
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void walksThroughOneBlockDoorWithTurns(GameTestHelper h) {
        for (int z = 1; z <= 13; z++) {
            if (z == 7) continue;   // прохід
            h.setBlock(new BlockPos(7, 2, z), Blocks.OAK_PLANKS);
            h.setBlock(new BlockPos(7, 3, z), Blocks.OAK_PLANKS);
        }
        HungryCow cow = cowWithoutGoals(h, new BlockPos(3, 2, 2));
        walk(h, cow, new BlockPos(11, 2, 12), "walksThroughOneBlockDoorWithTurns", 3);   // у проході 1 блок корова 0,9 бл. торкається косяків
    }
}

package com.gigongibus.hungrycows.test;

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
 * Автоматичні тести корів (Forge GameTest). Запуск: ./gradlew runGameTestServer
 * (або в грі командою /test runall). У звичайній грі цей клас нічого не робить: тести
 * запускаються лише тестовим сервером або командою /test.
 *
 * Тести перевіряють справжню поведінку корови: нічого в ігровій логіці не підмінюється,
 * тест лише ставить корову на майданчик, задає початковий голод чи втому й чекає.
 * Майданчик: data/hungrycows/structures/platform.nbt — підлога 9×9 з каменю, скляні стіни у 2 блоки.
 * Усі числа зібрані на початку файлу.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowGameTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String PLATFORM = "platform";        // тестовий майданчик
    private static final long NOON = 6000;                     // полудень: корови не йдуть спати
    // Координати тесту рахуються від службового блока під майданчиком: y=1 — підлога, y=2 — над підлогою
    private static final BlockPos CENTER = new BlockPos(4, 2, 4);
    // 1. Голод: росте на 1 приблизно раз на 7 с (ген ×0.7…1.3) — чекаємо до 60 с
    private static final int HUNGER_TIMEOUT = 1200;
    // 2. Їжа: голодна корова (голод 40 — удень іде їсти завжди) знаходить трав'яний блок за 6 блоків і з'їдає його
    private static final int FOOD_HUNGER = 40;
    private static final BlockPos FOOD_COW = new BlockPos(1, 2, 4);
    private static final BlockPos FOOD_GRASS = new BlockPos(7, 1, 4);   // у підлозі, з повітрям над ним
    private static final int FOOD_TIMEOUT = 1200;
    // 3. Втома: росте на 1 приблизно раз на 10 с — чекаємо; потім виснаження (100) → відпочинок → втома спадає
    private static final int FATIGUE_TIMEOUT = 3600;
    private static final int FATIGUE_RECOVERED_BY = 10;        // має спасти щонайменше на стільки

    // Поставити дорослу корову на майданчик удень
    private static HungryCow spawnCow(GameTestHelper h, BlockPos pos) {
        h.getLevel().setDayTime(NOON);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), pos);
        cow.setAge(0);   // доросла (теля їсть лише молоко)
        return cow;
    }

    // Стан корови для повідомлення про провал: де стоїть, показники, ціль їжі і службовий напис (там видно активні цілі)
    private static String describe(GameTestHelper h, HungryCow cow) {
        BlockPos rel = h.relativePos(cow.blockPosition());
        BlockPos food = cow.foodTarget == null ? null : h.relativePos(cow.foodTarget);
        return "корова на " + rel.toShortString() + ", голод " + cow.hunger + ", втома " + cow.fatigue + ", страх " + cow.fear
                + ", спрага " + cow.thirst + ", ціль їжі " + (food == null ? "немає" : food.toShortString())
                + ", напис [" + (cow.getCustomName() == null ? "" : cow.getCustomName().getString()) + "]";
    }

    private static void assertAlive(GameTestHelper h, HungryCow cow) {
        if (!cow.isAlive()) h.fail("корова загинула");
    }

    // 1. Голод з часом росте
    @GameTest(template = PLATFORM, timeoutTicks = HUNGER_TIMEOUT)
    public static void hungerGrows(GameTestHelper h) {
        HungryCow cow = spawnCow(h, CENTER);
        cow.hunger = 0;
        h.succeedWhen(() -> {
            assertAlive(h, cow);
            h.assertTrue(cow.hunger > 0, "голод не виріс за " + HUNGER_TIMEOUT / 20 + " с (голод " + cow.hunger + ")");
            LOG.info("[тест] hungerGrows: голод {} через {} тіків", cow.hunger, h.getTick());
        });
    }

    // 2. Голодна корова сама знаходить їжу і з'їдає її (трав'яний блок стає землею, голод спадає)
    @GameTest(template = PLATFORM, timeoutTicks = FOOD_TIMEOUT)
    public static void cowFindsAndEatsFood(GameTestHelper h) {
        h.setBlock(FOOD_GRASS, Blocks.GRASS_BLOCK);
        HungryCow cow = spawnCow(h, FOOD_COW);
        cow.hunger = FOOD_HUNGER;
        h.succeedWhen(() -> {
            assertAlive(h, cow);
            h.assertTrue(h.getBlockState(FOOD_GRASS).is(Blocks.DIRT), "трав'яний блок не з'їдено: " + describe(h, cow));
            h.assertTrue(cow.hunger < FOOD_HUNGER, "трава з'їдена, але голод не спав (голод " + cow.hunger + ")");
            LOG.info("[тест] cowFindsAndEatsFood: трава з'їдена, голод {} -> {} через {} тіків", FOOD_HUNGER, cow.hunger, h.getTick());
        });
    }

    // 3. Втома накопичується, а виснажена корова відпочиває і втома спадає
    @GameTest(template = PLATFORM, timeoutTicks = FATIGUE_TIMEOUT)
    public static void fatigueGrowsAndRecovers(GameTestHelper h) {
        HungryCow cow = spawnCow(h, CENTER);
        cow.fatigue = 0;
        h.startSequence()
                .thenWaitUntil(() -> {
                    assertAlive(h, cow);
                    h.assertTrue(cow.fatigue > 0, "втома не росте (втома " + cow.fatigue + ")");
                    LOG.info("[тест] fatigueGrowsAndRecovers: втома виросла до {} через {} тіків", cow.fatigue, h.getTick());
                })
                .thenExecute(() -> cow.fatigue = HungryCow.EXHAUSTED_AT)
                .thenWaitUntil(() -> {
                    assertAlive(h, cow);
                    h.assertTrue(cow.resting, "виснажена корова не відпочиває (втома " + cow.fatigue + ")");
                })
                .thenWaitUntil(() -> {
                    assertAlive(h, cow);
                    h.assertTrue(cow.fatigue <= HungryCow.EXHAUSTED_AT - FATIGUE_RECOVERED_BY,
                            "під час відпочинку втома не спадає (втома " + cow.fatigue + ")");
                    LOG.info("[тест] fatigueGrowsAndRecovers: відпочинок, втома {} -> {} на тіку {}", HungryCow.EXHAUSTED_AT, cow.fatigue, h.getTick());
                })
                .thenSucceed();
    }
}

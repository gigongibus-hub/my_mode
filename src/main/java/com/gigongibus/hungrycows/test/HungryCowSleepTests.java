package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowHome;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Тест сну в хліві (Forge GameTest), окрема партія "sleep".
 * Майданчик: data/hungrycows/structures/barn.nbt — камінь 17×17, скляна огорожа 2 бл.; хлів 7×7 з дощок
 * (усередині 5×5, дах, відкритий вхід 3×3 без дверей, як у будинках Fij).
 *
 * Механіка не змінюється: тест ставить 3 корови надворі, дає їм день, щоб знайти дім (як у звичайній грі),
 * потім ставить ніч і чекає, поки всі зайдуть у хлів і заснуть.
 * Провал = котрась корова не встигла: шкала сну дійшла до 100, а вона ще надворі (тоді засинає, де є), або час вийшов.
 * Усі числа зібрані на початку файлу.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowSleepTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String BARN = "barn";
    private static final String BATCH = "sleep";
    private static final long NOON = 6000;
    private static final long NIGHT = 13000;                 // ніч: шкала сну +2 кожні 2 с (100 за ~100 с)
    private static final int DAY_TICKS = 60 * 20;           // день, щоб корови знайшли дім
    private static final int NIGHT_TICKS = 150 * 20;         // ніч: на дорогу й сон щонайбільше 2,5 хв
    private static final int TIMEOUT = DAY_TICKS + NIGHT_TICKS + 100;
    // корови надворі, на захід від входу (координати тесту: y=1 — підлога, y=2 — над нею)
    private static final List<BlockPos> COWS = List.of(new BlockPos(2, 2, 6), new BlockPos(2, 2, 8), new BlockPos(2, 2, 10));

    private static boolean inBarn(GameTestHelper h, HungryCow cow) {
        return CowHome.shelterScore(h.getLevel(), cow.blockPosition()) > 0;
    }

    private static String state(GameTestHelper h, HungryCow cow) {
        BlockPos home = cow.home.home();
        return String.format("%s: %s, спить %s, сон %d, дім %s (бали %d), [%s]", cow.family.name,
                inBarn(h, cow) ? "у хліві" : "надворі", cow.sleeping ? "так" : "ні", Math.round(cow.sleep.sleepiness),
                home == null ? "немає" : home.toShortString(), cow.home.homeScore(),
                cow.getCustomName() == null ? "" : cow.getCustomName().getString());
    }

    // Нічлігом у хліві: 3 корови надворі вдень знаходять хлів, а вночі всі заходять і засинають
    @GameTest(template = BARN, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void cowsSleepInBarn(GameTestHelper h) {
        sleepTest(h, COWS, "cowsSleepInBarn");
    }

    // Великий хлів (bigbarn.nbt: майданчик 81×81, щоб сусідні тести були далі 32 блоків; хлів посередині, усередині 15×15,
    // стеля на висоті 10 блоків, вхід 3×3 на заході): дах високо, стіни далеко
    private static final String BIG_BARN = "bigbarn";
    private static final List<BlockPos> BIG_COWS = List.of(new BlockPos(24, 2, 37), new BlockPos(24, 2, 40), new BlockPos(24, 2, 43));

    @GameTest(template = BIG_BARN, batch = "sleep_big", timeoutTicks = TIMEOUT)   // окрема партія: інакше корови бачать сусідній хлів
    public static void cowsSleepInBigBarn(GameTestHelper h) {
        sleepTest(h, BIG_COWS, "cowsSleepInBigBarn");
    }

    private static void sleepTest(GameTestHelper h, List<BlockPos> spawns, String name) {
        h.getLevel().setDayTime(NOON);
        List<HungryCow> cows = spawns.stream().map(p -> {
            HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), p);
            c.setAge(0);
            return c;
        }).toList();
        int[] nightStart = { -1 };
        int[] arrived = new int[cows.size()];
        java.util.Arrays.fill(arrived, -1);
        h.onEachTick(() -> {
            if (nightStart[0] < 0) return;
            for (int i = 0; i < cows.size(); i++) {
                HungryCow c = cows.get(i);
                if (arrived[i] < 0 && inBarn(h, c) && c.sleeping) arrived[i] = (int) h.getTick() - nightStart[0];
            }
        });
        h.startSequence()
                .thenIdle(DAY_TICKS)
                .thenExecute(() -> {
                    for (HungryCow c : cows) LOG.info("[тест] {}, кінець дня: {}", name, state(h, c));
                    h.getLevel().setDayTime(NIGHT);
                    nightStart[0] = (int) h.getTick();
                })
                .thenWaitUntil(() -> {
                    for (HungryCow c : cows) {
                        if (!c.isAlive()) h.fail("корова загинула: " + c.family.name);
                        // шкала сну повна, а корова надворі — вона вже засне, де є: провал одразу
                        if (c.sleep.full() && !inBarn(h, c)) h.fail("не встигла дійти до хліва: " + state(h, c));
                    }
                    for (HungryCow c : cows) {
                        h.assertTrue(inBarn(h, c) && c.sleeping, "ще не спить у хліві: " + state(h, c));
                    }
                })
                .thenExecute(() -> {
                    for (int i = 0; i < cows.size(); i++) {
                        LOG.info("[тест] {}: {} заснула в хліві через {} с ночі", name, cows.get(i).family.name,
                                String.format("%.1f", arrived[i] / 20.0));
                    }
                })
                .thenSucceed();
    }
}

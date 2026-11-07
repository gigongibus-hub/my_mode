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

    // Прибрати після тесту хлів і огорожу: споруди тестів лишаються у світі, а наступна партія ставить свої майданчики на
    // тому самому місці — залишки даху робили з них «хлів» для чужих корів
    private static void clearBuilding(GameTestHelper h, int size, int x0, int x1, int z0, int z1, int top) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 2; y <= top; y++) h.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR);
            }
        }
        for (int i = 0; i < size; i++) {
            for (int y = 2; y <= 3; y++) {
                h.setBlock(new BlockPos(i, y, 0), net.minecraft.world.level.block.Blocks.AIR);
                h.setBlock(new BlockPos(i, y, size - 1), net.minecraft.world.level.block.Blocks.AIR);
                h.setBlock(new BlockPos(0, y, i), net.minecraft.world.level.block.Blocks.AIR);
                h.setBlock(new BlockPos(size - 1, y, i), net.minecraft.world.level.block.Blocks.AIR);
            }
        }
    }

    private static void clearBarn(GameTestHelper h, boolean big) {
        if (big) clearBuilding(h, 81, 32, 48, 32, 48, 12);
        else clearBuilding(h, 17, 9, 15, 5, 11, 5);
    }

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
        sleepTest(h, COWS, "cowsSleepInBarn", false);
    }

    // Великий хлів (bigbarn.nbt: майданчик 81×81, щоб сусідні тести були далі 32 блоків; хлів посередині, усередині 15×15,
    // стеля на висоті 10 блоків, вхід 3×3 на заході): дах високо, стіни далеко
    private static final String BIG_BARN = "bigbarn";
    private static final List<BlockPos> BIG_COWS = List.of(new BlockPos(24, 2, 37), new BlockPos(24, 2, 40), new BlockPos(24, 2, 43));

    @GameTest(template = BIG_BARN, batch = "sleep_big", timeoutTicks = TIMEOUT)   // окрема партія: інакше корови бачать сусідній хлів
    public static void cowsSleepInBigBarn(GameTestHelper h) {
        sleepTest(h, BIG_COWS, "cowsSleepInBigBarn", true);
    }

    // Вихід на пасовище: 3 корови посеред ВЕЛИКОГО хліва (звідти прогулянкою випадково не вийдеш), усередині кавуни, день →
    // за 3 хв хоча б одна виходить надвір (рішення за характером, GoOutGoal)
    private static final List<BlockPos> IN_BARN = List.of(new BlockPos(42, 2, 38), new BlockPos(42, 2, 40), new BlockPos(42, 2, 42));
    private static final int PASTURE_TICKS = 3 * 60 * 20;
    private static final double FAR_FROM_BARN = 10;  // «пішла на пасовище» = щонайменше 10 блоків від стін хліва (не стоїть біля виходу)

    // Скільки блоків від корови до найближчої стіни хліва ззовні (0 — усередині чи впритул)
    private static double outsideBarn(GameTestHelper h, HungryCow c) {
        net.minecraft.world.phys.Vec3 a = h.absoluteVec(new net.minecraft.world.phys.Vec3(32, 0, 32));
        net.minecraft.world.phys.Vec3 b = h.absoluteVec(new net.minecraft.world.phys.Vec3(49, 0, 49));
        double minX = Math.min(a.x, b.x), maxX = Math.max(a.x, b.x), minZ = Math.min(a.z, b.z), maxZ = Math.max(a.z, b.z);
        double dx = Math.max(0, Math.max(minX - c.getX(), c.getX() - maxX));
        double dz = Math.max(0, Math.max(minZ - c.getZ(), c.getZ() - maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    // Раніше падав: дорога надвір у обхід стін довша за 32 блоки (FOLLOW_RANGE = найдовша дорога пошуку шляху). Тепер 64.
    @GameTest(template = BIG_BARN, batch = "pasture", timeoutTicks = PASTURE_TICKS + 100)
    public static void cowsGoOutToPasture(GameTestHelper h) {
        h.getLevel().setDayTime(NOON);
        h.setBlock(new BlockPos(45, 2, 36), net.minecraft.world.level.block.Blocks.MELON);
        h.setBlock(new BlockPos(45, 2, 44), net.minecraft.world.level.block.Blocks.MELON);
        List<HungryCow> cows = IN_BARN.stream().map(p -> {
            HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), p);
            c.setAge(0);
            return c;
        }).toList();
        int[] firstOut = new int[cows.size()];
        java.util.Arrays.fill(firstOut, -1);
        double[] farthest = new double[cows.size()];   // найдалі від хліва (блоків від стін зовні)
        h.onEachTick(() -> {
            for (int i = 0; i < cows.size(); i++) {
                HungryCow c = cows.get(i);
                if (firstOut[i] < 0 && !com.gigongibus.hungrycows.CowHome.hasRoof(h.getLevel(), c.blockPosition())) firstOut[i] = (int) h.getTick();
                farthest[i] = Math.max(farthest[i], outsideBarn(h, c));
            }
        });
        h.startSequence()
                .thenIdle(PASTURE_TICKS)
                .thenExecute(() -> {
                    int out = 0, far = 0;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < cows.size(); i++) {
                        HungryCow c = cows.get(i);
                        if (firstOut[i] >= 0) out++;
                        if (farthest[i] >= FAR_FROM_BARN) far++;
                        sb.append(String.format("%s (активність %d, допитливість %d, полохливість %d): %s; ", c.family.name,
                                c.character.get(com.gigongibus.hungrycows.CowCharacter.Trait.ACTIVITY),
                                c.character.get(com.gigongibus.hungrycows.CowCharacter.Trait.CURIOSITY),
                                c.character.get(com.gigongibus.hungrycows.CowCharacter.Trait.FEARFULNESS),
                                firstOut[i] < 0 ? "лишилась у хліві" : String.format("вийшла на %.0f с, відійшла від хліва на %.0f бл.", firstOut[i] / 20.0, farthest[i])));
                    }
                    LOG.info("[тест] cowsGoOutToPasture: вийшло {} з {}, відійшли на {}+ бл. — {}: {}", out, cows.size(), (int) FAR_FROM_BARN, far, sb);
                    cows.forEach(net.minecraft.world.entity.Entity::discard);   // прибрати: інакше вони стають «стадом» для сусідніх тестів
                    clearBarn(h, true);
                    h.assertTrue(far >= 1, "за 3 хв жодна корова не відійшла від хліва на " + (int) FAR_FROM_BARN + "+ блоків: " + sb);
                })
                .thenSucceed();
        h.runAfterDelay(PASTURE_TICKS + 99, () -> { cows.forEach(net.minecraft.world.entity.Entity::discard); clearBarn(h, true); });
    }

    private static void sleepTest(GameTestHelper h, List<BlockPos> spawns, String name, boolean big) {
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
                    cows.forEach(net.minecraft.world.entity.Entity::discard);   // прибрати: інакше вони стають «стадом» для сусідніх тестів
                    clearBarn(h, big);
                    for (int i = 0; i < cows.size(); i++) {
                        LOG.info("[тест] {}: {} заснула в хліві через {} с ночі", name, cows.get(i).family.name,
                                String.format("%.1f", arrived[i] / 20.0));
                    }
                })
                .thenSucceed();
        h.runAfterDelay(TIMEOUT - 1, () -> { cows.forEach(net.minecraft.world.entity.Entity::discard); clearBarn(h, big); });
    }
}

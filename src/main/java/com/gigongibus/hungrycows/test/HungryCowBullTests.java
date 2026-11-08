package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Бики (ідеї Fij 12–13). Стать корови випадкова й задається при появі, тож тест створює кілька корів і бере потрібних.
 * Ігрова логіка не змінюється.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowBullTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String ARENA = "arena";
    private static final String BATCH = "bulls";
    private static final int TRIES = 24;                 // скільки корів створити, щоб знайти потрібну стать
    private static final int ZOMBIE_TIMEOUT = 600;       // 30 с, щоб хоч один бик ударив зомбі

    static List<HungryCow> spawnSex(GameTestHelper h, boolean bulls, int count, BlockPos... at) {
        List<HungryCow> out = new ArrayList<>();
        for (int i = 0; i < TRIES && out.size() < count; i++) {
            HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), at[out.size() % at.length]);
            c.setAge(0);
            if (c.repro.isFemale() == !bulls) out.add(c);
            else c.discard();
        }
        return out;
    }

    // 1. Шанс «бий або тікай»: бик більше за корову, теля 0
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 100)
    public static void fightChanceBullOverCow(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 12));
        z.setNoAi(true);
        List<HungryCow> bulls = spawnSex(h, true, 3, new BlockPos(3, 2, 3));
        List<HungryCow> cows = spawnSex(h, false, 3, new BlockPos(11, 2, 3));
        HungryCow calf = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 3));
        calf.setAge(-24000);
        double bull = bulls.stream().mapToDouble(c -> c.fightChance(z)).average().orElse(-1);
        double cow = cows.stream().mapToDouble(c -> c.fightChance(z)).average().orElse(-1);
        LOG.info("[тест] fightChanceBullOverCow: бик {}, корова {}, теля {}", String.format("%.2f", bull), String.format("%.2f", cow), calf.fightChance(z));
        h.assertTrue(bulls.size() == 3 && cows.size() == 3, "не вийшло створити 3 биків і 3 корів");
        h.assertTrue(bull > cow, "бик не сміливіший за корову: " + bull + " / " + cow);
        h.assertTrue(calf.fightChance(z) == 0, "теля нападає");
        h.assertTrue(bulls.get(0).isBull() && !cows.get(0).isBull(), "isBull плутає стать");
        bulls.forEach(HungryCow::discard);
        cows.forEach(HungryCow::discard);
        calf.discard();
        z.discard();
        h.succeed();
    }

    // 2. Бики поруч із зомбі: хоч один не тікає, а б'є його
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = ZOMBIE_TIMEOUT)
    public static void bullsRamZombie(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        List<HungryCow> bulls = spawnSex(h, true, 3, new BlockPos(5, 2, 7), new BlockPos(7, 2, 5), new BlockPos(9, 2, 7));
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 9));
        z.setNoAi(true);   // зомбі стоїть: тест перевіряє бика
        float full = z.getHealth();
        h.runAfterDelay(ZOMBIE_TIMEOUT - 1, () -> { bulls.forEach(HungryCow::discard); z.discard(); });
        h.succeedWhen(() -> {
            h.assertTrue(bulls.size() == 3, "не вийшло створити 3 биків");
            h.assertTrue(!z.isAlive() || z.getHealth() < full, "жоден бик не вдарив зомбі (здоров'я зомбі " + z.getHealth() + ")");
            LOG.info("[тест] bullsRamZombie: зомбі {} -> {} за {} с", full, z.isAlive() ? z.getHealth() : 0, String.format("%.1f", h.getTick() / 20.0));
            bulls.forEach(HungryCow::discard);
            z.discard();
        });
    }

    // 3. Бійка двох биків: обидва живі, хтось програв і тікає, переможець домінантніший
    private static final int FIGHT_TIMEOUT = 600;

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = FIGHT_TIMEOUT)
    public static void bullsFightUntilOneYields(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        List<HungryCow> bulls = spawnSex(h, true, 2, new BlockPos(4, 2, 7), new BlockPos(10, 2, 7));
        h.assertTrue(bulls.size() == 2, "не вийшло створити 2 биків");
        HungryCow a = bulls.get(0), b = bulls.get(1);
        int domA = a.character.get(com.gigongibus.hungrycows.CowCharacter.Trait.DOMINANCE);
        int domB = b.character.get(com.gigongibus.hungrycows.CowCharacter.Trait.DOMINANCE);
        float fullA = a.getHealth(), fullB = b.getHealth();
        com.gigongibus.hungrycows.ai.BullFightGoal.startFight(a, b);
        h.runAfterDelay(FIGHT_TIMEOUT - 1, () -> bulls.forEach(HungryCow::discard));
        h.succeedWhen(() -> {
            h.assertTrue(a.isAlive() && b.isAlive(), "бик загинув у бійці");
            h.assertTrue(a.fightRival == null && b.fightRival == null, "ще б'ються");
            h.assertTrue(a.retreating() || b.retreating(), "бійка скінчилась, але ніхто не тікає");
            HungryCow winner = a.retreating() ? b : a, loser = winner == a ? b : a;
            int domW = winner.character.get(com.gigongibus.hungrycows.CowCharacter.Trait.DOMINANCE);
            int before = winner == a ? domA : domB;
            LOG.info("[тест] bullsFightUntilOneYields: за {} с; {} {} -> {}, {} {} -> {}; переміг {} (домінантність {} -> {})",
                    String.format("%.1f", h.getTick() / 20.0), a.family.name, fullA, a.getHealth(), b.family.name, fullB, b.getHealth(),
                    winner.family.name, before, domW);
            h.assertTrue(a.getHealth() < fullA || b.getHealth() < fullB, "у бійці ніхто не отримав удару");
            h.assertTrue(domW >= before, "переможець не став домінантнішим");
            bulls.forEach(HungryCow::discard);
        });
    }
}

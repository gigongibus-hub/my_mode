package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowFood;
import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModBlocks;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Смак (CowTaste) і соляний лизунець. Ігрова логіка не змінюється: тест задає гени й годує корову так, як це робила б гра.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowTasteTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private static HungryCow cow(GameTestHelper h, BlockPos at) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), at);
        c.setAge(0);
        return c;
    }

    // 1. Гірке несмачне (папороть гірша за траву), ласунка любить солодке; трава набридає
    @GameTest(template = "platform", batch = "taste", timeoutTicks = 40)
    public static void tastesAndBoredom(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(4, 2, 4));
        c.goalSelector.removeAllGoals(g -> true);
        c.genes.set(CowGenes.Gene.TASTE_SWEET, 100);
        c.genes.set(CowGenes.Gene.TASTE_BITTER, 50);
        double grass = c.taste.tastiness(Items.GRASS), fern = c.taste.tastiness(Items.FERN), melon = c.taste.tastiness(Items.MELON);
        for (int i = 0; i < 8; i++) c.memory.rememberMeal(Items.GRASS);   // 8 разів поспіль трава
        double grassBored = c.taste.tastiness(Items.GRASS);
        LOG.info("[тест] tastesAndBoredom: трава {}, папороть {}, кавун {}; трава після 8 разів {}", r(grass), r(fern), r(melon), r(grassBored));
        c.discard();
        h.assertTrue(fern < grass, "папороть не гірша за траву");
        h.assertTrue(melon > grass, "ласунці кавун не смачніший за траву");
        h.assertTrue(grassBored < grass - 0.2, "трава не набридла");
        h.succeed();
    }

    // 2. Незнайому гірку їжу лише пробує (насичує на 30%); вдруге — уже знайома, їсть як слід
    @GameTest(template = "platform", batch = "taste", timeoutTicks = 40)
    public static void triesUnfamiliarBitter(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(4, 2, 4));
        c.goalSelector.removeAllGoals(g -> true);
        CowFood lilyPad = CowFood.get(Items.LILY_PAD);   // гірке, без шкоди перші рази
        boolean firstTry = c.taste.onlyTries(Items.LILY_PAD);
        c.hunger = 50;
        c.eatFood(lilyPad, Items.LILY_PAD);
        int first = 50 - c.hunger;
        boolean secondTry = c.taste.onlyTries(Items.LILY_PAD);
        c.hunger = 50;
        c.eatFood(lilyPad, Items.LILY_PAD);
        int second = 50 - c.hunger;
        LOG.info("[тест] triesUnfamiliarBitter: уперше пробує {} (насичує {}), удруге пробує {} (насичує {})", firstTry, first, secondTry, second);
        c.discard();
        h.assertTrue(firstTry && !secondTry, "пробує не лише першого разу");
        h.assertTrue(first < second, "шматочок насичує не менше за повну порцію");
        h.succeed();
    }

    // 3. Хоче солі — іде до лизунця й лиже
    @GameTest(template = "arena", batch = "taste", timeoutTicks = 800)
    public static void cowLicksSalt(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(3, 2, 7));
        c.hunger = 0;
        c.taste.salt = 80;
        BlockPos lick = new BlockPos(10, 2, 7);
        h.setBlock(lick, ModBlocks.SALT_LICK.get());
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; c.discard(); h.setBlock(lick, Blocks.AIR); };
        h.onEachTick(() -> c.hunger = 0);
        h.runAfterDelay(799, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "час вийшов (сіль " + Math.round(c.taste.salt) + ")");
            h.assertTrue(c.taste.salt < 40, "ще не лизала (сіль " + Math.round(c.taste.salt) + ")");
            LOG.info("[тест] cowLicksSalt: полизала на {} с, бажання солі 80 -> {}", h.getTick() / 20.0, Math.round(c.taste.salt));
            clear.run();
        });
    }

    private static String r(double v) {
        return String.format("%.2f", v);
    }
}

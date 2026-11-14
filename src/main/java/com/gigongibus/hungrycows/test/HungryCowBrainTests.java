package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.LobeNet;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Мозок із часток (етап 8). Ігрова логіка не змінюється.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowBrainTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private static HungryCow cow(GameTestHelper h, BlockPos at) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), at);
        c.setAge(0);
        return c;
    }

    // 1. 500 нейронів, кілька тисяч зв'язків; добрий нюх — більша частка нюху; збереження й завантаження ваг
    @GameTest(template = "platform", batch = "brain", timeoutTicks = 40)
    public static void lobesSizesAndSave(GameTestHelper h) {
        HungryCow a = cow(h, new BlockPos(2, 2, 2)), b = cow(h, new BlockPos(6, 2, 6));
        a.genes.set(CowGenes.Gene.SMELL_ACUITY, 100);
        b.genes.set(CowGenes.Gene.SMELL_ACUITY, 0);
        LobeNet na = a.brain.net(), nb = b.brain.net();
        int total = 0;
        for (LobeNet.Lobe l : LobeNet.Lobe.values()) total += na.size(l);
        CompoundTag tag = new CompoundTag();
        a.brain.save(tag);
        HungryCow c = cow(h, new BlockPos(4, 2, 4));
        c.genes.set(CowGenes.Gene.SMELL_ACUITY, 100);
        c.genes.set(CowGenes.Gene.VISION_ACUITY, a.genes.value(CowGenes.Gene.VISION_ACUITY));
        c.genes.set(CowGenes.Gene.HEARING_ACUITY, a.genes.value(CowGenes.Gene.HEARING_ACUITY));
        c.brain.load(tag);
        boolean same = c.brain.net().seed() == na.seed() && c.brain.net().save().getIntArray("W").length == na.save().getIntArray("W").length
                && java.util.Arrays.equals(c.brain.net().save().getIntArray("W"), na.save().getIntArray("W"));
        LOG.info("[тест] lobesSizesAndSave: нейронів {}, зв'язків {}, нюх (ген 100) {} / (ген 0) {}, збереження відновлено {}",
                total, na.connections(), na.size(LobeNet.Lobe.SMELL), nb.size(LobeNet.Lobe.SMELL), same);
        a.discard(); b.discard(); c.discard();
        h.assertTrue(total == LobeNet.TOTAL, "нейронів не 500: " + total);
        h.assertTrue(na.connections() > 2000 && na.connections() < 10000, "зв'язків не кілька тисяч: " + na.connections());
        h.assertTrue(na.size(LobeNet.Lobe.SMELL) > nb.size(LobeNet.Lobe.SMELL), "добрий нюх не дає більшої частки");
        h.assertTrue(same, "ваги не відновились після збереження");
        h.succeed();
    }

    // 2. Нові дії: сверблячка й паркан поруч — корова йде чесатися
    @GameTest(template = "arena", batch = "brain", timeoutTicks = 1200)
    public static void itchyCowScratches(GameTestHelper h) {
        HungryCow c = cow(h, new BlockPos(5, 2, 7));
        BlockPos fence = new BlockPos(8, 2, 7);
        h.setBlock(fence, Blocks.OAK_FENCE);
        boolean[] cleared = { false };
        Runnable clear = () -> { cleared[0] = true; c.discard(); h.setBlock(fence, Blocks.AIR); };
        h.onEachTick(() -> {
            if (h.getTick() < 20) c.touch.itch = 90;
            c.hunger = 0; c.thirst = 0; c.fear = 0;
        });
        double[] itchSeen = { 0 };
        h.runAfterDelay(1199, clear);
        h.succeedWhen(() -> {
            h.assertTrue(!cleared[0], "за 60 с не почухалась (свербіж " + Math.round(c.touch.itch) + ", мозок: " + c.brain.current().title + ")");
            h.assertTrue(h.getTick() > 20 && c.touch.itch < 5, "ще не почухалась");
            LOG.info("[тест] itchyCowScratches: почухалась на {} с", h.getTick() / 20.0);
            clear.run();
        });
    }
}

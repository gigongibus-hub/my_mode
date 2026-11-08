package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowCycle;
import com.gigongibus.hungrycows.CowInbreeding;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Спорідненість (CowInbreeding.coefficient) на вигаданих родоводах і старість (CowAging).
 * Ігрова логіка не змінюється: тест лише задає вік корови.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowInbreedingTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();
    private static final double EPS = 1e-9;

    // Вузол родоводу: id, мати, батько
    private static CompoundTag node(UUID id, CompoundTag mother, CompoundTag father) {
        CompoundTag n = new CompoundTag();
        n.putString("Name", id.toString().substring(0, 4));
        n.putUUID("Id", id);
        if (mother != null) n.put("M", mother.copy());
        if (father != null) n.put("F", father.copy());
        return n;
    }

    // 1. Формула: брат × сестра 25%, батько × дочка 25%, напів 12,5%, двоюрідні 6,25%, чужі 0
    @GameTest(template = "platform", batch = "inbreeding", timeoutTicks = 20)
    public static void kinshipCoefficients(GameTestHelper h) {
        CompoundTag granny = node(UUID.randomUUID(), null, null), grandpa = node(UUID.randomUUID(), null, null);
        CompoundTag mom = node(UUID.randomUUID(), granny, grandpa), dad = node(UUID.randomUUID(), null, null);
        CompoundTag sister = node(UUID.randomUUID(), mom, dad), brother = node(UUID.randomUUID(), mom, dad);
        CompoundTag halfBrother = node(UUID.randomUUID(), mom, node(UUID.randomUUID(), null, null));
        CompoundTag aunt = node(UUID.randomUUID(), granny, grandpa);              // сестра мами
        CompoundTag cousin = node(UUID.randomUUID(), aunt, node(UUID.randomUUID(), null, null));
        CompoundTag stranger = node(UUID.randomUUID(), null, null);
        double sib = CowInbreeding.coefficient(sister, brother);
        double fatherDaughter = CowInbreeding.coefficient(sister, dad);
        double half = CowInbreeding.coefficient(sister, halfBrother);
        double cousins = CowInbreeding.coefficient(sister, cousin);
        double none = CowInbreeding.coefficient(sister, stranger);
        LOG.info("[тест] kinshipCoefficients: брат×сестра {}, батько×дочка {}, напів {}, двоюрідні {}, чужі {}", sib, fatherDaughter, half, cousins, none);
        h.assertTrue(Math.abs(sib - 0.25) < EPS, "брат × сестра: " + sib + " (треба 0,25)");
        h.assertTrue(Math.abs(fatherDaughter - 0.25) < EPS, "батько × дочка: " + fatherDaughter + " (треба 0,25)");
        h.assertTrue(Math.abs(half - 0.125) < EPS, "напівбрат × напівсестра: " + half + " (треба 0,125)");
        h.assertTrue(Math.abs(cousins - 0.0625) < EPS, "двоюрідні: " + cousins + " (треба 0,0625)");
        h.assertTrue(none == 0, "чужі: " + none);
        h.succeed();
    }

    // 2. Старість: нова корова 100–400 діб; у 90% життя стара й не вагітніє; на межі життя помирає
    @GameTest(template = "platform", batch = "aging", timeoutTicks = 200)
    public static void oldCowDiesOfAge(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(4, 2, 4));
        cow.setAge(0);
        double life = cow.aging.lifespanDays();
        h.assertTrue(!cow.aging.old() && cow.aging.ageDays() >= 100 && cow.aging.ageDays() <= 400, "нова корова не 100–400 діб: " + cow.aging.ageDays());
        cow.aging.ageSeconds = life * 0.9 * CowCycle.SECONDS_PER_DAY;
        h.assertTrue(cow.aging.old(), "у 90% життя не стара");
        h.assertTrue(!cow.repro.isFemale() || cow.repro.isInfertile(), "стара самка ще може вагітніти");
        cow.aging.ageSeconds = life * CowCycle.SECONDS_PER_DAY - 1;   // за мить до кінця
        h.succeedWhen(() -> {
            h.assertTrue(!cow.isAlive(), "не померла від старості (вік " + Math.round(cow.aging.ageDays()) + " з " + Math.round(life) + ")");
            LOG.info("[тест] oldCowDiesOfAge: життя {} діб, померла на тіку {}", Math.round(life), h.getTick());
        });
    }
}

package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.gigongibus.hungrycows.ai.ConfrontGoal;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Протистояння (ConfrontGoal) і хвороба «кашель» (CowDisease). Ігрова логіка не змінюється: тест лише ставить корів,
 * заражає одну й запускає напад бика (force — так само, як після кидка шансу).
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowConfrontDiseaseTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String ARENA = "arena";
    private static final int SPREAD_TIMEOUT = 2400;    // 2 хв, щоб хвороба перейшла хоч на одну з 4 сусідок
    private static final int CHARGE_TIMEOUT = 300;     // 15 с: попередження 2,5 с + розгін

    // Гравець, якого можна поранити (FakePlayer за замовчуванням невразливий)
    // Здоров'я в нього не падає: «захист після появи» FakePlayer не спадає (його хід вимкнено) — тому тест запам'ятовує
    // сам удар: хто й наскільки вдарив
    private static class TestPlayer extends FakePlayer {
        Entity hitBy = null;
        float hitAmount = 0;

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (source.getEntity() != null) {
                hitBy = source.getEntity();
                hitAmount = amount;
            }
            return super.hurt(source, amount);
        }

        TestPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "confront_test"));
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }
    }

    // 1. Шанси корови з телям: невідомий 85%, відомий (1–30) 30%, довіра 100 — 5%
    @GameTest(template = "platform", batch = "confront", timeoutTicks = 20)
    public static void calfMotherChances(GameTestHelper h) {
        double stranger = ConfrontGoal.calfChanceForTrust(0), known = ConfrontGoal.calfChanceForTrust(20), best = ConfrontGoal.calfChanceForTrust(100);
        LOG.info("[тест] calfMotherChances: невідомий {}, відомий {}, довіра 100 {}", stranger, known, best);
        h.assertTrue(stranger == 0.85 && known == 0.30 && Math.abs(best - 0.05) < 1e-9, "шанси не ті");
        h.succeed();
    }

    // 2. Бик: попереджає (голова вниз) і кидається; гравець отримує удар і невидиме сповільнення
    @GameTest(template = ARENA, batch = "confront", timeoutTicks = CHARGE_TIMEOUT)
    public static void bullWarnsThenCharges(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        List<HungryCow> bulls = HungryCowBullTests.spawnSex(h, true, 1, new BlockPos(4, 2, 7));
        h.assertTrue(bulls.size() == 1, "не вийшло створити бика");
        HungryCow bull = bulls.get(0);
        TestPlayer p = new TestPlayer(h.getLevel());
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(9, 2, 7)));
        p.moveTo(at.x, at.y, at.z, 90, 0);
        p.setOnGround(true);
        h.getLevel().addNewPlayer(p);
        float health = p.getHealth();
        boolean[] sawHeadDown = { false };
        // щойно доданий гравець 3 с невразливий (захист після появи) — напад через 4 с
        h.runAfterDelay(80, () -> {
            Vec3 b = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(4, 2, 7)));   // за 4 с бик міг відійти: назад, за 5 блоків
            bull.moveTo(b.x, b.y, b.z, 270, 0);
            bull.confront.force(p);
        });
        h.onEachTick(() -> {
            if (bull.headDown) sawHeadDown[0] = true;
            p.setOnGround(true);
        });
        Runnable clear = () -> { bull.discard(); if (!p.isRemoved()) h.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED); };
        h.runAfterDelay(CHARGE_TIMEOUT - 1, clear);
        h.succeedWhen(() -> {
            h.assertTrue(sawHeadDown[0], "бик не опускав голову перед нападом");
            h.assertTrue(p.hitBy == bull, "бик не вдарив");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "немає сповільнення після удару");
            LOG.info("[тест] bullWarnsThenCharges: удар на {} с (урон {}), сповільнення {}", String.format("%.1f", h.getTick() / 20.0),
                    p.hitAmount, p.getEffect(MobEffects.MOVEMENT_SLOWDOWN).isVisible() ? "видиме" : "невидиме");
            clear.run();
        });
    }

    // 3. Кашель переходить на сусідок; хвора має жар (нездужання) і повільніша
    @GameTest(template = ARENA, batch = "disease", timeoutTicks = SPREAD_TIMEOUT)
    public static void coughSpreadsToNeighbours(GameTestHelper h) {
        h.getLevel().setDayTime(6000);
        TestWorld.calm(h);
        HungryCow sick = h.spawn(ModEntities.HUNGRY_COW.get(), new BlockPos(7, 2, 7));
        sick.setAge(0);
        List<HungryCow> others = new ArrayList<>();
        for (BlockPos b : new BlockPos[]{new BlockPos(6, 2, 6), new BlockPos(8, 2, 6), new BlockPos(6, 2, 8), new BlockPos(8, 2, 8)}) {
            HungryCow c = h.spawn(ModEntities.HUNGRY_COW.get(), b);
            c.setAge(0);
            others.add(c);
        }
        // загін 5×5 зі скла, щоб корови трималися поруч
        for (int x = 4; x <= 10; x++) for (int z = 4; z <= 10; z++) {
            if (x == 4 || x == 10 || z == 4 || z == 10) { h.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.GLASS); h.setBlock(new BlockPos(x, 3, z), net.minecraft.world.level.block.Blocks.GLASS); }
        }
        double speed = sick.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        h.assertTrue(sick.disease.infect(), "не заразилась");
        Runnable clear = () -> {
            sick.discard();
            others.forEach(HungryCow::discard);
            for (int x = 4; x <= 10; x++) for (int z = 4; z <= 10; z++) {
                h.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.AIR);
                h.setBlock(new BlockPos(x, 3, z), net.minecraft.world.level.block.Blocks.AIR);
            }
        };
        h.runAfterDelay(SPREAD_TIMEOUT - 1, clear);
        h.succeedWhen(() -> {
            long infected = others.stream().filter(c -> c.disease.sick()).count();
            h.assertTrue(infected > 0, "за 2 хв жодна сусідка не заразилась");
            h.assertTrue(sick.chem.feelUnwell() > 0, "хвора без жару (нездужання 0)");
            double slow = sick.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            h.assertTrue(slow < speed, "хвора не повільніша: " + speed + " -> " + slow);
            LOG.info("[тест] coughSpreadsToNeighbours: за {} с заразилось {} з 4, нездужання {}, швидкість {} -> {}",
                    String.format("%.0f", h.getTick() / 20.0), infected, sick.chem.feelUnwell(), String.format("%.3f", speed), String.format("%.3f", slow));
            clear.run();
        });
    }
}

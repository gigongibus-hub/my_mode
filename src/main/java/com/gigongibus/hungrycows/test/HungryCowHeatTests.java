package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowHeat;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Тести перегрівання (CowHeat). Сонця в тестовому світі немає (світло неба не пораховане, inSun() завжди false),
 * тому тут перевіряється лише те, що від сонця не залежить: охолодження у воді й у тіні, втрата тями при 100,
 * порятунок відром води, прихід до тями, коли охолола. Ігрова логіка не підмінюється: тест лише задає шкалу.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowHeatTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String ARENA = "arena";
    private static final String BATCH = "heat";
    private static final long NOON = 6000;
    private static final BlockPos CENTER = new BlockPos(7, 2, 7);
    // 1. Вода: −5 кожні 5 с (−1/с), тінь −1 кожні 5 с — за 10 с у воді має спасти щонайменше на 6 (у тіні було б лише 2)
    private static final double WATER_START = 60;
    private static final int WATER_WAIT = 200;
    private static final double WATER_MIN_DROP = 6;
    // 2. Без тями: стоїть на місці; відро води → −50, до тями, порожнє відро, довіра росте
    private static final int STILL_TICKS = 100;                // 5 с без тями — не відходить
    private static final double STILL_MAX_MOVE = 0.3;
    private static final int RESCUE_TIMEOUT = 400;
    // 3. У тіні охолоняє з 100 до 80 за ~100 с і приходить до тями ще до шкоди (шкода з 120 с)
    private static final int WAKE_TIMEOUT = 2600;

    private static HungryCow spawnCow(GameTestHelper h, BlockPos pos) {
        h.getLevel().setDayTime(NOON);
        TestWorld.calm(h, false);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), pos);
        cow.setAge(0);
        return cow;
    }

    private static ServerPlayer makePlayer(GameTestHelper h, BlockPos at) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "heat_test"));
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        p.setOnGround(true);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void removePlayer(GameTestHelper h, ServerPlayer p) {
        if (!p.isRemoved()) h.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }

    // ---------- 1. У воді перегрівання спадає швидко ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = WATER_WAIT + 100)
    public static void heatCoolsInWater(GameTestHelper h) {
        // басейн 5×5 глибиною 1 (вода на підлозі), навколо скло у 2 блоки — корова не вийде
        for (int x = 4; x <= 10; x++) {
            for (int z = 4; z <= 10; z++) {
                boolean wall = x == 4 || x == 10 || z == 4 || z == 10;
                h.setBlock(new BlockPos(x, 2, z), wall ? Blocks.GLASS : Blocks.WATER);
                if (wall) h.setBlock(new BlockPos(x, 3, z), Blocks.GLASS);
            }
        }
        HungryCow cow = spawnCow(h, CENTER);
        cow.heat.heat = WATER_START;
        h.runAfterDelay(WATER_WAIT, () -> {
            double drop = WATER_START - cow.heat.heat;
            LOG.info("[тест] heatCoolsInWater: за {} с у воді перегрівання {} -> {} (у воді: {})", WATER_WAIT / 20,
                    WATER_START, Math.round(cow.heat.heat), cow.isInWater());
            // прибрати басейн і корову: споруди лишаються у світі під майданчиками наступних партій
            cow.discard();
            for (int x = 4; x <= 10; x++) {
                for (int z = 4; z <= 10; z++) {
                    h.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
                    h.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
                }
            }
            h.assertTrue(drop >= WATER_MIN_DROP, "у воді перегрівання спало лише на " + Math.round(drop) + " (треба " + WATER_MIN_DROP + "+)");
            h.succeed();
        });
    }

    // ---------- 2. Перегрівання 100 → без тями, не рухається; відро води рятує ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = RESCUE_TIMEOUT)
    public static void unconsciousCowRescuedWithBucket(GameTestHelper h) {
        HungryCow cow = spawnCow(h, CENTER);
        cow.heat.heat = CowHeat.MAX;
        cow.hunger = 40;   // голодна: без тями однаково не йде їсти
        Vec3[] at = new Vec3[1];
        ServerPlayer[] player = new ServerPlayer[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(cow.heat.unconscious, "при перегріванні 100 корова не знепритомніла"))
                .thenExecute(() -> at[0] = cow.position())
                .thenIdle(STILL_TICKS)
                .thenExecute(() -> {
                    double moved = cow.position().distanceTo(at[0]);
                    h.assertTrue(cow.heat.unconscious, "прийшла до тями сама за 5 с (перегрівання " + Math.round(cow.heat.heat) + ")");
                    h.assertTrue(moved <= STILL_MAX_MOVE, "без тями відійшла на " + String.format("%.2f", moved) + " бл.");
                    player[0] = makePlayer(h, new BlockPos(7, 2, 9));
                    player[0].setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
                    int trustBefore = cow.realTrustOf(player[0]);
                    double before = cow.heat.heat;
                    cow.mobInteract(player[0], InteractionHand.MAIN_HAND);
                    int trustAfter = cow.realTrustOf(player[0]);
                    ItemStack hand = player[0].getItemInHand(InteractionHand.MAIN_HAND);
                    LOG.info("[тест] unconsciousCowRescuedWithBucket: стояла {} бл., перегрівання {} -> {}, до тями: {}, довіра {} -> {}, у руці {}",
                            String.format("%.2f", moved), Math.round(before), Math.round(cow.heat.heat), !cow.heat.unconscious, trustBefore, trustAfter, hand);
                    removePlayer(h, player[0]);
                    h.assertTrue(!cow.heat.unconscious, "після відра води корова без тями (перегрівання " + Math.round(cow.heat.heat) + ")");
                    h.assertTrue(cow.heat.heat <= before - CowHeat.BUCKET_COOL + 0.01, "відро не охолодило на " + CowHeat.BUCKET_COOL);
                    h.assertTrue(hand.is(Items.BUCKET), "відро не стало порожнім: " + hand);
                    h.assertTrue(trustAfter > trustBefore, "довіра не виросла: " + trustBefore + " -> " + trustAfter);
                })
                .thenExecute(cow::discard)
                .thenSucceed();
    }

    // ---------- 3. Без тями в тіні: охолоняє й приходить до тями сама, ще до шкоди ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = WAKE_TIMEOUT)
    public static void unconsciousCowWakesInShade(GameTestHelper h) {
        // справжня тінь: дах над коровою (відколи ділянки довкола активні, гра рахує світло неба — інакше корова на сонці)
        for (int x = 5; x <= 9; x++) {
            for (int z = 5; z <= 9; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
        }
        h.runAfterDelay(WAKE_TIMEOUT - 1, () -> {
            for (int x = 5; x <= 9; x++) for (int z = 5; z <= 9; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
        });
        HungryCow cow = spawnCow(h, CENTER);
        cow.heat.heat = CowHeat.MAX;
        float health = cow.getHealth();
        int[] outAt = { -1 };
        h.onEachTick(() -> { if (outAt[0] < 0 && cow.heat.unconscious) outAt[0] = (int) h.getTick(); });
        h.succeedWhen(() -> {
            h.assertTrue(cow.isAlive(), "корова загинула");
            h.assertTrue(outAt[0] >= 0, "не знепритомніла");
            h.assertTrue(!cow.heat.unconscious, "ще без тями (перегрівання " + Math.round(cow.heat.heat) + ")");
            LOG.info("[тест] unconsciousCowWakesInShade: прийшла до тями через {} с, перегрівання {}, здоров'я {} -> {}",
                    (h.getTick() - outAt[0]) / 20, Math.round(cow.heat.heat), health, cow.getHealth());
            h.assertTrue(cow.getHealth() >= health, "охолоняючи в тіні, втратила здоров'я: " + health + " -> " + cow.getHealth());
            cow.discard();
            for (int x = 5; x <= 9; x++) for (int z = 5; z <= 9; z++) h.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
        });
    }
}

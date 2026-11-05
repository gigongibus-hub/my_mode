package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Тести страху (Forge GameTest), окрема партія "fear", щоб тестовий гравець не заважав іншим тестам.
 * Майданчик: data/hungrycows/structures/arena.nbt — камінь 15×15, скляні стіни у 2 блоки (є куди тікати).
 *
 * Механіка страху не змінюється: тест лише ставить корову й тестового гравця, переставляє гравця й (у тесті 3) б'є.
 *
 * Тестовий гравець — TestPlayer (Forge FakePlayer, доданий у світ через ServerLevel.addNewPlayer).
 * Ванільний makeMockServerPlayerInLevel() під Forge 47 падає (Connection.channel() = null). Обмеження:
 *  - немає фізики: «на землі» завжди false, а корова вважає гравця не на землі таким, що СТРИБАЄ (страх 35 замість 3).
 *    Тому після кожного переміщення тест ставить йому onGround = true (стан гравця, не корови);
 *  - не ходить сам: «ходьба» = переставлення на 1 блок щосекунди. Корова рахує рух гравця раз на секунду, тож для неї
 *    це ходьба 1 блок/с, але плавного руху між секундами немає; біг (isSprinting) не перевіряється;
 *  - звичайний FakePlayer невразливий; TestPlayer — ні, щоб було видно, чи корова справді вдарила (лише в тесті).
 *
 * Що механіка обіцяє (і що тест вимагає):
 *  1) підхід — страх росте (вимагає); втеча ймовірнісна: мозок вибирає «стерегтися» за балами, тож при страху біля
 *     порогу корова може й не тікати (не вимагає, лише записує; вимагав би при паніці 80+, але від ходьби її немає);
 *  2) раптова поява — страх ≈30–40, набагато вище порогу 5–15: має тікати (вимагає);
 *  3) удар — довіра падає, страх різко росте; ворожа корова тікає або б'є кривдника (вимагає одне з двох).
 * Усі числа зібрані на початку файлу.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowFearTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String ARENA = "arena";
    private static final String BATCH = "fear";
    private static final long NOON = 6000;
    private static final BlockPos CENTER = new BlockPos(7, 2, 7);   // y=1 — підлога
    private static final int SECOND = 20;
    private static final double FLED_BY = 1.5;        // «втекла» = відійшла від гравця щонайменше на 1,5 блока
    private static final int FLEE_WAIT = 3 * SECOND;  // після останнього руху гравця чекаємо 3 с
    // 1. Підхід: від 6 блоків до 2, щосекунди на 1 блок ближче до корови (туди, де вона зараз), потім стоїть
    private static final int APPROACH_FROM = 6;
    private static final int APPROACH_TO = 2;
    private static final int APPROACH_TIMEOUT = 400;
    // 2. Раптова поява: 2 с за 12 блоків, потім одразу за 1,5 блока
    private static final double FAR = 12;
    private static final double NEAR = 1.5;
    private static final int SUDDEN_TIMEOUT = 300;
    // 3. Удар: 2 с за 2 блоки, потім удар рукою
    private static final double HIT_DISTANCE = 2;
    private static final int HIT_TIMEOUT = 300;

    // ---------- тестовий гравець ----------

    // FakePlayer, якого можна поранити (щоб бачити, чи корова вдарила). Лише для тестів.
    private static class TestPlayer extends FakePlayer {
        Entity hurtBy = null;   // хто останнім завдав урону

        TestPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "fear_test"));
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            boolean r = super.hurt(source, amount);
            if (r) hurtBy = source.getEntity();
            return r;
        }
    }

    private static TestPlayer makePlayer(GameTestHelper h) {
        TestPlayer p = new TestPlayer(h.getLevel());
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void removePlayer(GameTestHelper h, ServerPlayer p) {
        if (!p.isRemoved()) h.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }

    // Поставити гравця в точку (на висоті корови), обличчям до корови
    private static void placePlayer(ServerPlayer p, HungryCow cow, Vec3 at) {
        p.moveTo(at.x, cow.getY(), at.z, 0, 0);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
        p.setOnGround(true);   // див. обмеження вгорі: інакше корова вважала б, що гравець стрибає
    }

    private static HungryCow spawnCow(GameTestHelper h) {
        h.getLevel().setDayTime(NOON);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), CENTER);
        cow.setAge(0);
        return cow;
    }

    private static boolean fearGoalRunning(HungryCow cow) {
        return cow.isDoing(CowBrain.Action.WARY);   // працює ціль страху (FearGoal)
    }

    private static String state(HungryCow cow, ServerPlayer p) {
        return String.format("страх %d (відчуття %d, поріг %d), довіра %d, відстань %.1f, ціль страху %s",
                cow.fear, cow.chem.fearFelt(), cow.scaredAt(), cow.trustOf(p), cow.distanceTo(p), fearGoalRunning(cow) ? "так" : "ні");
    }

    // Спостереження щотіку: найбільший страх, чи доходило відчуття до порогу, коли вперше почалась ціль страху,
    // найменша відстань до гравця і найбільша після неї (втеча = відійшла на FLED_BY від найближчої точки)
    private static class Watch {
        int maxFear = 0;
        boolean reachedThreshold = false;
        int fearGoalTick = -1;
        double minDist = Double.MAX_VALUE;
        double maxDistAfterMin = 0;

        void tick(GameTestHelper h, HungryCow cow, ServerPlayer p) {
            maxFear = Math.max(maxFear, cow.fear);
            if (cow.chem.fearFelt() >= cow.scaredAt()) reachedThreshold = true;
            if (fearGoalTick < 0 && fearGoalRunning(cow)) fearGoalTick = (int) h.getTick();
            double d = cow.distanceTo(p);
            if (d < minDist) {
                minDist = d;
                maxDistAfterMin = d;
            }
            maxDistAfterMin = Math.max(maxDistAfterMin, d);
        }

        boolean fled() {
            return fearGoalTick >= 0 && maxDistAfterMin - minDist >= FLED_BY;
        }

        String text() {
            return String.format("страх найбільший %d, поріг досягнуто: %s, ціль страху: %s, відстань найменша %.1f -> потім найбільша %.1f, втекла: %s",
                    maxFear, reachedThreshold ? "так" : "ні", fearGoalTick >= 0 ? "з тіку " + fearGoalTick : "ні",
                    minDist, maxDistAfterMin, fled() ? "так" : "ні");
        }
    }

    // ---------- 1. Гравець підходить ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = APPROACH_TIMEOUT)
    public static void fearFromApproachingPlayer(GameTestHelper h) {
        HungryCow cow = spawnCow(h);
        TestPlayer p = makePlayer(h);
        Vec3 dir = new Vec3(0, 0, -1);
        Watch w = new Watch();          // страх і ціль страху — за весь тест
        Watch afterStop = new Watch();  // втеча — після того, як гравець зупинився
        boolean[] stopped = { false };
        placePlayer(p, cow, cow.position().add(dir.scale(APPROACH_FROM)));
        int startFear = cow.fear;
        h.onEachTick(() -> {
            w.tick(h, cow, p);
            if (stopped[0]) afterStop.tick(h, cow, p);
        });
        var seq = h.startSequence().thenIdle(SECOND);   // корова запам'ятовує, де гравець
        for (int d = APPROACH_FROM - 1; d >= APPROACH_TO; d--) {
            final double dist = d;
            // крок до корови: з того боку, де гравець зараз, на dist від корови
            seq = seq.thenExecute(() -> {
                Vec3 from = p.position().subtract(cow.position());
                Vec3 side = new Vec3(from.x, 0, from.z).lengthSqr() < 1.0E-4 ? dir : new Vec3(from.x, 0, from.z).normalize();
                placePlayer(p, cow, cow.position().add(side.scale(dist)));
            }).thenIdle(SECOND);
        }
        seq.thenExecute(() -> stopped[0] = true)
                .thenIdle(FLEE_WAIT)
                .thenExecute(() -> {
                    afterStop.fearGoalTick = w.fearGoalTick;   // ціль страху могла початись ще під час підходу
                    LOG.info("[тест] fearFromApproachingPlayer: страх на початку {}; {}; після зупинки гравця: відстань {} -> найбільша {}, втекла: {}",
                            startFear, w.text(), String.format("%.1f", afterStop.minDist), String.format("%.1f", afterStop.maxDistAfterMin),
                            afterStop.fled() ? "так" : "ні");
                    removePlayer(h, p);
                    h.assertTrue(w.maxFear > startFear, "страх не зріс, коли гравець підходив: " + w.text());
                    if (w.maxFear >= HungryCow.PANIC_AT) {   // паніка — рефлекс, тоді втеча обов'язкова
                        h.assertTrue(afterStop.fled(), "паніка, але корова не втекла: " + w.text());
                    }
                })
                .thenSucceed();
        h.runAfterDelay(APPROACH_TIMEOUT - 1, () -> removePlayer(h, p));
    }

    // ---------- 2. Раптова поява поруч ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = SUDDEN_TIMEOUT)
    public static void fearFromSuddenPlayer(GameTestHelper h) {
        HungryCow cow = spawnCow(h);
        TestPlayer p = makePlayer(h);
        Vec3 dir = new Vec3(0, 0, -1);
        Watch w = new Watch();
        placePlayer(p, cow, cow.position().add(dir.scale(FAR)));
        int startFear = cow.fear;
        h.onEachTick(() -> w.tick(h, cow, p));
        h.startSequence()
                .thenIdle(2 * SECOND)
                .thenExecute(() -> placePlayer(p, cow, cow.position().add(dir.scale(NEAR))))
                .thenWaitUntil(() -> h.assertTrue(w.reachedThreshold, "гравець раптом поруч, а страх не дійшов до порогу: " + state(cow, p)))
                .thenWaitUntil(() -> h.assertTrue(w.fearGoalTick >= 0, "страх вище порогу, але ціль страху не почалась: " + state(cow, p)))
                .thenWaitUntil(() -> h.assertTrue(w.fled(), "ціль страху почалась, але корова не відійшла від гравця: " + w.text()))
                .thenExecute(() -> {
                    LOG.info("[тест] fearFromSuddenPlayer: страх на початку {}; {}", startFear, w.text());
                    removePlayer(h, p);
                })
                .thenSucceed();
        h.runAfterDelay(SUDDEN_TIMEOUT - 1, () -> removePlayer(h, p));
    }

    // ---------- 3. Удар ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = HIT_TIMEOUT)
    public static void fearFromHit(GameTestHelper h) {
        HungryCow cow = spawnCow(h);
        TestPlayer p = makePlayer(h);
        Watch w = new Watch();
        placePlayer(p, cow, cow.position().add(new Vec3(0, 0, -HIT_DISTANCE)));
        int[] before = new int[2];   // страх і довіра перед ударом
        h.startSequence()
                .thenIdle(2 * SECOND)
                .thenExecute(() -> {
                    before[0] = cow.fear;
                    before[1] = cow.trustOf(p);
                    h.onEachTick(() -> w.tick(h, cow, p));   // відстань рахуємо від моменту удару
                    p.attack(cow);   // справжній удар гравця рукою
                })
                .thenWaitUntil(() -> h.assertTrue(cow.fear > before[0], "після удару страх не зріс: " + state(cow, p)))
                .thenWaitUntil(() -> h.assertTrue(w.fearGoalTick >= 0, "після удару ціль страху не почалась: " + state(cow, p)))
                .thenIdle(FLEE_WAIT)
                .thenExecute(() -> {
                    boolean kicked = p.hurtBy == cow;
                    LOG.info("[тест] fearFromHit: страх {} -> {}, довіра {} -> {}, корова вдарила гравця: {} (складність світу {}); {}",
                            before[0], w.maxFear, before[1], cow.trustOf(p), kicked ? "так" : "ні", h.getLevel().getDifficulty(), w.text());
                    removePlayer(h, p);
                    h.assertTrue(cow.trustOf(p) < before[1], "після удару довіра не впала: " + state(cow, p));
                    h.assertTrue(w.fled() || kicked, "після удару корова не втекла й не вдарила гравця: " + w.text());
                })
                .thenSucceed();
        h.runAfterDelay(HIT_TIMEOUT - 1, () -> removePlayer(h, p));
    }
}

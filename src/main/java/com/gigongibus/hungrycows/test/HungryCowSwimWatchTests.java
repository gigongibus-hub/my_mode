package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.CowCharacter.Trait;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModEntities;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Тести плавання й погляду (Forge GameTest), окрема партія "swim". Майданчик arena.nbt (15×15), на якому тест
 * будує ставок: земля піднята, посередині ставок 7×7 (для випливу — глибиною 2, для утоплення — 3). На 1 блоці корова
 * стоїть на дні з головою над водою — бреде, а не пливе, і втопитися не може (так і має бути).
 * Механіка не змінюється: тест ставить корову у воду, задає початкову втому, у тесті 2 — бере корову на повідок
 * (як гравець) і відкриває прохід; у тестах погляду ставить вовка без ШІ або гравця-друга.
 * Усі числа зібрані на початку файлу.
 */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowSwimWatchTests {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final String ARENA = "arena";
    private static final String BATCH = "swim";
    private static final long NOON = 6000;
    private static final int GROUND = 2;                       // піднята земля (y=1 — підлога майданчика)
    private static final int POOL_MIN = 4, POOL_MAX = 10;      // ставок 7×7
    private static final int SHORE_DEPTH = 2;                  // глибина ставка для тесту випливу (на 1 блоці корова бреде, а не пливе)
    private static final BlockPos IN_POOL = new BlockPos(7, GROUND + SHORE_DEPTH - 1, 7);
    private static final int DEEP = 3;                         // глибина ставка для тесту утоплення
    // 1. Виплив до берега
    private static final int SHORE_TIMEOUT = 600;
    // 2. Тоне й рятує гравець
    private static final int TIRED_START = 55;                 // початкова втома: до 70 (тоне) ≈ 5 с у воді
    private static final int SINK_FEAR_AFTER = 7 * 20;         // після 5 с у воді страх має рости — перевіряємо на 7-й с
    private static final BlockPos RESCUER = new BlockPos(13, GROUND + DEEP, 7);              // на березі
    private static final BlockPos IN_POOL_WEST = new BlockPos(5, GROUND + DEEP - 1, 7);     // 8 блоків від гравця: повідок тягне (далі 6)
    private static final int RESCUE_TIMEOUT = 1200;
    // 3–4. Погляд
    private static final double LOOKING_AT = 25;               // «дивиться» = голова повернута до цілі точніше 25°
    private static final int DANGER_TIMEOUT = 100;             // небезпеку помічає за 5 с
    private static final int FRIEND_WATCH_TICKS = 600;         // за 30 с ...
    // ... хоч раз дивиться на друга довше 25 тіків поспіль: механіка дає ≥45 тіків погляду, але тест рахує лише тіки,
    // коли голова вже повернута точніше 25°, а спокійний поворот (10°/тік) займає до 18 тіків
    private static final int FRIEND_MIN_STREAK = 25;

    // ---------- допоміжне ----------

    private static HungryCow spawnCow(GameTestHelper h, BlockPos pos) {
        h.getLevel().setDayTime(NOON);
        TestWorld.calm(h, false);
        HungryCow cow = h.spawn(ModEntities.HUNGRY_COW.get(), pos);
        cow.setAge(0);
        return cow;
    }

    // Ставок: земля на рівні GROUND, посередині вода; стіни майданчика надбудовано, щоб корова не вилізла
    private static void buildPond(GameTestHelper h, int depth, boolean trapped) {
        int top = GROUND + depth;   // рівень, на якому стоять на березі
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z <= 13; z++) {
                boolean pool = x >= POOL_MIN && x <= POOL_MAX && z >= POOL_MIN && z <= POOL_MAX;
                for (int y = GROUND; y < top; y++) h.setBlock(new BlockPos(x, y, z), pool ? Blocks.WATER : Blocks.STONE);
                // пастка: скло навколо ставка (на березі), щоб корова не могла вийти
                boolean rim = trapped && !pool && x >= POOL_MIN - 1 && x <= POOL_MAX + 1 && z >= POOL_MIN - 1 && z <= POOL_MAX + 1;
                if (rim) {
                    h.setBlock(new BlockPos(x, top, z), Blocks.GLASS);
                    h.setBlock(new BlockPos(x, top + 1, z), Blocks.GLASS);
                }
            }
        }
        for (int i = 0; i <= 14; i++) {   // зовнішні стіни: 2 блоки над берегом
            for (int y = 3; y <= top + 1; y++) {
                h.setBlock(new BlockPos(i, y, 0), Blocks.GLASS);
                h.setBlock(new BlockPos(i, y, 14), Blocks.GLASS);
                h.setBlock(new BlockPos(0, y, i), Blocks.GLASS);
                h.setBlock(new BlockPos(14, y, i), Blocks.GLASS);
            }
        }
    }

    // Прибрати ставок і скло після тесту: споруди лишаються у світі, і майданчики наступних партій (великий хлів) стоять
    // на них — у залишеному ставку тонули корови з інших тестів
    private static void clearPond(GameTestHelper h, Entity... entities) {
        for (Entity e : entities) if (e != null && !e.isRemoved()) e.discard();
        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) {
                for (int y = GROUND; y <= GROUND + DEEP + 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
    }

    private static boolean underwater(HungryCow cow) {
        return cow.isEyeInFluid(FluidTags.WATER);
    }

    // На скільки градусів голова корови відвернута від цілі
    private static double lookError(HungryCow cow, Entity target) {
        double dx = target.getX() - cow.getX(), dz = target.getZ() - cow.getZ();
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double d = (cow.getYHeadRot() - yaw) % 360.0;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return Math.abs(d);
    }

    // Тестовий гравець (FakePlayer у світі) — див. HungryCowFearTests
    private static ServerPlayer makePlayer(GameTestHelper h, BlockPos at) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "swim_test"));
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(at));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        p.setOnGround(true);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void removePlayer(GameTestHelper h, ServerPlayer p) {
        if (!p.isRemoved()) h.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }

    // ---------- 1. Корова у воді пливе до берега й виходить ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = SHORE_TIMEOUT)
    public static void cowSwimsToShore(GameTestHelper h) {
        buildPond(h, SHORE_DEPTH, false);
        HungryCow cow = spawnCow(h, IN_POOL);
        int startFatigue = cow.fatigue;
        h.succeedWhen(() -> {
            h.assertTrue(cow.isAlive(), "корова загинула");
            var path = cow.getNavigation().getPath();
            h.assertTrue(!cow.isInWater() && cow.onGround(), String.format("ще у воді: утома %d, страх %d, місце %.2f %.2f %.2f, шлях %s, [%s]",
                    cow.fatigue, cow.fear, cow.getX(), cow.getY(), cow.getZ(),
                    path == null ? "немає" : path.getNextNodeIndex() + "/" + path.getNodeCount() + " до " + path.getTarget().toShortString()
                            + (path.canReach() ? "" : " (не дійде)") + (cow.getNavigation().isDone() ? " завершено" : ""),
                    cow.getCustomName() == null ? "" : cow.getCustomName().getString()));
            LOG.info("[тест] cowSwimsToShore: вийшла на берег за {} с, утома {} -> {}", String.format("%.1f", h.getTick() / 20.0),
                    startFatigue, cow.fatigue);
            clearPond(h, cow);
        });
        h.runAfterDelay(SHORE_TIMEOUT - 1, () -> clearPond(h, cow));
    }

    // ---------- 2. Утомлена корова тоне; гравець на повідку витягає її — довіра й прив'язаність ростуть ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = RESCUE_TIMEOUT)
    public static void tiredCowSinksAndIsRescued(GameTestHelper h) {
        buildPond(h, DEEP, true);
        HungryCow cow = spawnCow(h, IN_POOL_WEST);
        cow.fatigue = TIRED_START;
        int[] before = new int[3];   // утома, довіра, прив'язаність
        ServerPlayer[] p = new ServerPlayer[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(h.getTick() >= SINK_FEAR_AFTER, "чекаємо"))
                .thenExecute(() -> h.assertTrue(cow.fear > 0, String.format("після 5+ с у воді страх не росте: страх %d, утома %d, у воді %s, на дні %s, голова під водою %s, місце %.2f %.2f %.2f, [%s]",
                        cow.fear, cow.fatigue, cow.isInWater(), cow.onGround(), underwater(cow), cow.getX(), cow.getY(), cow.getZ(),
                        cow.getCustomName() == null ? "" : cow.getCustomName().getString())))
                .thenWaitUntil(() -> h.assertTrue(cow.fatigue >= 70 && underwater(cow),
                        String.format("не тоне: утома %d, під водою %s", cow.fatigue, underwater(cow))))
                .thenExecute(() -> {
                    LOG.info("[тест] tiredCowSinksAndIsRescued: тоне на {} с (утома {}, страх {}, повітря {})",
                            String.format("%.1f", h.getTick() / 20.0), cow.fatigue, cow.fear, cow.getAirSupply());
                    // гравець на березі бере корову на повідок і відкриває прохід у склі
                    for (int z = 6; z <= 8; z++) {
                        h.setBlock(new BlockPos(POOL_MAX + 1, GROUND + DEEP, z), Blocks.AIR);
                        h.setBlock(new BlockPos(POOL_MAX + 1, GROUND + DEEP + 1, z), Blocks.AIR);
                    }
                    p[0] = makePlayer(h, RESCUER);
                    before[1] = cow.trustOf(p[0]);
                    before[2] = cow.character.get(Trait.HUMAN_ATTACHMENT);
                    cow.setLeashedTo(p[0], true);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(cow.isAlive(), "корова втопилась (від " + (cow.getLastDamageSource() == null ? "?" : cow.getLastDamageSource().getMsgId())
                            + ", напис [" + (cow.getCustomName() == null ? "" : cow.getCustomName().getString()) + "])");
                    h.assertTrue(cow.trustOf(p[0]) > before[1], String.format("ще не врятована: у воді %s, на повідку %s, довіра %d",
                            cow.isInWater(), cow.isLeashed(), cow.trustOf(p[0])));
                })
                .thenExecute(() -> {
                    int att = cow.character.get(Trait.HUMAN_ATTACHMENT);
                    LOG.info("[тест] tiredCowSinksAndIsRescued: врятована на {} с; довіра {} -> {}, прив'язаність до людей {} -> {}",
                            String.format("%.1f", h.getTick() / 20.0), before[1], cow.trustOf(p[0]), before[2], att);
                    removePlayer(h, p[0]);
                    clearPond(h, cow);
                    h.assertTrue(att > before[2], "прив'язаність до людей не зросла");
                })
                .thenSucceed();
        h.runAfterDelay(RESCUE_TIMEOUT - 1, () -> { if (p[0] != null) removePlayer(h, p[0]); clearPond(h, cow); });
    }

    // ---------- 3. Небезпеку (вовк) корова помічає й дивиться на неї ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = DANGER_TIMEOUT)
    public static void cowWatchesDanger(GameTestHelper h) {
        HungryCow cow = spawnCow(h, new BlockPos(7, 2, 9));
        Wolf wolf = h.spawn(EntityType.WOLF, new BlockPos(7, 2, 3));
        wolf.setNoAi(true);   // вовк стоїть (тест перевіряє погляд корови, а не вовка)
        h.succeedWhen(() -> {
            double err = lookError(cow, wolf);
            h.assertTrue(err < LOOKING_AT, String.format("не дивиться на вовка: відхилення %.0f°", err));
            LOG.info("[тест] cowWatchesDanger: дивиться на вовка через {} тіків (відхилення {}°)", h.getTick(), Math.round(err));
            cow.discard();
            wolf.discard();
        });
        h.runAfterDelay(DANGER_TIMEOUT - 1, () -> { cow.discard(); wolf.discard(); });
    }

    // ---------- 4. На друга корова дивиться довго й спокійно ----------
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = FRIEND_WATCH_TICKS + 100)
    public static void cowWatchesFriendLong(GameTestHelper h) {
        HungryCow cow = spawnCow(h, new BlockPos(7, 2, 9));
        ServerPlayer p = makePlayer(h, new BlockPos(7, 2, 5));
        cow.addTrust(p, 100, "тест: друг", false);
        int[] streak = { 0, 0 };   // поточна і найдовша серія тіків погляду на друга
        h.onEachTick(() -> {
            if (lookError(cow, p) < LOOKING_AT) streak[0]++;
            else streak[0] = 0;
            streak[1] = Math.max(streak[1], streak[0]);
        });
        h.startSequence()
                .thenIdle(FRIEND_WATCH_TICKS)
                .thenExecute(() -> {
                    LOG.info("[тест] cowWatchesFriendLong: найдовше дивилась на друга {} с поспіль (довіра {})",
                            String.format("%.1f", streak[1] / 20.0), cow.trustOf(p));
                    removePlayer(h, p);
                    cow.discard();   // інакше вона — «стадо» для корів наступних тестів
                    h.assertTrue(streak[1] >= FRIEND_MIN_STREAK, "за 30 с жодного разу не дивилась на друга довго (найдовше "
                            + streak[1] + " тіків)");
                })
                .thenSucceed();
    }
}

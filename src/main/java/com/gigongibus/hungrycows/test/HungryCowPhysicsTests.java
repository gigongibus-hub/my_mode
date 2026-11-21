package com.gigongibus.hungrycows.test;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.chop.Chopping;
import com.gigongibus.hungrycows.chop.WoodPiece;
import com.gigongibus.hungrycows.chop.WoodPileBlockEntity;
import com.gigongibus.hungrycows.phys.FallenLogs;
import com.gigongibus.hungrycows.phys.Physics;
import com.gigongibus.hungrycows.phys.WoodBodyEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.slf4j.Logger;

/** Фізика твердих тіл (Fij, 20.11): шматки падають, перекидаються, лягають купою; розрубані розлітаються; повалена колода від удару — знову фізична */
@GameTestHolder(HungryCowsMod.MODID)
@PrefixGameTestTemplate(false)
public class HungryCowPhysicsTests {
    private static final Logger LOG = LogUtils.getLogger();

    static WoodPiece half() {
        WoodPiece p = WoodPiece.whole(Direction.Axis.X, true, false, WoodPiece.OAK);
        p.sz = 8;
        return p;
    }

    static double lowest(WoodBodyEntity e) {
        return e.body.aabb()[1];
    }

    // 1. Половинка падає з 3 бл. з обертанням: б'ється об підлогу, перекидається, лягає (нижче не провалюється), стає купою
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 240)
    public static void pieceFallsAndRests(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos at = h.absolutePos(new BlockPos(7, 5, 7));
        double floor = h.absolutePos(new BlockPos(7, 2, 7)).getY();
        var e = WoodBodyEntity.spawnPiece(h.getLevel(), half(), new Vector3d(at.getX(), at.getY(), at.getZ()), new Vector3d(0.5, 0, 0), new Vector3d(1.5, 0.3, 2));
        double[] minY = { 99 };
        int[] sleptAt = { -1 };
        long start = h.getTick();
        h.onEachTick(() -> {
            if (e.isRemoved()) return;
            minY[0] = Math.min(minY[0], lowest(e));
            if (sleptAt[0] < 0 && e.body.asleep) sleptAt[0] = (int) (h.getTick() - start);
        });
        h.runAfterDelay(200, () -> {
            int pieces = 0;
            BlockPos pile = null;
            for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) for (int y = 2; y < 4; y++)
                if (h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x, y, z))) instanceof WoodPileBlockEntity be) { pieces += be.pieces().size(); pile = new BlockPos(x, y, z); }
            LOG.info("[тест] pieceFallsAndRests: найнижче {} (підлога {}), заснула за {} тіків, сутність {}, купа {} з {} шматків",
                    minY[0], floor, sleptAt[0], e.isRemoved() ? "зникла" : "є", pile, pieces);
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(minY[0] > floor - 0.12, "провалилась у підлогу: " + minY[0]);
            h.assertTrue(sleptAt[0] > 5 && sleptAt[0] < 120, "не лягла: " + sleptAt[0]);
            h.assertTrue(e.isRemoved() && pieces == 1 && pile.getY() == 2, "не стала купою на підлозі");
            h.succeed();
        });
    }

    // 2. Шматок котиться зі сходинки: поштовх убік — перекидається через край і падає нижче
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 200)
    public static void pieceTipsOffStep(GameTestHelper h) {
        TestWorld.calm(h);
        for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        BlockPos at = h.absolutePos(new BlockPos(4, 3, 2));
        var e = WoodBodyEntity.spawnPiece(h.getLevel(), half(), new Vector3d(at.getX(), at.getY() + 0.01, at.getZ()), new Vector3d(1.5, 0, 0), new Vector3d());
        double top = at.getY();
        double[] last = { 99 };
        StringBuilder tr = new StringBuilder();
        h.onEachTick(() -> {
            if (e.isRemoved()) return;
            last[0] = lowest(e);
            if (e.tickCount % 5 == 0) tr.append(String.format(" [%d: %.2f %.2f %.2f]", e.tickCount, e.body.pos.x - at.getX(), last[0] - top, e.body.pos.z - at.getZ()));
        });
        h.runAfterDelay(100, () -> {
            LOG.info("[тест] pieceTipsOffStep: найнижча точка {} (сходинка {}){}", last[0], top, tr);
            HungryCowFellTests.clear(h, 0);
            for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            h.assertTrue(last[0] < top - 0.6, "не впала зі сходинки: " + last[0]);
            h.succeed();
        });
    }

    // 3. Дві половинки одна на одну: верхня лягає на нижню (не провалюється крізь неї)
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 200)
    public static void piecesStack(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos a = h.absolutePos(new BlockPos(10, 2, 10));
        var low = WoodBodyEntity.spawnPiece(h.getLevel(), half(), new Vector3d(a.getX(), a.getY() + 0.02, a.getZ()), new Vector3d(), new Vector3d());
        var high = WoodBodyEntity.spawnPiece(h.getLevel(), half(), new Vector3d(a.getX() + 0.1, a.getY() + 2, a.getZ() + 0.05), new Vector3d(), new Vector3d(0, 0.5, 0));
        double[] hy = { 0 }, ly = { 0 };
        h.runAfterDelay(50, () -> { hy[0] = lowest(high); ly[0] = low.body.aabb()[4]; });
        h.runAfterDelay(160, () -> {
            LOG.info("[тест] piecesStack: верхня найнижче {}, нижня згори {}", hy[0], ly[0]);
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(hy[0] > ly[0] - 0.12, "верхня провалилась крізь нижню: " + hy[0] + " < " + ly[0]);
            h.succeed();
        });
    }

    // 4. Розрубали колоду на землі: половинки розлітаються тілами (Fij «А»), потім лягають купою; ЛКМ по шматку — штовхає, ПКМ — забрати
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 260)
    public static void chopFliesApart(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos log = new BlockPos(7, 2, 3);
        h.setBlock(log, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        var p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        Vec3 c = Vec3.atCenterOf(h.absolutePos(log));
        p.moveTo(c.x, c.y, c.z - 2.5, 0, 30);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        var r = Chopping.chop(h.getLevel(), p, h.absolutePos(log), 0, Direction.Axis.Z, 8, 1F);
        var bodies = h.getLevel().getEntitiesOfClass(WoodBodyEntity.class, new AABB(h.absolutePos(log)).inflate(3));
        Vec3 start = bodies.isEmpty() ? Vec3.ZERO : bodies.get(0).position();
        double[] spread = { 0 };
        h.runAfterDelay(15, () -> {
            if (bodies.size() == 2) spread[0] = bodies.get(0).position().distanceTo(bodies.get(1).position());
        });
        h.runAfterDelay(200, () -> {
            int piles = 0, pieces = 0;
            for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++)
                if (h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x, 2, z))) instanceof WoodPileBlockEntity be) { piles++; pieces += be.pieces().size(); }
            LOG.info("[тест] chopFliesApart: {}, тіл {}, розлетілись на {} бл. (за 0,75 с), куп {} з {} шматків, лишилось тіл {}",
                    r, bodies.size(), spread[0], piles, pieces, bodies.stream().filter(e -> !e.isRemoved()).count());
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(r == Chopping.Result.CLEAN || r == Chopping.Result.HARD, "не розрубалось: " + r);
            h.assertTrue(bodies.size() == 2 && spread[0] > 0.03, "не розлетілись (важкі — розходяться трохи): тіл " + bodies.size() + ", " + spread[0]);
            h.assertTrue(pieces == 2 && bodies.stream().allMatch(WoodBodyEntity::isRemoved), "не лягли купою: " + pieces);
            h.succeed();
        });
    }

    // 5. ЛКМ штовхає шматок, ПКМ — забрати в руки
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 100)
    public static void punchAndPickPiece(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos a = h.absolutePos(new BlockPos(3, 2, 11));
        var e = WoodBodyEntity.spawnPiece(h.getLevel(), half(), new Vector3d(a.getX(), a.getY() + 0.01, a.getZ()), new Vector3d(), new Vector3d());
        var p = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("punch".getBytes()), "punch_tester"));
        p.moveTo(a.getX() - 1.5, a.getY(), a.getZ() + 0.5, -90, 20);       // дивиться на схід
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vec3 before = e.position();
        String[] dbg = { "" };
        h.runAfterDelay(10, () -> { boolean ok = e.hurt(h.getLevel().damageSources().playerAttack(p), 1); dbg[0] = "hurt " + ok + " v " + e.body.v + " сон " + e.body.asleep; });
        h.runAfterDelay(12, () -> dbg[0] += " → v " + e.body.v + " pos " + e.position());
        h.runAfterDelay(50, () -> {
            Vec3 look = p.getLookAngle();
            double moved = (e.position().x - before.x) * look.x + (e.position().z - before.z) * look.z;
            int had = p.getInventory().countItem(Chopping.WOOD_PIECE.get());
            e.interact(p, InteractionHand.MAIN_HAND);
            int got = p.getInventory().countItem(Chopping.WOOD_PIECE.get()) - had;
            LOG.info("[тест] punchAndPickPiece: від удару туди, куди дивиться, {} бл., забрав {} ({})", moved, got, dbg[0]);
            p.getInventory().clearContent();
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(moved > 0.1, "не штовхнуло (шматок важкий — зсувається трохи): " + moved);
            h.assertTrue(got == 1 && e.isRemoved(), "не забрав");
            h.succeed();
        });
    }

    // 6. Повалена колода (блок) на стовпчику: удар — знову фізична, падає, лягає на підлогу блоком
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 300)
    public static void fallenLogWakes(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos post = new BlockPos(11, 2, 7), log = post.above();
        h.setBlock(post, Blocks.STONE);
        h.setBlock(log, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        FallenLogs.get(h.getLevel()).mark(h.absolutePos(log));
        Vec3 hit = Vec3.atCenterOf(h.absolutePos(log)).add(-0.5, 0.3, 0);
        var body = Physics.wake(h.getLevel(), h.absolutePos(log), new Vec3(1.5, 0, 0), hit);
        boolean[] done = { false };
        h.onEachTick(() -> { if (body != null && !body.isRemoved() && body.body.asleep && !done[0]) { done[0] = true; body.toBlocks(); } });
        h.runAfterDelay(240, () -> {
            int onFloor = 0;
            for (int x = 8; x < 15; x++) for (int z = 4; z < 11; z++) if (h.getBlockState(new BlockPos(x, 2, z)).is(Blocks.OAK_LOG)) onFloor++;
            boolean stillUp = h.getBlockState(log).is(Blocks.OAK_LOG);
            LOG.info("[тест] fallenLogWakes: тіло {}, лягло {}, на підлозі {}, лишилась на стовпчику {}", body != null, done[0], onFloor, stillUp);
            h.setBlock(post, Blocks.AIR);
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(body != null && done[0] && onFloor == 1 && !stillUp, "колода не впала зі стовпчика");
            h.succeed();
        });
    }

    // 7. Fij (20.11): «гравець відскакує при наближенні» — свиня поруч із колодою (не торкається) стоїть, ледь у ній — лише виштовхує
    //    на глибину, а не відкидає; сокирою по шматку — шматок одразу купою (рубати далі)
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 120)
    public static void nearbyNotLaunchedAxeSnaps(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos a = h.absolutePos(new BlockPos(7, 2, 11));
        var log = WoodBodyEntity.spawnPiece(h.getLevel(), WoodPiece.whole(Direction.Axis.X, true, false, WoodPiece.OAK),
                new Vector3d(a.getX(), a.getY() + 0.01, a.getZ()), new Vector3d(), new Vector3d());
        var near = h.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(9, 2, 11));   // за ~0,5 бл. від колоди
        var touching = h.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(5, 2, 11));
        near.setNoAi(true);
        touching.setNoAi(true);
        Vec3 n0 = near.position();
        touching.moveTo(a.getX() - 0.35, a.getY(), a.getZ() + 0.5);       // трохи влізла в колоду
        Vec3 t0 = touching.position();
        h.runAfterDelay(40, () -> {
            double nearMoved = near.position().distanceTo(n0), touchMoved = touching.position().distanceTo(t0);
            // свій гравець: FakePlayer «Minecraft» спільний для тестів партії, і сокира в його руці псувала тест штовхання
            var p = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("axe".getBytes()), "axe_tester"));
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            log.hurt(h.getLevel().damageSources().playerAttack(p), 1);
            boolean pile = h.getLevel().getBlockEntity(log.blockPosition()) instanceof WoodPileBlockEntity
                    || h.getLevel().getBlockEntity(a) instanceof WoodPileBlockEntity;
            LOG.info("[тест] nearbyNotLaunchedAxeSnaps: свиня поруч зсунулась {}, свиня в колоді {}, сокирою — купа {} (тіло {})",
                    nearMoved, touchMoved, pile, log.isRemoved() ? "зникло" : "є");
            near.discard();
            touching.discard();
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(nearMoved < 0.05, "свиню поруч відкинуло: " + nearMoved);
            h.assertTrue(touchMoved < 1.2, "свиню в колоді відкинуло далеко: " + touchMoved);
            h.assertTrue(pile && log.isRemoved(), "сокирою шматок не ліг купою");
            h.succeed();
        });
    }

    // 8. Fij (20.11): «по фізичних блоках можна бігати» — свиня падає на колоду-тіло й стоїть на ній, а не проходить крізь
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 120)
    public static void standOnBody(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos a = h.absolutePos(new BlockPos(3, 2, 3));
        var log = WoodBodyEntity.spawnPiece(h.getLevel(), WoodPiece.whole(Direction.Axis.X, true, false, WoodPiece.OAK),
                new Vector3d(a.getX(), a.getY() + 0.01, a.getZ()), new Vector3d(), new Vector3d());
        // стійка для броні: без розуму, але падає (свиня з вимкненим розумом висить у повітрі)
        var pig = h.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new BlockPos(3, 5, 3));
        pig.moveTo(a.getX() + 0.5, a.getY() + 2.2, a.getZ() + 0.5);
        double startY = pig.getY();
        h.runAfterDelay(24, () -> {   // шматок за 1,5 с лягає купою — перевірити раніше
            double top = log.body.aabb()[4], feet = pig.getY();
            boolean on = com.gigongibus.hungrycows.phys.BodyCollision.standsOn(pig) == log;
            LOG.info("[тест] standOnBody: верх колоди {}, ноги стійки {} (почала з {}), стоїть на тілі {}", top, feet, startY, on);
            pig.discard();
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(on && feet > top - 0.1 && feet < top + 0.2, "стійка не стоїть на колоді: " + feet + ", верх " + top);
            h.succeed();
        });
    }

    // 9. Fij (20.11): «гравець в'язне в дереві» — колода навскіс (45°): стійка, що була в ній, виштовхнута одразу й не застрягла;
    //    рамки щільні — біля колоди за 0,4 бл. від її краю стійка стоїть на землі, а не «на невидимій стіні»
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 100)
    public static void diagonalLogNotSticky(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos a = h.absolutePos(new BlockPos(10, 2, 3));
        var log = WoodBodyEntity.spawnPiece(h.getLevel(), WoodPiece.whole(Direction.Axis.X, true, false, WoodPiece.OAK),
                new Vector3d(a.getX(), a.getY() + 0.01, a.getZ()), new Vector3d(), new Vector3d());
        // повернута навскіс довкола свого центру
        Vector3d mid = log.body.comWorld(new Vector3d());
        log.body.q.rotateY(Math.toRadians(45));
        log.body.updateWorld();
        log.body.pos.set(mid).sub(log.body.rot.transform(new Vector3d(log.body.com)));
        var inside = h.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new BlockPos(10, 2, 3));
        inside.moveTo(mid.x, a.getY(), mid.z);                                    // посередині колоди
        var nearCorner = h.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new BlockPos(10, 2, 3));
        // кут прямої рамки навколо повернутої колоди: колода туди не дістає (≈0,3 бл. від її грані)
        nearCorner.moveTo(mid.x + 0.75, a.getY() + 1.5, mid.z + 0.75);   // край стійки — за 0,2 бл. від колоди
        h.runAfterDelay(20, () -> {
            double top = log.body.aabb()[4], floor = a.getY();
            LOG.info("[тест] diagonalLogNotSticky: стійка з середини — ноги {} (верх колоди {}), стійка біля кута — ноги {} (підлога {})",
                    inside.getY(), top, nearCorner.getY(), floor);
            inside.discard();
            nearCorner.discard();
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(inside.getY() > top - 0.1, "з колоди не виштовхнуло на неї");
            h.assertTrue(nearCorner.getY() < floor + 0.2, "біля колоди «невидима стіна»: стоїть на висоті " + (nearCorner.getY() - floor));
            h.succeed();
        });
    }

    // 10. Fij (21.11): «гравця смикає, коли стоїть на дереві» — колода похило (25°): стійка стоїть на схилі рівно (висота не скаче,
    //     не з'їжджає), а колода, що їде, везе її з собою
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 140)
    public static void slopeSteadyAndCarries(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos a = h.absolutePos(new BlockPos(7, 3, 9));
        var log = WoodBodyEntity.spawnPiece(h.getLevel(), WoodPiece.whole(Direction.Axis.X, true, false, WoodPiece.OAK),
                new Vector3d(a.getX(), a.getY(), a.getZ()), new Vector3d(), new Vector3d());
        Vector3d mid = log.body.comWorld(new Vector3d());
        log.body.q.rotateZ(Math.toRadians(25));
        log.body.updateWorld();
        log.body.pos.set(mid).sub(log.body.rot.transform(new Vector3d(log.body.com)));
        log.body.asleep = true;
        Vector3d hold = new Vector3d(log.body.pos);
        var stand = h.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new BlockPos(7, 5, 9));
        stand.moveTo(mid.x, mid.y + 1.2, mid.z);
        double[] ys = new double[40], xs = new double[40];
        double[] carried = new double[2];
        for (int t = 0; t < 100; t++) {
            final int k = t;
            h.runAfterDelay(t + 1, () -> {
                log.body.asleep = true;             // фізика колоду не рухає: тест сам її тримає
                log.body.asleepTicks = 0;
                log.body.v.zero(); log.body.w.zero();
                log.body.pos.set(hold);             // висить у повітрі там, де поставили (фізика раз на 0,5 с її «відпускала»)
                if (k >= 20 && k < 60) { ys[k - 20] = stand.getY(); xs[k - 20] = stand.getX(); }
                if (k == 60) carried[0] = stand.getX();
                if (k >= 60 && k < 80) hold.add(0, 0, 0.08);
                log.body.pos.set(hold);   // колода їде вбік (уздовж z) 1,6 бл.
                if (k == 90) carried[1] = stand.getZ() - mid.z;
            });
        }
        h.runAfterDelay(95, () -> {
            double ymin = 1e9, ymax = -1e9;
            for (double y : ys) { ymin = Math.min(ymin, y); ymax = Math.max(ymax, y); }
            double slide = Math.abs(xs[39] - xs[0]);
            LOG.info("[тест] slopeSteadyAndCarries: на схилі висота скаче на {}, з'їхала на {}; колода проїхала 1,6 — стійка {} (на тілі {})",
                    ymax - ymin, slide, carried[1], com.gigongibus.hungrycows.phys.BodyCollision.standsOn(stand) == log);
            stand.discard();
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(ymax - ymin < 0.01, "на схилі смикає: " + (ymax - ymin));
            h.assertTrue(slide < 0.05, "з'їжджає зі схилу: " + slide);
            h.assertTrue(carried[1] > 1.4, "колода не везе: " + carried[1]);
            h.succeed();
        });
    }

    // 11. по похилій колоді (20°) можна йти вгору й униз: висота міняється плавно, без провалів і стрибків
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 120)
    public static void walkUpSlope(GameTestHelper h) {
        TestWorld.calm(h);
        BlockPos a = h.absolutePos(new BlockPos(3, 3, 11));
        var log = Physics.spawnLog(h.getLevel(), a, 0, 6);
        Vector3d mid = log.body.comWorld(new Vector3d());
        log.body.q.rotateZ(Math.toRadians(20));
        log.body.updateWorld();
        log.body.pos.set(mid).sub(log.body.rot.transform(new Vector3d(log.body.com)));
        Vector3d hold = new Vector3d(log.body.pos);
        var stand = h.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new BlockPos(3, 6, 11));
        stand.moveTo(mid.x - 2, mid.y + 1.5, mid.z);
        java.util.List<double[]> path = new java.util.ArrayList<>();
        for (int t = 0; t < 90; t++) {
            final int k = t;
            h.runAfterDelay(t + 1, () -> {
                log.body.asleep = true;
                log.body.asleepTicks = 0;
                log.body.v.zero(); log.body.w.zero();
                log.body.pos.set(hold);
                if (k >= 15 && k < 45) stand.setDeltaMovement(0.12, stand.getDeltaMovement().y, 0);    // угору
                if (k >= 55 && k < 85) stand.setDeltaMovement(-0.12, stand.getDeltaMovement().y, 0);   // униз
                if (k >= 15) path.add(new double[]{ stand.getX(), stand.getY() });
            });
        }
        h.runAfterDelay(92, () -> {
            double worst = 0, climbed = 0, x0 = path.get(0)[0], y0 = path.get(0)[1];
            for (int i = 1; i < path.size(); i++) {
                double dx = path.get(i)[0] - path.get(i - 1)[0], dy = path.get(i)[1] - path.get(i - 1)[1];
                // на 20° висота на крок — tan20 × зсув; більше — ривок
                worst = Math.max(worst, Math.abs(dy) - Math.abs(dx) * Math.tan(Math.toRadians(20)));
            }
            double maxX = path.stream().mapToDouble(p -> p[0]).max().orElse(0);
            double topY = path.stream().mapToDouble(p -> p[1]).max().orElse(0);
            climbed = topY - y0;
            LOG.info("[тест] walkUpSlope: пройшла вгору {} бл., піднялась на {} (очікувано ~{}), найбільший ривок {}; кінець x {} y {}",
                    maxX - x0, climbed, (maxX - x0) * Math.tan(Math.toRadians(20)), worst, path.get(path.size() - 1)[0], path.get(path.size() - 1)[1]);
            stand.discard();
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(maxX - x0 > 2, "не йде вгору схилом: " + (maxX - x0));
            h.assertTrue(Math.abs(climbed - (maxX - x0) * Math.tan(Math.toRadians(20))) < 0.15, "висота не за схилом: " + climbed);
            h.assertTrue(worst < 0.05, "ривок висоти: " + worst);
            h.succeed();
        });
    }

    // 12. Fij (21.11): «дерево на воді не повинно тонути й лишатися фізичним» — колода падає в ставок, спливає (над водою
    //     ~третина), за 30 с не стає блоками; шматок теж пливе
    @GameTest(template = "arena", batch = "phys_water", timeoutTicks = 760)
    public static void logFloats(GameTestHelper h) {
        TestWorld.calm(h, false);
        for (int x = 2; x <= 12; x++) for (int z = 2; z <= 12; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.WATER);               // ставок 2 бл. замість підлоги
            h.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
        }
        BlockPos a = h.absolutePos(new BlockPos(4, 5, 7));
        var log = Physics.spawnLog(h.getLevel(), a, 0, 5);
        var piece = WoodBodyEntity.spawnPiece(h.getLevel(), WoodPiece.whole(Direction.Axis.X, true, false, WoodPiece.OAK).split(Direction.Axis.Z, 8, 0)[0],
                new Vector3d(a.getX() + 1, a.getY() + 1, a.getZ() + 3), new Vector3d(), new Vector3d());
        double surface = h.absolutePos(new BlockPos(0, 1, 0)).getY() + 8 / 9.0;   // верх джерела води
        double[] low = { 1e9 };
        for (int t = 0; t < 740; t += 5) h.runAfterDelay(t + 1, () -> { if (!log.isRemoved()) low[0] = Math.min(low[0], log.body.aabb()[1]); });
        h.runAfterDelay(740, () -> {
            double[] bb = log.body.aabb();
            double above = bb[4] - surface, below = surface - bb[1];
            double[] pb = piece.isRemoved() ? null : piece.body.aabb();
            LOG.info("[тест] logFloats: колода фізична {}, у воді {}, над водою {} / під водою {} (найнижче {} під водою), спить {}; шматок фізичний {}, над водою {}",
                    !log.isRemoved(), log.body.inWater, above, below, surface - low[0], log.body.asleep, !piece.isRemoved(), pb == null ? -1 : pb[4] - surface);
            boolean ok = !log.isRemoved() && log.body.inWater && above > 0.15 && above < 0.6 && !piece.isRemoved() && pb[4] - surface > 0;
            log.discard();
            piece.discard();
            for (int x = 2; x <= 12; x++) for (int z = 2; z <= 12; z++) for (int y = 0; y <= 1; y++)
                h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(ok, "колода не пливе або стала блоками: над водою " + above);
            h.succeed();
        });
    }

    // 14. Fij (21.11): «повідцем фізичне дерево можна прив'язати до коня» — кінь іде 6 бл., колода (3 бл.) волочиться за ним,
    //     мотузка не довша за 3 бл. (з запасом), кінь повільніший; відв'язав — повідець назад, кінь знову швидкий
    @GameTest(template = "arena", batch = "phys_rope", timeoutTicks = 200)
    public static void horseDragsLog(GameTestHelper h) {
        TestWorld.calm(h);
        var lvl = h.getLevel();
        var p = net.minecraftforge.common.util.FakePlayerFactory.get(lvl, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("rope".getBytes()), "rope_tester"));
        BlockPos a = h.absolutePos(new BlockPos(2, 2, 7));
        var log = Physics.spawnLog(lvl, a, 0, 3);
        var horse = h.spawn(net.minecraft.world.entity.EntityType.HORSE, new BlockPos(7, 2, 7));
        horse.setNoAi(true);
        double baseSpeed = horse.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        ItemStack lead = new ItemStack(Items.LEAD, 2);
        Vector3d knot = new Vector3d(2.9, 0.5, 0.5);                       // торець колоди, ближчий до коня
        com.gigongibus.hungrycows.phys.Rope.tie(log, horse, knot, p, lead);
        double startX = log.body.comWorld(new Vector3d()).x;
        double[] worst = { 0 }, slowSpeed = { 0 };
        for (int t = 0; t < 120; t++) {
            final int k = t;
            h.runAfterDelay(t + 2, () -> {
                if (k < 80) horse.setPos(horse.getX() + 0.075, horse.getY(), horse.getZ());   // кінь іде 1,5 бл./с, 6 бл.
                if (k > 10) {
                    Vector3d kw = log.body.toWorld(knot.x, knot.y, knot.z, new Vector3d());
                    Vector3d hw = com.gigongibus.hungrycows.phys.Rope.hitch(horse, new Vector3d());
                    worst[0] = Math.max(worst[0], kw.distance(hw));
                }
                if (k == 40) slowSpeed[0] = horse.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            });
        }
        h.runAfterDelay(125, () -> {
            double moved = log.body.comWorld(new Vector3d()).x - startX;
            boolean physical = !log.isRemoved();
            int leadsBefore = lead.getCount();
            p.getInventory().clearContent();
            com.gigongibus.hungrycows.phys.Rope.untie(log, p);
            boolean gotLead = p.getInventory().countItem(Items.LEAD) == 1;
            double after = horse.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            LOG.info("[тест] horseDragsLog: колода проїхала {} бл. (кінь 6), мотузка найдовша {}, швидкість коня {} → {} → {}, повідців у руці лишилось {}, відв'язав — повідець {}, фізична {}",
                    moved, worst[0], baseSpeed, slowSpeed[0], after, leadsBefore, gotLead, physical);
            log.discard();
            horse.discard();
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(moved > 4.5, "колода не тягнеться: " + moved);
            h.assertTrue(worst[0] < com.gigongibus.hungrycows.phys.Rope.LENGTH + 0.8, "мотузка розтягнулась: " + worst[0]);
            h.assertTrue(slowSpeed[0] < baseSpeed * 0.8 && Math.abs(after - baseSpeed) < 1e-6, "кінь не сповільнився / не відновився");
            h.assertTrue(leadsBefore == 1 && gotLead && physical, "повідець не забрало / не повернуло");
            h.succeed();
        });
    }

    // 13. Fij (21.11): «фізичне дерево не можна розрубити» → «рубається фізичним»: колода-тіло (4 бл.) упоперек — два тіла,
    //     окрема колода вздовж — дві половинки, слабкий удар — засічка, тіло одне
    @GameTest(template = "arena", batch = "phys", timeoutTicks = 120)
    public static void chopPhysicalLog(GameTestHelper h) {
        TestWorld.calm(h);
        var lvl = h.getLevel();
        var p = net.minecraftforge.common.util.FakePlayerFactory.get(lvl, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("bodychop".getBytes()), "bodychop_tester"));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        BlockPos a = h.absolutePos(new BlockPos(3, 2, 3));
        p.moveTo(a.getX() + 2, a.getY(), a.getZ() + 2.5);
        var log = Physics.spawnLog(lvl, a, 0, 4);
        BlockPos b = h.absolutePos(new BlockPos(3, 2, 9));
        var single = Physics.spawnLog(lvl, b, 0, 1);
        h.runAfterDelay(20, () -> {
            AABB area = new AABB(h.absolutePos(new BlockPos(0, 0, 0)), h.absolutePos(new BlockPos(14, 8, 14)));   // лише свій майданчик
            // 1) упоперек волокон (вісь X — уздовж колоди) посередині другої колоди, повна сила
            var r1 = com.gigongibus.hungrycows.phys.BodyChop.chop(lvl, p, log, 1, Direction.Axis.X, 8, 1F);
            int afterAcross = lvl.getEntitiesOfClass(WoodBodyEntity.class, new AABB(a).inflate(5, 2, 1.5)).size();
            // 2) окрема колода — уздовж волокон (вісь Z) посередині
            p.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 2);
            var r2 = com.gigongibus.hungrycows.phys.BodyChop.chop(lvl, p, single, 0, Direction.Axis.Z, 8, 1F);
            var near = lvl.getEntitiesOfClass(WoodBodyEntity.class, new AABB(b).inflate(2));
            // 3) слабкий удар по половинці — засічка, тіло одне
            var half = near.isEmpty() ? null : near.get(0);
            var r3 = half == null ? null : com.gigongibus.hungrycows.phys.BodyChop.chop(lvl, p, half, 0, Direction.Axis.X, 8, 0.2F);
            var afterStuck = lvl.getEntitiesOfClass(WoodBodyEntity.class, new AABB(b).inflate(2));
            boolean notch = afterStuck.stream().anyMatch(e -> e.body.parts.stream().anyMatch(q -> !q.gone && q.piece != null && q.piece.notch > 0));
            LOG.info("[тест] chopPhysicalLog: упоперек {} → тіл {} (старе зникло {}); уздовж {} → тіл {}; слабко {} → тіл {}, засічка {}",
                    r1, afterAcross, log.isRemoved(), r2, near.size(), r3, afterStuck.size(), notch);
            lvl.getEntitiesOfClass(WoodBodyEntity.class, area).forEach(e -> e.discard());
            HungryCowFellTests.clear(h, 0);
            h.assertTrue(r1 == com.gigongibus.hungrycows.chop.Chopping.Result.CLEAN && afterAcross == 2 && log.isRemoved(), "упоперек не розпалось на 2: " + afterAcross);
            h.assertTrue(r2 != com.gigongibus.hungrycows.chop.Chopping.Result.NONE && near.size() == 2, "уздовж не дві половинки: " + near.size());
            h.assertTrue(r3 == com.gigongibus.hungrycows.chop.Chopping.Result.STUCK && notch && afterStuck.size() == 2, "засічки немає");
            h.succeed();
        });
    }
}

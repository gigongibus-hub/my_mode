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
            h.assertTrue(bodies.size() == 2 && spread[0] > 0.1, "не розлетілись: тіл " + bodies.size() + ", " + spread[0]);
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
            h.assertTrue(moved > 0.2, "не штовхнуло: " + moved);
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
}

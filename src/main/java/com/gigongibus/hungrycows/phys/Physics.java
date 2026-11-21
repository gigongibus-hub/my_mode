package com.gigongibus.hungrycows.phys;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.chop.Chopping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Реєстрація фізичних тіл і події світу: крок фізики щотіку, удар по поваленій колоді, зламана опора */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID)
public final class Physics {
    private Physics() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double PUNCH_LOG = 0.5;      // ЛКМ по поваленій колоді (без сокири): поштовх (маса × бл./с; Fij «важче»: було 1,2)
    public static final int WAKE_LEAF_STEPS = 2;     // разом з колодами прокидається листя до 2 кроків від них
    public static final int WAKE_MAX = 300;

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HungryCowsMod.MODID);
    public static final RegistryObject<EntityType<WoodBodyEntity>> WOOD_BODY = ENTITIES.register("wood_body", () ->
            EntityType.Builder.<WoodBodyEntity>of(WoodBodyEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(10).updateInterval(1).fireImmune().build("wood_body"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel level) PhysicsWorld.of(level).tick();
    }

    /** Зламали блок — тіла, що на ньому лежали, прокидаються; повалені колоди над ним — знову фізичні */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        PhysicsWorld.of(level).wakeNear(e.getPos());
        FallenLogs.get(level).unmark(e.getPos());
        BlockPos up = e.getPos().above();
        level.getServer().execute(() -> {
            if (FallenLogs.get(level).isFallen(level, up) && !level.getBlockState(up.below()).isFaceSturdy(level, up.below(), Direction.UP))
                wake(level, up, new Vec3(0, 0, 0), Vec3.atCenterOf(up));
        });
    }

    /** ЛКМ без сокири по колоді поваленого дерева — тіло знову фізичне й відчуває поштовх */
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level) || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        Player p = e.getEntity();
        if (Chopping.holdsAxe(p) && !p.isShiftKeyDown()) return;          // сокирою — рубати
        if (!FallenLogs.get(level).isFallen(level, e.getPos())) return;
        e.setCanceled(true);
        Vec3 look = p.getLookAngle();
        double k = PUNCH_LOG * (p.isSprinting() ? 1.5 : 1);
        wake(level, e.getPos(), look.scale(k), p.getEyePosition().add(look.scale(Math.sqrt(p.distanceToSqr(Vec3.atCenterOf(e.getPos()))) - 0.4)));
    }

    /** Лежача дубова колода з len блоків уздовж x, повернута на angle° довкола свого центру (команда для перевірки) */
    public static WoodBodyEntity spawnLog(ServerLevel level, BlockPos pos, double angle, int len) {
        RigidBody b = new RigidBody();
        BlockState log = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int i = 0; i < len; i++) b.parts.add(Part.block(log, new Vector3d(i, 0, 0), PhysicsWorld.LOG_MASS, false));
        b.recompute();
        Vector3d mid = new Vector3d(pos.getX() + len / 2.0, pos.getY() + 0.5 + 0.01, pos.getZ() + 0.5);
        b.q.rotateY(Math.toRadians(angle));
        b.updateWorld();
        b.pos.set(mid).sub(b.rot.transform(new Vector3d(b.com)));
        WoodBodyEntity e = new WoodBodyEntity(level, b, WoodBodyEntity.TREE);
        level.addFreshEntity(e);
        return e;
    }

    /** Колоди поваленого дерева, з'єднані з pos, і листя біля них — знову одне фізичне тіло */
    public static WoodBodyEntity wake(ServerLevel level, BlockPos start, Vec3 impulse, Vec3 at) {
        FallenLogs fallen = FallenLogs.get(level);
        if (!fallen.isFallen(level, start)) return null;
        List<BlockPos> logs = new ArrayList<>(), leaves = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && logs.size() < WAKE_MAX) {
            BlockPos p = q.poll();
            logs.add(p);
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                BlockPos c = n.immutable();
                if (seen.add(c) && fallen.isFallen(level, c)) q.add(c);
            }
        }
        ArrayDeque<long[]> lq = new ArrayDeque<>();
        for (BlockPos l : logs) lq.add(new long[]{ l.asLong(), 0 });
        while (!lq.isEmpty() && leaves.size() < WAKE_MAX * 4) {
            long[] e = lq.poll();
            if (e[1] >= WAKE_LEAF_STEPS) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = BlockPos.of(e[0]).relative(d);
                if (seen.contains(n)) continue;
                BlockState s = level.getBlockState(n);
                if (!s.is(BlockTags.LEAVES) || s.getValue(LeavesBlock.PERSISTENT)) continue;
                seen.add(n);
                leaves.add(n);
                lq.add(new long[]{ n.asLong(), e[1] + 1 });
            }
        }
        RigidBody b = new RigidBody();
        BlockPos o = start;
        for (BlockPos p : logs) b.parts.add(Part.block(level.getBlockState(p), new Vector3d(p.getX() - o.getX(), p.getY() - o.getY(), p.getZ() - o.getZ()), PhysicsWorld.LOG_MASS, false));
        for (BlockPos p : leaves) b.parts.add(Part.block(level.getBlockState(p), new Vector3d(p.getX() - o.getX(), p.getY() - o.getY(), p.getZ() - o.getZ()), PhysicsWorld.LEAF_MASS, true));
        b.pos.set(o.getX(), o.getY(), o.getZ());
        b.recompute();
        for (BlockPos p : leaves) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (BlockPos p : logs) { fallen.unmark(p); level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
        Vector3d cw = b.comWorld(new Vector3d());
        b.impulse(new Vector3d(impulse.x, impulse.y, impulse.z), new Vector3d(at.x, at.y, at.z), cw);
        WoodBodyEntity ent = new WoodBodyEntity(level, b, WoodBodyEntity.TREE);
        level.addFreshEntity(ent);
        level.playSound(null, start, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1F, 0.6F);
        return ent;
    }
}

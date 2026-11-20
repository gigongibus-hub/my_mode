package com.gigongibus.hungrycows.phys;

import com.gigongibus.hungrycows.CowHearing;
import com.gigongibus.hungrycows.chop.Chopping;
import com.gigongibus.hungrycows.chop.WoodPiece;
import com.gigongibus.hungrycows.chop.WoodPieceItem;
import com.gigongibus.hungrycows.chop.WoodPileBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.BitSet;

/**
 * Фізичне тіло з дерева у світі (Fij, 20.11): повалене дерево (колоди, обрубок над зарубкою, листя) або шматок з розрубки.
 * Рахує його `PhysicsWorld`; сутність лише зберігає, передає гравцям і робить те, що видно й чути. Дерево, що лежить нерухомо
 * 20 с, стає звичайними блоками (колоди лежать, листя лишається); шматок за 3 с — купою (`WoodPileBlock`). Шматок можна
 * забрати ПКМ і штовхнути ЛКМ.
 */
public class WoodBodyEntity extends Entity implements IEntityAdditionalSpawnData {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int TREE_REST_TICKS = 400;     // дерево лежить нерухомо 20 с — стає блоками
    public static final int PIECE_REST_TICKS = 30;     // шматок — 1,5 с, і стає купою (рубати далі; було 3 с)
    public static final int THUD_GAP = 6;              // гупає не частіше раз на 0,3 с
    public static final double HEAVY = 40;             // така вага — найсильніше тремтіння землі
    public static final double PUNCH = 1.5;            // ЛКМ по шматку: поштовх (маса × бл./с; половинка — ~3 бл./с)
    public static final int CREAK_EVERY = 9;           // поки хилиться на пні — тріщить
    public static final double LEAF_SHED = 0.25;       // листя обривається в польоті: частинок за тік на рад/с
    public static final int MAX_AGE = 20 * 120;        // 2 хв і досі рухається — однаково стає блоками
    public static final byte TREE = 0, PIECE = 1;

    private static final EntityDataAccessor<Quaternionf> DATA_Q = SynchedEntityData.defineId(WoodBodyEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<CompoundTag> DATA_GONE = SynchedEntityData.defineId(WoodBodyEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> DATA_THUD = SynchedEntityData.defineId(WoodBodyEntity.class, EntityDataSerializers.INT);

    public RigidBody body = new RigidBody();
    public byte kind = TREE;
    private long lastThud = -100;
    private int thudSeq;
    private boolean thudded;
    // гравцю: попередній і теперішній поворот (плавно між тіками)
    public final Quaternionf prevQ = new Quaternionf(), curQ = new Quaternionf();
    private boolean gotQ;

    public WoodBodyEntity(EntityType<? extends WoodBodyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public WoodBodyEntity(Level level, RigidBody body, byte kind) {
        this(Physics.WOOD_BODY.get(), level);
        this.body = body;
        this.kind = kind;
        if (body.dirty) body.recompute();
        setPos(body.pos.x, body.pos.y, body.pos.z);
        syncQ();
    }

    // ---------- тверді частини (по них ходять) ----------

    private WoodBodyPart[] colliders;
    public static final double COLLIDER_SPLIT_FROM = 0.4;   // брусок довший за 0,8 бл. …
    public static final int COLLIDER_SUBS = 3;              // … — на 3 (колода — 3 × 3 рамки)

    /**
     * Частини незмінні з появи тіла (гра реєструє їх один раз): на кожну колоду/шматок, листя — ні. Рамка гри завжди пряма, тож
     * повернута колода обіймається кількома тіснішими рамками (уздовж довшої осі) — інакше навскіс лежача колода мала б
     * «невидимі стіни» довкола й гравець у них застрягав (Fij, 20.11).
     */
    @Override
    public WoodBodyPart[] getParts() {
        if (colliders == null) {
            if (body.parts.isEmpty()) return new WoodBodyPart[0];      // ще не завантажене — не запам'ятовувати
            java.util.List<WoodBodyPart> l = new java.util.ArrayList<>();
            for (int i = 0; i < body.parts.size(); i++) {
                Part p = body.parts.get(i);
                if (p.leaf) continue;
                // дві найдовші осі бруска; довша за 0,8 бл. ділиться на 3
                double[] h = { p.hx, p.hy, p.hz };
                int a = h[0] >= h[1] && h[0] >= h[2] ? 0 : h[1] >= h[2] ? 1 : 2;
                int b = -1;
                for (int k = 0; k < 3; k++) if (k != a && (b < 0 || h[k] > h[b])) b = k;
                int na = h[a] >= COLLIDER_SPLIT_FROM ? COLLIDER_SUBS : 1, nb = h[b] >= COLLIDER_SPLIT_FROM ? COLLIDER_SUBS : 1;
                for (int ia = 0; ia < na; ia++) for (int ib = 0; ib < nb; ib++) l.add(new WoodBodyPart(this, i, a, na, ia, b, nb, ib));
            }
            colliders = l.toArray(new WoodBodyPart[0]);
        }
        return colliders;
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    /** Рамки частин — за теперішнім положенням тіла (і на сервері, і в гравця: ходить гравець у себе) */
    void placeColliders() {
        body.updateWorld();
        Vector3d c = new Vector3d();
        for (WoodBodyPart wp : getParts()) {
            Part p = wp.index < body.parts.size() ? body.parts.get(wp.index) : null;
            if (p == null || p.gone) { wp.place(null); continue; }
            double[] h = { p.hx, p.hy, p.hz };
            double[] lc = { p.c.x, p.c.y, p.c.z };
            if (wp.na > 1) { double seg = h[wp.a] / wp.na; lc[wp.a] += -h[wp.a] + seg * (2 * wp.ia + 1); h[wp.a] = seg; }
            if (wp.nb > 1) { double seg = h[wp.b] / wp.nb; lc[wp.b] += -h[wp.b] + seg * (2 * wp.ib + 1); h[wp.b] = seg; }
            body.toWorld(lc[0], lc[1], lc[2], c);
            double ex = Math.abs(body.rot.m00()) * h[0] + Math.abs(body.rot.m10()) * h[1] + Math.abs(body.rot.m20()) * h[2];
            double ey = Math.abs(body.rot.m01()) * h[0] + Math.abs(body.rot.m11()) * h[1] + Math.abs(body.rot.m21()) * h[2];
            double ez = Math.abs(body.rot.m02()) * h[0] + Math.abs(body.rot.m12()) * h[1] + Math.abs(body.rot.m22()) * h[2];
            wp.place(new AABB(c.x - ex, c.y - ey, c.z - ez, c.x + ex, c.y + ey, c.z + ez));
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_Q, new Quaternionf());
        entityData.define(DATA_GONE, new CompoundTag());
        entityData.define(DATA_THUD, 0);
    }

    private void syncQ() {
        entityData.set(DATA_Q, new Quaternionf((float) body.q.x, (float) body.q.y, (float) body.q.z, (float) body.q.w));
    }

    public void partsChanged() {
        BitSet b = new BitSet();
        for (int i = 0; i < body.parts.size(); i++) if (body.parts.get(i).gone) b.set(i);
        CompoundTag t = new CompoundTag();
        t.putLongArray("G", b.toLongArray());
        entityData.set(DATA_GONE, t);
        body.dirty = true;
    }

    public boolean isPiece() {
        return kind == PIECE && body.alive() == 1 && body.parts.stream().anyMatch(p -> !p.gone && p.kind == Part.PIECE);
    }

    // ---------- щотіку ----------

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level() instanceof ServerLevel sl) PhysicsWorld.of(sl).add(this);
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level() instanceof ServerLevel sl) PhysicsWorld.of(sl).remove(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            prevQ.set(curQ);
            curQ.set(entityData.get(DATA_Q));
            if (!gotQ) { prevQ.set(curQ); gotQ = true; }
            body.q.set(curQ.x, curQ.y, curQ.z, curQ.w);
            body.pos.set(getX(), getY(), getZ());
            body.updateWorld();
            placeColliders();
            shedLeaves();
            return;
        }
        if (level() instanceof ServerLevel sl) PhysicsWorld.of(sl).add(this);
    }

    /** Після кроку фізики (з `PhysicsWorld.tick`): сон, блоки, істоти, звуки, передача гравцям */
    void afterPhysics() {
        ServerLevel level = (ServerLevel) level();
        PhysicsWorld world = PhysicsWorld.of(level);
        RigidBody b = body;
        if (b.alive() == 0 || b.onlyLeaves()) {                       // від дерева лишилось саме листя — осипалось
            for (int i = 0; i < b.parts.size(); i++) {
                Part p = b.parts.get(i);
                if (p.gone || p.state == null) continue;
                Vector3d c = b.toWorld(p.c.x, p.c.y, p.c.z, new Vector3d());
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.state), c.x, c.y, c.z, 3, 0.3, 0.3, 0.3, 0.05);
            }
            discard();
            return;
        }
        if (!b.asleep) {
            world.crushPlants(this);
            if (b.v.length() < PhysicsWorld.SLEEP_V && b.w.length() < PhysicsWorld.SLEEP_W && !b.hinged) {
                if (++b.still >= PhysicsWorld.SLEEP_TICKS) { b.asleep = true; b.v.zero(); b.w.zero(); }
            } else b.still = 0;
        }
        setPos(b.pos.x, b.pos.y, b.pos.z);
        placeColliders();                                             // спершу рамки — за ними виштовхуємо істот
        world.entities(this);
        b.asleepTicks = b.asleep ? b.asleepTicks + 1 : 0;
        if (b.asleepTicks >= (kind == TREE ? TREE_REST_TICKS : PIECE_REST_TICKS) || tickCount > MAX_AGE) { toBlocks(); return; }
        if (b.pos.y < level.getMinBuildHeight() - 32) { discard(); return; }
        setPos(b.pos.x, b.pos.y, b.pos.z);
        syncQ();
        placeColliders();
        if (b.hinged && tickCount % CREAK_EVERY == 0)                 // тріщить волокно в зарубці
            level.playSound(null, BlockPos.containing(b.pivot.x, b.pivot.y, b.pivot.z), level.random.nextBoolean()
                    ? SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR : SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.7F, 0.4F + level.random.nextFloat() * 0.2F);
        if (kind == TREE && tickCount % 8 == 0 && b.w.length() > 0.25 && b.leafMass > 0) {   // крона шумить у польоті
            Vector3d c = b.comWorld(new Vector3d());
            level.playSound(null, BlockPos.containing(c.x, c.y, c.z), SoundEvents.GRASS_BREAK, SoundSource.BLOCKS,
                    0.5F + (float) Math.min(1, b.w.length()), 0.6F);
        }
    }

    /** Удар об землю (з `PhysicsWorld`): гупає, пил; важке — гуркіт на всю округу, корови чують, земля здригається */
    void impact(Vector3d at, double speed, double energy, BlockPos ground) {
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        if (now - lastThud < THUD_GAP) return;
        lastThud = now;
        BlockPos pos = BlockPos.containing(at.x, at.y, at.z);
        if (ground != null) {
            BlockState g = level.getBlockState(ground);
            if (!g.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, g), at.x, at.y, at.z,
                    (int) Math.min(30, 3 + energy / 4), 0.3, 0.05, 0.3, 0.1);
        }
        float vol = (float) Math.min(3, 0.3 + energy / 40);
        if (body.mass >= 3) {
            level.playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, vol, 0.45F);
            level.playSound(null, pos, SoundEvents.GENERIC_BIG_FALL, SoundSource.BLOCKS, vol, 0.5F);
        }
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, Math.min(1.5F, vol + 0.3F), body.mass >= 3 ? 0.5F : 1.3F);
        if (!thudded && energy > 15) {
            CowHearing.onSound(level, "hungrycows:tree_fall", new Vec3(at.x, at.y, at.z), this, vol);
            thudded = true;
        }
        if (body.mass >= 4 && energy > 20)
            entityData.set(DATA_THUD, ++thudSeq * 1000 + (int) Math.min(100, body.mass / HEAVY * 100 * Math.min(1, speed / 8)));
    }

    // ---------- стати блоками ----------

    /** Лягло: колоди — блоками на землю (лежачи, за тим, як лягли), листя — де вільно, шматки — купою */
    public void toBlocks() {
        if (isRemoved() || !(level() instanceof ServerLevel level)) return;
        RigidBody b = body;
        b.updateWorld();
        FallenLogs fallen = FallenLogs.get(level);
        for (int pass = 0; pass < 3; pass++)
            for (Part p : b.parts) {
                if (p.gone) continue;
                boolean logPass = pass == 0 && p.kind == Part.BLOCK && !p.leaf, piecePass = pass == 1 && p.kind == Part.PIECE,
                        leafPass = pass == 2 && p.leaf;
                if (!logPass && !piecePass && !leafPass) continue;
                Vector3d c = b.toWorld(p.c.x, p.c.y, p.c.z, new Vector3d());
                if (piecePass) { placePiece(level, p, c); continue; }
                BlockState st = p.state;
                if (logPass && st.hasProperty(RotatedPillarBlock.AXIS))
                    st = st.setValue(RotatedPillarBlock.AXIS, worldAxis(st.getValue(RotatedPillarBlock.AXIS)));
                if (leafPass && st.hasProperty(LeavesBlock.PERSISTENT)) st = st.setValue(LeavesBlock.PERSISTENT, false);
                BlockPos cell = BlockPos.containing(c.x, c.y, c.z);
                boolean placed = false;
                for (int k = 0; k < (logPass ? 3 : 1) && !placed; k++, cell = cell.above()) {
                    BlockState there = level.getBlockState(cell);
                    if (!there.canBeReplaced() || leafPass && (!there.isAir() || !st.canSurvive(level, cell))) continue;
                    if (!there.isAir()) level.destroyBlock(cell, true);
                    level.setBlock(cell, st, Block.UPDATE_ALL);
                    if (leafPass) level.scheduleTick(cell, st.getBlock(), 1);     // листя саме перерахує відстань до колод
                    if (logPass) fallen.mark(cell);
                    placed = true;
                }
                if (!placed && logPass) Block.popResource(level, BlockPos.containing(c.x, c.y, c.z), new ItemStack(st.getBlock()));
            }
        discard();
    }

    /** Вісь колоди у світі після повороту тіла */
    private Direction.Axis worldAxis(Direction.Axis local) {
        Vector3d a = new Vector3d(local == Direction.Axis.X ? 1 : 0, local == Direction.Axis.Y ? 1 : 0, local == Direction.Axis.Z ? 1 : 0);
        body.rot.transform(a);
        double x = Math.abs(a.x), y = Math.abs(a.y), z = Math.abs(a.z);
        return y >= x && y >= z ? Direction.Axis.Y : x >= z ? Direction.Axis.X : Direction.Axis.Z;
    }

    /** Шматок лягає в купу на тому місці, де лежить (або докладається до купи під ним) */
    private void placePiece(ServerLevel level, Part p, Vector3d c) {
        WoodPiece w = p.piece.copy();
        Direction.Axis g = worldAxis(w.grain);
        boolean lying = g != Direction.Axis.Y;
        double halfY = Math.abs(body.rot.m01()) * p.hx + Math.abs(body.rot.m11()) * p.hy + Math.abs(body.rot.m21()) * p.hz;
        BlockPos cell = BlockPos.containing(c.x, c.y - halfY + 0.05, c.z);
        double tx = (c.x - cell.getX()) * 16, tz = (c.z - cell.getZ()) * 16;
        // спершу там, де лежить, потім вище, потім поруч (на пні, на колоді — місце зайняте)
        BlockPos base = cell;
        BlockPos[] tries = { base, base.above(), base.above(2), base.north(), base.south(), base.east(), base.west(),
                base.north().above(), base.south().above(), base.east().above(), base.west().above() };
        for (BlockPos at : tries) {
            if (level.getBlockEntity(at) instanceof WoodPileBlockEntity pile) {
                if (pile.add(w, lying, tx, tz, lying ? g : Direction.Axis.X)) { level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 0.9F); return; }
                continue;
            }
            BlockState there = level.getBlockState(at);
            if (!there.canBeReplaced() || !there.getFluidState().isEmpty()) continue;
            if (!there.isAir()) level.destroyBlock(at, true);
            level.setBlock(at, Chopping.WOOD_PILE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(at) instanceof WoodPileBlockEntity pile && pile.add(w, lying, tx, tz, lying ? g : Direction.Axis.X)) return;
            level.removeBlock(at, false);
        }
        Block.popResource(level, BlockPos.containing(c.x, c.y, c.z), WoodPieceItem.of(w));
    }

    // ---------- гравець ----------

    @Override
    public boolean isPickable() {
        return isPiece();
    }

    @Override
    public boolean isAttackable() {
        return isPiece();
    }

    /** ПКМ по шматку — забрати */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isPiece()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        for (Part p : body.parts) {
            if (p.gone || p.kind != Part.PIECE) continue;
            ItemStack s = WoodPieceItem.of(p.piece);
            if (!player.getInventory().add(s)) player.drop(s, false);
        }
        level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
        discard();
        return InteractionResult.CONSUME;
    }

    /** ЛКМ по шматку — штовхнути туди, куди дивишся */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || !isPiece()) return false;
        if (src.getEntity() instanceof Player pl && Chopping.holdsAxe(pl) && !pl.isShiftKeyDown()) {
            // Fij (20.11: «фізичні частинки не можна розрубити»): сокирою — шматок одразу лягає купою, і його рубаєш як завжди
            toBlocks();
            return true;
        }
        if (src.getEntity() instanceof Player pl) {
            Vec3 look = pl.getLookAngle();
            Vector3d cw = body.comWorld(new Vector3d());
            Vector3d at = new Vector3d(cw).add(-look.x * 0.1, 0.05, -look.z * 0.1);
            double k = PUNCH * (pl.isSprinting() ? 1.5 : 1);
            body.impulse(new Vector3d(look.x, Math.max(0.25, look.y + 0.3), look.z).mul(k), at, cw);
            body.asleep = false;
            body.still = 0;
            body.asleepTicks = 0;
            level().playSound(null, blockPosition(), SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 1F);
            return true;
        }
        return false;
    }

    // ---------- гравцю ----------

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) return;
        if (DATA_GONE.equals(key)) {
            BitSet g = BitSet.valueOf(entityData.get(DATA_GONE).getLongArray("G"));
            for (int i = 0; i < body.parts.size(); i++) body.parts.get(i).gone = g.get(i);
            refreshDimensions();
        }
        if (DATA_THUD.equals(key) && entityData.get(DATA_THUD) > 0) {
            Vector3d c = new Vector3d();
            int n = 0;
            for (Part p : body.parts) if (!p.gone) { c.add(body.toWorld(p.c.x, p.c.y, p.c.z, new Vector3d())); n++; }
            if (n > 0) c.div(n);
            float s = entityData.get(DATA_THUD) % 1000 / 100F;
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.gigongibus.hungrycows.client.TreeShakeClient.thud(c.x, c.y, c.z, s));
        }
    }

    private void shedLeaves() {
        if (body.parts.isEmpty() || kind != TREE) return;
        float speed = prevQ.angle() == curQ.angle() && prevQ.equals(curQ) ? 0 : new Quaternionf(curQ).mul(new Quaternionf(prevQ).invert()).angle() * 20;
        if (speed < 0.2F) return;
        int n = (int) (speed * LEAF_SHED * 4) + (random.nextFloat() < speed * LEAF_SHED ? 1 : 0);
        for (int k = 0; k < n; k++) {
            Part p = body.parts.get(random.nextInt(body.parts.size()));
            if (!p.leaf || p.gone || p.state == null) continue;
            Vector3d c = body.toWorld(p.c.x, p.c.y, p.c.z, new Vector3d());
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, p.state), c.x + random.nextGaussian() * 0.4,
                    c.y + random.nextGaussian() * 0.4, c.z + random.nextGaussian() * 0.4, 0, 0, 0);
        }
    }

    @Override
    protected AABB makeBoundingBox() {
        if (body == null || body.parts.isEmpty()) return super.makeBoundingBox();
        double dx = getX() - body.pos.x, dy = getY() - body.pos.y, dz = getZ() - body.pos.z;
        double[] b = body.aabb();
        if (b[0] > b[3]) return super.makeBoundingBox();
        return new AABB(b[0] + dx, b[1] + dy, b[2] + dz, b[3] + dx, b[4] + dy, b[5] + dz);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    // ---------- збереження ----------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Part p : body.parts) l.add(p.save());
        tag.put("Parts", l);
        tag.putByte("Kind", kind);
        RigidBody b = body;
        putD(tag, "State", new double[]{ b.pos.x, b.pos.y, b.pos.z, b.q.x, b.q.y, b.q.z, b.q.w, b.v.x, b.v.y, b.v.z, b.w.x, b.w.y, b.w.z });
        if (b.hinged) {
            putD(tag, "Hinge", new double[]{ b.pivot.x, b.pivot.y, b.pivot.z, b.anchor.x, b.anchor.y, b.anchor.z,
                    b.axis.x, b.axis.y, b.axis.z, b.fallDir.x, b.fallDir.y, b.fallDir.z, b.hingeRelease, b.kickOmega });
        }
        tag.putBoolean("Asleep", b.asleep);
        tag.putInt("AsleepTicks", b.asleepTicks);
    }

    private static void putD(CompoundTag t, String k, double[] a) {
        ListTag l = new ListTag();
        for (double d : a) l.add(net.minecraft.nbt.DoubleTag.valueOf(d));
        t.put(k, l);
    }

    private static double[] getD(CompoundTag t, String k) {
        ListTag l = t.getList(k, Tag.TAG_DOUBLE);
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.getDouble(i);
        return a;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        RigidBody b = new RigidBody();
        for (Tag t : tag.getList("Parts", Tag.TAG_COMPOUND)) b.parts.add(Part.load((CompoundTag) t, level()));
        kind = tag.getByte("Kind");
        double[] s = getD(tag, "State");
        if (s.length == 13) {
            b.pos.set(s[0], s[1], s[2]);
            b.q.set(s[3], s[4], s[5], s[6]).normalize();
            b.v.set(s[7], s[8], s[9]);
            b.w.set(s[10], s[11], s[12]);
        } else b.pos.set(getX(), getY(), getZ());
        double[] h = getD(tag, "Hinge");
        if (h.length == 14) {
            b.hinged = true;
            b.pivot.set(h[0], h[1], h[2]);
            b.anchor.set(h[3], h[4], h[5]);
            b.axis.set(h[6], h[7], h[8]);
            b.fallDir.set(h[9], h[10], h[11]);
            b.hingeRelease = h[12];
            b.kickOmega = h[13];
        }
        b.asleep = tag.getBoolean("Asleep");
        b.asleepTicks = tag.getInt("AsleepTicks");
        b.recompute();
        body = b;
        syncQ();
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeVarInt(body.parts.size());
        for (Part p : body.parts) p.write(buf);
        BitSet g = new BitSet();
        for (int i = 0; i < body.parts.size(); i++) if (body.parts.get(i).gone) g.set(i);
        buf.writeLongArray(g.toLongArray());
        buf.writeDouble(body.pos.x);
        buf.writeDouble(body.pos.y);
        buf.writeDouble(body.pos.z);
        buf.writeFloat((float) body.q.x);
        buf.writeFloat((float) body.q.y);
        buf.writeFloat((float) body.q.z);
        buf.writeFloat((float) body.q.w);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        kind = buf.readByte();
        int n = buf.readVarInt();
        RigidBody b = new RigidBody();
        for (int i = 0; i < n; i++) b.parts.add(Part.read(buf));
        BitSet g = BitSet.valueOf(buf.readLongArray());
        for (int i = 0; i < n; i++) b.parts.get(i).gone = g.get(i);
        b.pos.set(buf.readDouble(), buf.readDouble(), buf.readDouble());
        curQ.set(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        prevQ.set(curQ);
        gotQ = true;
        b.q.set(curQ.x, curQ.y, curQ.z, curQ.w);
        b.dirty = false;
        b.updateWorld();
        body = b;
        setPos(b.pos.x, b.pos.y, b.pos.z);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    /** Для тестів і команд: тіло в одному шматку */
    public static WoodBodyEntity spawnPiece(ServerLevel level, WoodPiece piece, Vector3d corner, Vector3d v, Vector3d w) {
        RigidBody b = new RigidBody();
        b.parts.add(Part.piece(piece, new Vector3d(), PhysicsWorld.LOG_MASS));
        b.pos.set(corner);
        b.recompute();
        b.v.set(v);
        b.w.set(w);
        WoodBodyEntity e = new WoodBodyEntity(level, b, PIECE);
        level.addFreshEntity(e);
        return e;
    }

}

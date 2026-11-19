package com.gigongibus.hungrycows.fell;

import com.gigongibus.hungrycows.CowHearing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Дерево, що падає (Fij, 19.11: «смак, тяжкість дерева і його руйнівна сила, реалістичність»). Тверде тіло: обертається довкола
 * пня (точка розрубу) в бік `dir`. Кожна колода — важка точка, листок — легка; падає під власною вагою: спершу ледь-ледь (тріщить,
 * є час відбігти), потім усе швидше (момент сили від центру ваги / момент інерції). Що зустрічає дорогою: траву й квіти — трощить;
 * паркани, скло, дошки — ламає, якщо вистачає сили (енергія падіння ½·I·ω², важче й швидше дерево ламає більше), і від цього
 * сповільнюється; землю й камінь — ні: удар, дерево трохи підскакує, гупає, листя ламається й сиплеться. Кого накрило —
 * б'є (швидкість × вага). Ляже — колоди стають на землю (їх рубають нашою розрубкою).
 */
public class FallingTreeEntity extends Entity implements IEntityAdditionalSpawnData {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double LOG_MASS = 1.0;       // колода
    public static final double LEAF_MASS = 0.06;     // листок (крона важить, але небагато)
    public static final double GRAVITY = 12;         // бл./с² (справжнє 9,8 при 1 бл. = 1 м; трохи більше — гра не тягнеться)
    public static final double KICK_ANGLE = Math.toRadians(4);   // перші 4° — «зарубка штовхає»: дерево вже не стоїть
    public static final double KICK_ALPHA = 0.12;    // … щонайменше таке кутове прискорення (рад/с²)
    public static final double START_OMEGA = 0.012;  // з чого починає (рад/с) — тріщить і ледь-ледь хилиться (Fij: «тяжкість»)
    public static final double CREAK_UNTIL = Math.toRadians(20);   // поки хилиться повільно — тріщить
    public static final int CREAK_EVERY = 9;         // раз на ~0,45 с
    public static final double LEAF_DRAG = 0.6;      // опір повітря кроні (частка ω за с × частка маси листя)
    public static final double BREAK_BASE = 8;       // ламати блок коштує енергії: 8 + 55 × міцність
    public static final double BREAK_PER_HARDNESS = 55;
    public static final double PLANT_COST = 0.5;     // трава, квіти, сніг — майже нічого
    public static final double LEAF_LOSS = 0.15;     // листок, що вдарився, забирає трохи енергії
    public static final double BOUNCE = 0.22;        // удар об землю: відскакує з такою часткою швидкості
    public static final double BOUNCE_MIN = 0.9;     // повільніше (рад/с) — уже не підскакує, лягає
    public static final int MAX_BOUNCES = 3;
    public static final double LOG_MIN_R = 1.1;      // колоди ближче до пня не б'ються об землю (лежать на пні)
    public static final double HIT_PER_SPEED = 1.1;  // шкода: 1,1 × швидкість (бл./с) × вага …
    public static final double HIT_MAX = 40;
    public static final double LEAF_HIT = 0.2;       // крона б'є слабше за колоду (гілки)
    public static final double MAX_ANGLE = Math.toRadians(150);  // з урвища — далі не крутиться, лягає
    public static final int MAX_AGE = 600;           // 30 с — лягає, хоч би що
    public static final double MAX_MOVE = 0.35;      // за підкрок жодна точка не зсувається більше (не проскакує крізь блок)
    public static final int LEAF_PARTICLES_MAX = 300;
    public static boolean DEBUG = false;
    public static final double HEAVY_TREE = 40;      // така вага (колоди + листя) — найсильніше тремтіння землі
    public static final double LEAF_SHED = 0.25;     // листя обривається в польоті: частинок за тік на кожен рад/с швидкості

    private static final EntityDataAccessor<Float> DATA_ANGLE = SynchedEntityData.defineId(FallingTreeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_THUD = SynchedEntityData.defineId(FallingTreeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> DATA_BROKEN = SynchedEntityData.defineId(FallingTreeEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** Частина дерева: блок і де лежить його нижній кут відносно пня (до падіння) */
    public static final class Part {
        public final BlockState state;
        public final Vec3 rel;
        public final boolean log;

        public Part(BlockState state, Vec3 rel, boolean log) {
            this.state = state;
            this.rel = rel;
            this.log = log;
        }
    }

    public final List<Part> parts = new ArrayList<>();
    private final BitSet broken = new BitSet();
    private Vec3 dir = new Vec3(1, 0, 0);
    private double angle, omega = START_OMEGA;
    private int bounces;
    private boolean crashed;
    private final Set<UUID> hit = new HashSet<>();
    public float prevAngle;       // для плавного малювання
    private double radius = 4;

    /** Блок пня (під точкою обертання) */
    private BlockPos stump() {
        return BlockPos.containing(getX(), getY() - 0.01, getZ());
    }

    public FallingTreeEntity(EntityType<? extends FallingTreeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public FallingTreeEntity(Level level, Vec3 pivot, Vec3 dir, List<Part> parts) {
        this(Felling.FALLING_TREE.get(), level);
        setPos(pivot.x, pivot.y, pivot.z);
        this.dir = dir;
        this.parts.addAll(parts);
        updateRadius();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ANGLE, 0F);
        entityData.define(DATA_BROKEN, new CompoundTag());
        entityData.define(DATA_THUD, 0);
    }

    public Vec3 dir() { return dir; }
    public float angle() { return entityData.get(DATA_ANGLE); }
    public boolean isBroken(int i) { return broken.get(i); }

    private void updateRadius() {
        double r = 2;
        for (Part p : parts) r = Math.max(r, p.rel.add(0.5, 0.5, 0.5).length() + 1);
        radius = r;
    }

    // ---------- геометрія ----------

    /** Де опиниться точка (відносно пня) при куті a: «угору» хилиться в бік dir */
    public Vec3 rotate(Vec3 r, double a) {
        double along = r.x * dir.x + r.z * dir.z, h = r.y;
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double s = r.x * side.x + r.z * side.z;
        double c = Math.cos(a), sn = Math.sin(a);
        double along2 = along * c + h * sn, h2 = h * c - along * sn;
        return new Vec3(dir.x * along2 + side.x * s, h2, dir.z * along2 + side.z * s);
    }

    private Vec3 world(Part p, double a) {
        return position().add(rotate(p.rel.add(0.5, 0.5, 0.5), a));
    }

    // ---------- фізика ----------

    @Override
    public void tick() {
        super.tick();
        prevAngle = entityData.get(DATA_ANGLE);
        if (level().isClientSide) { shedLeaves(); return; }
        ServerLevel level = (ServerLevel) level();
        if (tickCount > MAX_AGE) { land(level); return; }
        double dtTick = 0.05;
        int sub = (int) Math.max(1, Math.min(32, Math.ceil(Math.abs(omega) * radius * dtTick / MAX_MOVE)));
        double dt = dtTick / sub;
        for (int i = 0; i < sub && !isRemoved(); i++) step(level, dt);
        if (isRemoved()) return;
        entityData.set(DATA_ANGLE, (float) angle);
        if (angle < CREAK_UNTIL && tickCount % CREAK_EVERY == 0 && !crashed)   // тріщить волокно в зарубці
            level.playSound(null, BlockPos.containing(position()), level.random.nextBoolean() ? SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR
                    : SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.7F, 0.4F + level.random.nextFloat() * 0.2F);
        if (tickCount % 8 == 0 && angle > 0.25 && !crashed)            // крона шумить, поки летить
            level.playSound(null, BlockPos.containing(world(parts.get(parts.size() - 1), angle)), SoundEvents.GRASS_BREAK,
                    SoundSource.BLOCKS, 0.5F + (float) Math.min(1, Math.abs(omega)), 0.6F);
    }

    private void step(ServerLevel level, double dt) {
        // момент сили від ваги й момент інерції (довкола осі обертання, що йде крізь пень упоперек падіння)
        double torque = 0, inertia = 0, leafMass = 0, mass = 0;
        for (int i = 0; i < parts.size(); i++) {
            if (broken.get(i)) continue;
            Part p = parts.get(i);
            double m = p.log ? LOG_MASS : LEAF_MASS;
            Vec3 c = p.rel.add(0.5, 0.5, 0.5);
            double along = c.x * dir.x + c.z * dir.z, h = c.y;
            double along2 = along * Math.cos(angle) + h * Math.sin(angle);
            torque += GRAVITY * m * along2;
            inertia += m * (along * along + h * h);
            mass += m;
            if (!p.log) leafMass += m;
        }
        if (inertia < 1e-6) { land(level); return; }
        double alpha = torque / inertia;
        if (angle < KICK_ANGLE && alpha < KICK_ALPHA) alpha = KICK_ALPHA;
        omega += alpha * dt;
        omega *= 1 - LEAF_DRAG * (leafMass / mass) * dt;
        double next = angle + omega * dt;
        // що зустрічає на шляху до next
        boolean blocked = false;
        for (int i = 0; i < parts.size() && !blocked; i++) {
            if (broken.get(i)) continue;
            Part p = parts.get(i);
            Vec3 c = world(p, next);
            double r = p.rel.add(0.5, 0.5, 0.5).length();
            if (p.log) {
                if (r < LOG_MIN_R) continue;
                for (Vec3 pt : new Vec3[]{ c, c.add(0, -0.45, 0) }) {
                    if (!meet(level, BlockPos.containing(pt), inertia)) { blocked = true; break; }
                }
            } else {
                BlockPos cell = BlockPos.containing(c);
                BlockState s = level.getBlockState(cell);
                if (s.isAir()) continue;
                if (soft(level, cell, s)) { level.destroyBlock(cell, true); continue; }
                breakLeaf(level, i, c);                             // листок об щось тверде — ламається
                loseEnergy(LEAF_LOSS, inertia);
            }
        }
        if (blocked) {
            impact(level, mass);
            return;
        }
        angle = next;
        hurtEntities(level, mass);
        if (angle > MAX_ANGLE) land(level);
    }

    private boolean soft(ServerLevel level, BlockPos pos, BlockState s) {
        return s.canBeReplaced() || s.getCollisionShape(level, pos).isEmpty() && s.getDestroySpeed(level, pos) >= 0 && s.getFluidState().isEmpty();
    }

    /** Земля, камінь, руда, метал, бедрок — дерево їх не ламає (лише б'ється) */
    private static boolean ground(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.getDestroySpeed(level, pos) < 0 || s.requiresCorrectToolForDrops()) return true;
        return s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH) || s.is(net.minecraft.tags.BlockTags.SNOW) && s.isCollisionShapeFullBlock(level, pos)
                || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE);
    }

    /** Колода зустрічає блок: true — пройшла (порожньо, трава, або зламала), false — упирається */
    private boolean meet(ServerLevel level, BlockPos pos, double inertia) {
        if (pos.equals(stump())) return true;              // свій пень не тримає (дерево зісковзує з нього)
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getCollisionShape(level, pos).isEmpty()) return true;
        if (soft(level, pos, s)) {
            level.destroyBlock(pos, true);
            loseEnergy(PLANT_COST, inertia);
            return true;
        }
        if (ground(level, pos, s)) { if (DEBUG) System.out.println("[дерево] упор у " + s + " " + pos + " кут " + Math.toDegrees(angle)); return false; }
        double cost = BREAK_BASE + BREAK_PER_HARDNESS * s.getDestroySpeed(level, pos);
        double energy = 0.5 * inertia * omega * omega;
        if (energy <= cost) { if (DEBUG) System.out.println("[дерево] не зламало " + s + " енергія " + energy + " треба " + cost); return false; }
        level.destroyBlock(pos, true);                     // паркан, скло, дошки — трощить, якщо вистачає сили
        loseEnergy(cost, inertia);
        return true;
    }

    private void loseEnergy(double e, double inertia) {
        double energy = Math.max(0, 0.5 * inertia * omega * omega - e);
        omega = Math.signum(omega) * Math.sqrt(2 * energy / inertia);
    }

    private void impact(ServerLevel level, double mass) {
        hurtEntities(level, mass);
        if (!crashed) {
            crashed = true;
            Vec3 top = crownCenter();
            float vol = (float) Math.min(3, 0.8 + Math.abs(omega) * 0.6);
            level.playSound(null, BlockPos.containing(top), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, vol, 0.45F);
            level.playSound(null, BlockPos.containing(top), SoundEvents.GENERIC_BIG_FALL, SoundSource.BLOCKS, vol, 0.5F);
            level.playSound(null, BlockPos.containing(top), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, vol, 0.5F);
            CowHearing.onSound(level, "hungrycows:tree_fall", top, this, vol);
            entityData.set(DATA_THUD, (int) Math.min(100, mass / HEAVY_TREE * 100));   // гравцям поруч — здригнулась земля
            dust(level);
        }
        if (Math.abs(omega) > BOUNCE_MIN && bounces < MAX_BOUNCES) {
            bounces++;
            omega = -omega * BOUNCE;                     // підскок
        } else {
            land(level);
        }
    }

    private Vec3 crownCenter() {
        Vec3 sum = Vec3.ZERO;
        int n = 0;
        double a = level().isClientSide ? angle() : angle;
        for (int i = 0; i < parts.size(); i++) if (!parts.get(i).log) { sum = sum.add(world(parts.get(i), a)); n++; }
        return n == 0 ? position() : sum.scale(1.0 / n);
    }

    /** Пил і тріски там, де колоди лягли на землю */
    private void dust(ServerLevel level) {
        for (Part p : parts) {
            if (!p.log) continue;
            Vec3 c = world(p, angle);
            BlockPos below = BlockPos.containing(c.add(0, -1, 0));
            BlockState g = level.getBlockState(below);
            if (!g.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, g), c.x, c.y - 0.4, c.z, 6, 0.4, 0.1, 0.4, 0.1);
        }
    }

    private void breakLeaf(ServerLevel level, int i, Vec3 at) {
        if (broken.get(i)) return;
        broken.set(i);
        Part p = parts.get(i);
        BlockPos pos = BlockPos.containing(at);
        if (p.state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
            Block.dropResources(p.state, level, findFree(level, pos), null);
        if (level.random.nextInt(3) == 0)
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.state), at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.05);
        CompoundTag t = new CompoundTag();
        t.putLongArray("B", broken.toLongArray());
        entityData.set(DATA_BROKEN, t);
    }

    private static BlockPos findFree(ServerLevel level, BlockPos pos) {
        for (int k = 0; k < 4; k++) { if (!level.getBlockState(pos).isSolidRender(level, pos)) return pos; pos = pos.above(); }
        return pos;
    }

    /** Кого накрило — удар: швидкість колоди × вага дерева (раз на падіння кожного) */
    private void hurtEntities(ServerLevel level, double mass) {
        if (Math.abs(omega) < 0.3) return;
        double weight = Math.max(0.5, Math.min(3, Math.sqrt(mass / 5)));
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (broken.get(i)) continue;
            double r = p.rel.add(0.5, 0.5, 0.5).length();
            Vec3 c = world(p, angle);
            AABB box = new AABB(c.subtract(0.5, 0.5, 0.5), c.add(0.5, 0.5, 0.5));
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (hit.contains(e.getUUID()) || e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
                double v = Math.abs(omega) * r;
                float dmg = (float) Math.min(HIT_MAX, HIT_PER_SPEED * v * weight * (p.log ? 1 : LEAF_HIT));
                if (dmg < 1) continue;
                hit.add(e.getUUID());
                e.hurt(level.damageSources().fallingBlock(this), dmg);
                e.push(dir.x * v * 0.08, 0.2, dir.z * v * 0.08);
            }
        }
    }

    /** Ляг: листя ламається й сиплеться, колоди стають на землю (впоперек — лежачи) */
    public void land(ServerLevel level) {
        if (isRemoved()) return;
        if (DEBUG) System.out.println("[дерево] лягло: кут " + Math.toDegrees(angle) + " ω " + omega + " тік " + tickCount + " підскоків " + bounces);
        int particles = 0;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (p.log || broken.get(i)) continue;
            Vec3 c = world(p, angle);
            if (p.state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
                Block.dropResources(p.state, level, findFree(level, BlockPos.containing(c)), null);
            if (particles < LEAF_PARTICLES_MAX) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.state), c.x, c.y, c.z, 3, 0.3, 0.3, 0.3, 0.05);
                particles += 3;
            }
        }
        if (particles > 0) level.playSound(null, BlockPos.containing(crownCenter()), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.5F, 0.7F);
        Vec3 up = rotate(new Vec3(0, 1, 0), angle);
        Direction.Axis axis = Math.abs(up.y) >= 0.707 ? Direction.Axis.Y : Math.abs(up.x) >= Math.abs(up.z) ? Direction.Axis.X : Direction.Axis.Z;
        for (Part p : parts) {
            if (!p.log) continue;
            BlockState st = p.state.hasProperty(RotatedPillarBlock.AXIS) ? p.state.setValue(RotatedPillarBlock.AXIS, axis) : p.state;
            BlockPos cell = BlockPos.containing(world(p, angle));
            boolean placed = false;
            for (int k = 0; k < 3 && !placed; k++, cell = cell.above()) {
                BlockState there = level.getBlockState(cell);
                if (there.canBeReplaced()) {
                    if (!there.isAir()) level.destroyBlock(cell, true);
                    level.setBlock(cell, st, Block.UPDATE_ALL);
                    placed = true;
                }
            }
            if (!placed) Block.popResource(level, BlockPos.containing(world(p, angle)), new ItemStack(p.state.getBlock()));
        }
        discard();
    }

    /** Гравцю: з крони обривається листя, поки дерево летить (що швидше — то більше) */
    private void shedLeaves() {
        float a = angle(), speed = Math.abs(a - prevAngle) * 20;
        if (speed < 0.2F || parts.isEmpty()) return;
        int n = (int) (speed * LEAF_SHED * 4) + (random.nextFloat() < speed * LEAF_SHED ? 1 : 0);
        for (int k = 0; k < n; k++) {
            int i = random.nextInt(parts.size());
            Part p = parts.get(i);
            if (p.log || broken.get(i)) continue;
            Vec3 c = world(p, a);
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, p.state), c.x + random.nextGaussian() * 0.4,
                    c.y + random.nextGaussian() * 0.4, c.z + random.nextGaussian() * 0.4, 0, 0, 0);
        }
    }

    // ---------- збереження й передача гравцям ----------

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_THUD.equals(key) && level().isClientSide && entityData.get(DATA_THUD) > 0) {
            Vec3 c = crownCenter();
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.gigongibus.hungrycows.client.TreeShakeClient.thud(c.x, c.y, c.z, entityData.get(DATA_THUD) / 100F));
        }
        if (DATA_BROKEN.equals(key)) {
            broken.clear();
            broken.or(BitSet.valueOf(entityData.get(DATA_BROKEN).getLongArray("B")));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        parts.clear();
        ListTag l = tag.getList("Parts", Tag.TAG_COMPOUND);
        var blocks = level().holderLookup(net.minecraft.core.registries.Registries.BLOCK);
        for (Tag t : l) {
            CompoundTag c = (CompoundTag) t;
            parts.add(new Part(NbtUtils.readBlockState(blocks, c.getCompound("S")), new Vec3(c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z")), c.getBoolean("L")));
        }
        dir = new Vec3(tag.getDouble("DX"), 0, tag.getDouble("DZ"));
        if (dir.lengthSqr() < 1e-6) dir = new Vec3(1, 0, 0);
        angle = tag.getDouble("Angle");
        omega = tag.getDouble("Omega");
        bounces = tag.getInt("Bounces");
        crashed = tag.getBoolean("Crashed");
        broken.clear();
        broken.or(BitSet.valueOf(tag.getLongArray("Broken")));
        entityData.set(DATA_ANGLE, (float) angle);
        updateRadius();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Part p : parts) {
            CompoundTag c = new CompoundTag();
            c.put("S", NbtUtils.writeBlockState(p.state));
            c.putDouble("X", p.rel.x);
            c.putDouble("Y", p.rel.y);
            c.putDouble("Z", p.rel.z);
            c.putBoolean("L", p.log);
            l.add(c);
        }
        tag.put("Parts", l);
        tag.putDouble("DX", dir.x);
        tag.putDouble("DZ", dir.z);
        tag.putDouble("Angle", angle);
        tag.putDouble("Omega", omega);
        tag.putInt("Bounces", bounces);
        tag.putBoolean("Crashed", crashed);
        tag.putLongArray("Broken", broken.toLongArray());
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeDouble(dir.x);
        buf.writeDouble(dir.z);
        buf.writeVarInt(parts.size());
        for (Part p : parts) {
            buf.writeVarInt(Block.getId(p.state));
            buf.writeFloat((float) p.rel.x);
            buf.writeFloat((float) p.rel.y);
            buf.writeFloat((float) p.rel.z);
            buf.writeBoolean(p.log);
        }
        buf.writeLongArray(broken.toLongArray());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        dir = new Vec3(buf.readDouble(), 0, buf.readDouble());
        int n = buf.readVarInt();
        parts.clear();
        for (int i = 0; i < n; i++)
            parts.add(new Part(Block.stateById(buf.readVarInt()), new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readBoolean()));
        broken.clear();
        broken.or(BitSet.valueOf(buf.readLongArray()));
        updateRadius();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position()).inflate(radius);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}

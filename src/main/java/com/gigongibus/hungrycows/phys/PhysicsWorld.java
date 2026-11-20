package com.gigongibus.hungrycows.phys;

import com.gigongibus.hungrycows.fell.Felling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * ФІЗИКА ТВЕРДИХ ТІЛ (Fij, 20.11: «реальна фізика дерева, що падає, і шматків, які відрубуєш»). У Майнкрафті такої немає — своя.
 * Кожне тіло (`WoodBodyEntity`) — бруски, що падають під вагою, б'ються об блоки й одне об одне, ковзають і зупиняються тертям,
 * перекидаються через край, підскакують від сильного удару. Удар — розрахунок поштовхів у точках дотику (як у справжніх
 * фізичних рушіях: кілька проходів, тертя Кулона, пружність). Важке й швидке ламає паркани, скло, дошки (не землю й камінь);
 * дерево ламається на стиках, коли згин від удару більший за міцність (гілки навскіс — слабші). Листя м'яке: тримає трохи,
 * під вагою чи від сильного удару ламається. Кого зачепило — б'є (швидкість × вага); кого штовхнуло — відкидає, гравець і моби
 * самі штовхають легкі шматки. Тіло, що лежить нерухомо, «засинає» (не рахується); удар, зламана опора — прокидається.
 */
public final class PhysicsWorld {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final double GRAVITY = 12;          // бл./с² (справжнє 9,8; трохи більше — гра не тягнеться)
    public static final double LOG_MASS = 1.0;        // колода (блок деревини); шматок — частка
    public static final double LEAF_MASS = 0.06;      // листок
    public static final int MIN_SUBSTEPS = 4, MAX_SUBSTEPS = 20;
    public static final double MAX_MOVE = 0.15;       // жодна точка не зсувається більше за підкрок (не проскакує крізь блок)
    public static final int ITERATIONS = 10;          // проходи розрахунку ударів
    public static final double FRICTION = 0.6;        // дерево по землі
    public static final double LEAF_FRICTION = 0.9;   // крона чіпляється
    public static final double RESTITUTION = 0.18;    // пружність удару (дерево трохи відскакує) …
    public static final double RESTITUTION_FROM = 1.5;// … лише від удару швидше 1,5 бл./с
    public static final double BAUMGARTE = 0.2;       // як швидко виштовхує з блока (частка за підкрок)
    public static final double SLOP = 0.005;          // таке занурення не виправляється (не тремтить)
    public static final double MAX_BIAS = 3;          // виштовхує не швидше 3 бл./с
    public static final double AIR_DRAG = 0.02;       // опір повітря (за с)
    public static final double LEAF_DRAG = 1.2;       // крона гальмує в повітрі: × частка маси листя
    public static final double LEAF_R = 0.3;          // листок торкається землі нижче свого центру
    public static final double LEAF_HOLD = 1.5;       // листок тримає не більше такої сили (сам важить ~0,7; крона не тримає стовбур) …
    public static final double LEAF_CRUSH = 0.35;     // … втиснутий глибше — розчавлено
    public static final double LEAF_SNAP = 6.0;       // удар листком швидше 6 бл./с — ламається
    public static final double BREAK_BASE = 2;        // зламати блок коштує енергії удару: 2 + 6 × міцність (паркан 14, скло 4) …
    public static final double BREAK_PER_HARDNESS = 6;  // … (енергія = ½ × маса, що «припадає» на точку удару × швидкість²)
    public static final double JOINT_CHECK = 1.5;     // згин на стиках рахується лише від поштовху сильніше за цей
    public static final double JOINT_STRENGTH = 2200; // міцність стику колод гранню (згин) …
    public static final double JOINT_WEAK = 0.35;     // … навскіс (гілка) — частка від неї
    public static final double HINGE_FREE = 1.3;      // поки на шарнірі — частини ближче до пня не б'ються об землю
    public static final double THIN = 0.6;            // тонші блоки (паркан, шибка) ловляться ще й своїми кутами
    public static final double KICK_MAX = Math.toRadians(15);   // далі 15° зарубка вже не «штовхає»
    public static final int HINGE_STUCK_TICKS = 60;  // дерево сперлося на щось і не хилиться (3 с) — волокна рвуться під вагою
    public static final int HINGE_MAX_TICKS = 300;   // за 15 с — однаково
    public static final double SLEEP_V = 0.08, SLEEP_W = 0.1;   // повільніше — «майже стоїть»
    public static final int SLEEP_TICKS = 15;         // стільки тіків майже стоїть — засинає
    public static final int SUPPORT_CHECK = 10;       // сплячі раз на 0,5 с перевіряють, чи є опора
    public static final double HIT_MIN = 2.5;         // повільніше — не б'є
    public static final double HIT_PER_SPEED = 1.1;   // шкода: 1,1 × швидкість × √(вага/5) …
    public static final double HIT_MAX = 40;
    public static final double LEAF_HIT = 0.2;        // листя б'є слабше
    public static final int HIT_COOLDOWN = 10;        // ту саму істоту — не частіше раз на 0,5 с
    public static final double ENTITY_DENSITY = 0.17; // маса істоти: об'єм рамки × 0,17 (гравець ≈ 0,11 колоди)
    public static final double PUSH_OUT = 0.15;       // істоту, що влізла в брусок, виштовхує з такою швидкістю
    public static final double THUD_SPEED = 3.0;      // удар швидше — гупає (звук, пил)
    public static final double ACTIVE_RANGE = 96;     // далі від усіх гравців — не рахується (спить)
    public static final int MAX_BODIES = 300;         // більше — найстаріші сплячі стають блоками

    public static boolean DEBUG = false, DEBUG_BREAK = false;
    private static final Map<ServerLevel, PhysicsWorld> ALL = new WeakHashMap<>();

    public static PhysicsWorld of(ServerLevel level) {
        return ALL.computeIfAbsent(level, PhysicsWorld::new);
    }

    final ServerLevel level;
    final List<WoodBodyEntity> bodies = new ArrayList<>();
    private final Map<Long, AABB[]> shapes = new HashMap<>();
    private final Map<Long, UUID> lastHitter = new HashMap<>();

    private PhysicsWorld(ServerLevel level) {
        this.level = level;
    }

    public void add(WoodBodyEntity e) {
        if (!bodies.contains(e)) bodies.add(e);
    }

    public void remove(WoodBodyEntity e) {
        bodies.remove(e);
    }

    public List<WoodBodyEntity> bodies() {
        return bodies;
    }

    /** Розбудити тіла, що торкаються місця pos (зламали опору) */
    public void wakeNear(BlockPos pos) {
        AABB box = new AABB(pos).inflate(1.1);
        for (WoodBodyEntity e : bodies) if (e.getBoundingBox().intersects(box)) e.body.asleep = false;
    }

    // ---------- блоки світу ----------

    private AABB[] shapes(int x, int y, int z) {
        long k = BlockPos.asLong(x, y, z);
        AABB[] s = shapes.get(k);
        if (s != null) return s;
        BlockPos p = new BlockPos(x, y, z);
        BlockState st = level.getBlockState(p);
        var shape = st.getCollisionShape(level, p);
        s = shape.isEmpty() ? new AABB[0] : shape.toAabbs().stream().map(b -> b.move(p)).toArray(AABB[]::new);
        shapes.put(k, s);
        return s;
    }

    private boolean solidAt(double x, double y, double z) {
        for (AABB b : shapes((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)))
            if (x > b.minX && x < b.maxX && y > b.minY && y < b.maxY && z > b.minZ && z < b.maxZ) return true;
        return false;
    }

    /** Чим тіло вважає блок: −1 — не зламати (земля, камінь, руда, скрині, пень), інакше скільки енергії коштує зламати */
    double cost(BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        float hard = s.getDestroySpeed(level, pos);
        if (hard < 0 || s.requiresCorrectToolForDrops() || s.hasBlockEntity() || s.is(Felling.FELLING_CUT.get())) return -1;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.FARMLAND)
                || s.is(Blocks.DIRT_PATH) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE)) return -1;
        if (s.is(BlockTags.LOGS)) return -1;                  // інше дерево не ламає — зачіпається за нього
        return BREAK_BASE + BREAK_PER_HARDNESS * hard;
    }

    /** Точка (світ) у блоці: куди виштовхувати (не крізь сусідній блок) і як глибоко; null — не в блоці */
    private double[] worldContact(Vector3d p) {
        int bx = (int) Math.floor(p.x), by = (int) Math.floor(p.y), bz = (int) Math.floor(p.z);
        for (AABB b : shapes(bx, by, bz)) {
            if (!(p.x > b.minX && p.x < b.maxX && p.y > b.minY && p.y < b.maxY && p.z > b.minZ && p.z < b.maxZ)) continue;
            double[] d = { b.maxX - p.x, p.x - b.minX, b.maxY - p.y, p.y - b.minY, b.maxZ - p.z, p.z - b.minZ };
            int[][] dir = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
            int best = -1;
            for (int i = 0; i < 6; i++) {
                double e = d[i] + 0.002;
                if (solidAt(p.x + dir[i][0] * e, p.y + dir[i][1] * e, p.z + dir[i][2] * e)) continue;   // там теж тверде
                if (best < 0 || d[i] < d[best]) best = i;
            }
            if (best < 0) best = 2;                                   // глибоко всередині — угору
            return new double[]{ dir[best][0], dir[best][1], dir[best][2], d[best], bx, by, bz };
        }
        return null;
    }

    // ---------- дотик ----------

    static final class Contact {
        RigidBody a, b;
        int pa = -1;
        final Vector3d p = new Vector3d(), n = new Vector3d();
        double depth;
        boolean leaf;
        BlockPos block;
        final Vector3d ra = new Vector3d(), rb = new Vector3d(), t1 = new Vector3d(), t2 = new Vector3d();
        double mn, mt1, mt2, bias, target, jn, jt1, jt2, vn0, mu, cap;
        boolean dead;
    }

    // ---------- крок світу ----------

    private final Set<RigidBody> brokeThisTick = new HashSet<>();

    public void tick() {
        shapes.clear();
        bodies.removeIf(Entity::isRemoved);
        if (bodies.isEmpty()) return;
        boolean noPlayers = level.players().isEmpty();
        long time = level.getGameTime();
        List<WoodBodyEntity> active = new ArrayList<>();
        for (WoodBodyEntity e : bodies) {
            RigidBody b = e.body;
            if (b.dirty) b.recompute();
            if (b.mass <= 0) continue;
            if (!noPlayers && level.getNearestPlayer(e, ACTIVE_RANGE) == null) continue;
            if (b.asleep && (time + e.getId()) % SUPPORT_CHECK == 0 && !supported(b)) b.asleep = false;
            active.add(e);
        }
        // скільки підкроків: жодна точка не зсувається більше MAX_MOVE
        double maxSpeed = 0;
        for (WoodBodyEntity e : active) {
            RigidBody b = e.body;
            if (!b.asleep) maxSpeed = Math.max(maxSpeed, b.v.length() + b.w.length() * b.radius + GRAVITY * 0.05);
        }
        int sub = (int) Math.max(MIN_SUBSTEPS, Math.min(MAX_SUBSTEPS, Math.ceil(maxSpeed * 0.05 / MAX_MOVE)));
        double dt = 0.05 / sub;
        brokeThisTick.clear();
        for (int s = 0; s < sub; s++) substep(active, dt);
        for (WoodBodyEntity e : active) {
            if (e.body.hinged) e.body.hingeTicks++;
            if (DEBUG && !e.body.asleep && e.body.mass > 3) {
                Vector3d up = e.body.rot.transform(new Vector3d(0, 1, 0));
                System.out.printf("[фізика] %d тіло %d: шарнір %s, нахил %.1f°, w %.2f (вздовж осі %.2f), v %.2f, підкроків %d%n", e.tickCount, e.getId(), e.body.hinged,
                        Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, up.y)))), e.body.w.length(), e.body.w.dot(e.body.axis), e.body.v.length(), sub);
            }
            e.afterPhysics();
        }
        if (bodies.size() > MAX_BODIES) {
            WoodBodyEntity oldest = null;
            for (WoodBodyEntity e : bodies) if (e.body.asleep && (oldest == null || e.tickCount > oldest.tickCount)) oldest = e;
            if (oldest != null) oldest.toBlocks();
        }
    }

    /** Під сплячим тілом є на чому лежати */
    private boolean supported(RigidBody b) {
        List<Contact> cs = new ArrayList<>();
        worldContacts(b, cs);
        for (WoodBodyEntity o : bodies) if (o.body != b) bodyContacts(b, o.body, cs);
        for (Contact c : cs) if (c.n.y > 0.3 && !c.leaf) return true;
        return false;
    }

    private void substep(List<WoodBodyEntity> active, double dt) {
        List<RigidBody> moving = new ArrayList<>();
        for (WoodBodyEntity e : active) if (!e.body.asleep && e.body.mass > 0) moving.add(e.body);
        if (moving.isEmpty()) return;
        Map<RigidBody, Vector3d[]> before = new HashMap<>();
        for (RigidBody b : moving) {
            before.put(b, new Vector3d[]{ new Vector3d(b.v), new Vector3d(b.w) });
            b.v.y -= GRAVITY * dt;
            double drag = AIR_DRAG + LEAF_DRAG * b.leafMass / b.mass;
            b.v.mul(Math.max(0, 1 - drag * dt));
            b.w.mul(Math.max(0, 1 - drag * dt));
            b.updateWorld();
        }
        // дотики
        List<Contact> cs = new ArrayList<>();
        for (RigidBody b : moving) worldContacts(b, cs);
        for (int i = 0; i < active.size(); i++)
            for (int j = i + 1; j < active.size(); j++) {
                RigidBody a = active.get(i).body, b = active.get(j).body;
                if (a.asleep && b.asleep) continue;
                if (!overlap(a, b)) continue;
                int n0 = cs.size();
                bodyContacts(a, b, cs);
                bodyContacts(b, a, cs);
                if (cs.size() > n0) {                                          // торкнулись — прокидаються обидва
                    if (a.asleep) { a.asleep = false; a.still = 0; }
                    if (b.asleep) { b.asleep = false; b.still = 0; }
                }
            }
        prestep(cs, dt);
        // поштовхи
        for (int it = 0; it < ITERATIONS; it++) {
            for (RigidBody b : moving) if (b.hinged) solveHinge(b, dt);
            for (Contact c : cs) if (!c.dead) solve(c);
        }
        if (DEBUG) for (Contact c : cs) if (c.jn > 0.3 && c.a.mass > 3)
            System.out.printf("[фізика]   удар jn %.2f vn0 %.2f глиб %.2f n (%.2f %.2f %.2f) лист %s блок %s част %d %s%n", c.jn, c.vn0, c.depth, c.n.x, c.n.y, c.n.z, c.leaf,
                    c.block == null ? "тіло" : level.getBlockState(c.block).getBlock().getName().getString() + " " + c.block, c.pa,
                    c.a.parts.get(c.pa).kind == Part.PIECE ? "обрубок" : c.a.parts.get(c.pa).state.getBlock().getName().getString());
        for (RigidBody b : moving) if (b.hinged) kick(b, dt);
        for (RigidBody b : moving) b.integrate(dt);
        // згин на стиках — лише від сильного удару
        for (RigidBody b : moving) {
            double maxJ = 0;
            for (Contact c : cs) if (c.a == b && !c.dead) maxJ = Math.max(maxJ, c.jn);
            if (maxJ > JOINT_CHECK && !b.hinged && !brokeThisTick.contains(b)) checkJoints(b, before.get(b), cs, dt);
        }
        for (RigidBody b : moving) if (b.dirty) b.recompute();
    }

    private static boolean overlap(RigidBody a, RigidBody b) {
        double[] x = a.aabb(), y = b.aabb();
        return x[0] < y[3] && y[0] < x[3] && x[1] < y[4] && y[1] < x[4] && x[2] < y[5] && y[2] < x[5];
    }

    private void worldContacts(RigidBody b, List<Contact> out) {
        Vector3d p = new Vector3d();
        for (int i = 0; i < b.parts.size(); i++) {
            Part part = b.parts.get(i);
            if (part.gone) continue;
            if (part.leaf) {
                b.toWorld(part.c.x, part.c.y, part.c.z, p);
                p.y -= LEAF_R;
                double[] r = worldContact(p);
                if (r == null) continue;
                if (r[3] > LEAF_CRUSH) { crushLeaf(b, i, p); continue; }
                add(out, b, null, i, p, r, true);
                continue;
            }
            double[] s = part.samples;
            if (s == null) continue;
            // на пні низ стовбура тримає шарнір (волокна), а не земля — інакше шарнір і земля тягнуть у різні боки
            if (b.hinged && part.c.lengthSquared() < HINGE_FREE * HINGE_FREE) continue;
            for (int k = 0; k < s.length; k += 3) {
                b.toWorld(s[k], s[k + 1], s[k + 2], p);
                double[] r = worldContact(p);
                if (r != null) add(out, b, null, i, p, r, false);
            }
            blockCorners(b, i, out);
        }
    }

    /**
     * Кути блоків, що влізли в брусок (тонкий паркан, ребро сходинки — точки бруска їх можуть «оминути»): виштовхнути брусок
     * від кута крізь найближчу його грань.
     */
    private void blockCorners(RigidBody b, int i, List<Contact> out) {
        Part part = b.parts.get(i);
        Vector3d c = b.toWorld(part.c.x, part.c.y, part.c.z, new Vector3d());
        double[] e = new double[3];
        for (int a = 0; a < 3; a++) e[a] = Math.abs(b.rot.get(0, a)) * part.hx + Math.abs(b.rot.get(1, a)) * part.hy + Math.abs(b.rot.get(2, a)) * part.hz;
        int x0 = (int) Math.floor(c.x - e[0]), x1 = (int) Math.floor(c.x + e[0]), y0 = (int) Math.floor(c.y - e[1]), y1 = (int) Math.floor(c.y + e[1]),
                z0 = (int) Math.floor(c.z - e[2]), z1 = (int) Math.floor(c.z + e[2]);
        if ((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 64) return;
        Vector3d corner = new Vector3d(), lp = new Vector3d();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    for (AABB box : shapes(x, y, z)) {
                        // лише тонке (паркан, стіна, шибка, плита): цілі блоки досить ловлять точки бруска, а кути цілих блоків
                        // штовхали б брусок убік замість угору
                        if (Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) > THIN) continue;
                        for (int k = 0; k < 8; k++) {
                            corner.set((k & 1) == 0 ? box.minX : box.maxX, (k & 2) == 0 ? box.minY : box.maxY, (k & 4) == 0 ? box.minZ : box.maxZ);
                            b.toLocal(corner, lp);
                            double dx = lp.x - part.c.x, dy = lp.y - part.c.y, dz = lp.z - part.c.z;
                            double ex = part.hx - Math.abs(dx), ey = part.hy - Math.abs(dy), ez = part.hz - Math.abs(dz);
                            if (ex <= 0.01 || ey <= 0.01 || ez <= 0.01) continue;     // лише торкається — не удар
                            // кут блока, що торкається іншого блока (у суцільній землі) — нецікавий: його закриває сусід
                            if (solidAt(corner.x + 0.01, corner.y + 0.01, corner.z + 0.01) && solidAt(corner.x - 0.01, corner.y + 0.01, corner.z - 0.01)
                                    && solidAt(corner.x + 0.01, corner.y + 0.01, corner.z - 0.01) && solidAt(corner.x - 0.01, corner.y + 0.01, corner.z + 0.01)) continue;
                            double[] pen = { ex, ey, ez };
                            double[] sg = { Math.signum(dx), Math.signum(dy), Math.signum(dz) };
                            int best = -1;
                            for (int a = 0; a < 3; a++) {
                                // вихід крізь грань, за якою інший брусок цього ж тіла, — не вихід
                                double ox = lp.x + (a == 0 ? sg[0] * (pen[0] + 0.01) : 0), oy = lp.y + (a == 1 ? sg[1] * (pen[1] + 0.01) : 0),
                                        oz = lp.z + (a == 2 ? sg[2] * (pen[2] + 0.01) : 0);
                                boolean inner = false;
                                for (Part o : b.parts) if (o != part && !o.gone && !o.leaf && o.contains(ox, oy, oz, 0)) { inner = true; break; }
                                if (inner) continue;
                                if (best < 0 || pen[a] < pen[best]) best = a;
                            }
                            if (best < 0) continue;
                            Vector3d n = new Vector3d(best == 0 ? -sg[0] : 0, best == 1 ? -sg[1] : 0, best == 2 ? -sg[2] : 0);
                            b.rot.transform(n);
                            add(out, b, null, i, corner, new double[]{ n.x, n.y, n.z, pen[best], x, y, z }, false);
                        }
                    }
    }

    private static void add(List<Contact> out, RigidBody a, RigidBody b, int pa, Vector3d p, double[] r, boolean leaf) {
        Contact c = new Contact();
        c.a = a;
        c.b = b;
        c.pa = pa;
        c.p.set(p);
        c.n.set(r[0], r[1], r[2]);
        c.depth = r[3];
        c.leaf = leaf;
        if (r.length > 4) c.block = new BlockPos((int) r[4], (int) r[5], (int) r[6]);
        out.add(c);
    }

    /** Точки тіла a, що всередині брусків тіла b */
    private void bodyContacts(RigidBody a, RigidBody b, List<Contact> out) {
        Vector3d p = new Vector3d(), lp = new Vector3d();
        double[] bb = b.aabb();
        for (int i = 0; i < a.parts.size(); i++) {
            Part part = a.parts.get(i);
            if (part.gone || part.leaf || part.samples == null) continue;
            double[] s = part.samples;
            for (int k = 0; k < s.length; k += 3) {
                a.toWorld(s[k], s[k + 1], s[k + 2], p);
                if (p.x < bb[0] || p.x > bb[3] || p.y < bb[1] || p.y > bb[4] || p.z < bb[2] || p.z > bb[5]) continue;
                b.toLocal(p, lp);
                for (Part o : b.parts) {
                    if (o.gone || o.leaf || !o.contains(lp.x, lp.y, lp.z, 0)) continue;
                    double[] d = { o.c.x + o.hx - lp.x, lp.x - (o.c.x - o.hx), o.c.y + o.hy - lp.y, lp.y - (o.c.y - o.hy),
                            o.c.z + o.hz - lp.z, lp.z - (o.c.z - o.hz) };
                    int best = -1;
                    for (int f = 0; f < 6; f++) {
                        double e = d[f] + 0.005, sg = f % 2 == 0 ? 1 : -1;
                        double ex = lp.x + (f / 2 == 0 ? sg * e : 0), ey = lp.y + (f / 2 == 1 ? sg * e : 0), ez = lp.z + (f / 2 == 2 ? sg * e : 0);
                        boolean inner = false;
                        for (Part o2 : b.parts) if (o2 != o && !o2.gone && !o2.leaf && o2.contains(ex, ey, ez, 0)) { inner = true; break; }
                        if (inner) continue;
                        if (best < 0 || d[f] < d[best]) best = f;
                    }
                    if (best < 0) continue;
                    double sg = best % 2 == 0 ? 1 : -1;
                    Vector3d n = new Vector3d(best / 2 == 0 ? sg : 0, best / 2 == 1 ? sg : 0, best / 2 == 2 ? sg : 0);
                    b.rot.transform(n);
                    add(out, a, b, i, p, new double[]{ n.x, n.y, n.z, d[best] }, false);
                    break;
                }
            }
        }
    }

    private void prestep(List<Contact> cs, double dt) {
        Vector3d ca = new Vector3d(), cb = new Vector3d(), va = new Vector3d(), vb = new Vector3d();
        Set<BlockPos> broken = new HashSet<>();
        for (Contact c : cs) {
            c.a.comWorld(ca);
            c.ra.set(c.p).sub(ca);
            c.a.velAt(c.p, ca, va);
            if (c.b != null) {
                c.b.comWorld(cb);
                c.rb.set(c.p).sub(cb);
                c.b.velAt(c.p, cb, vb);
            } else vb.zero();
            Vector3d dv = new Vector3d(va).sub(vb);
            c.vn0 = dv.dot(c.n);
            double k = c.a.invMassAlong(c.ra, c.n) + (c.b != null && !c.b.asleep ? c.b.invMassAlong(c.rb, c.n) : 0);
            c.mn = k > 0 ? 1 / k : 0;
            if (Math.abs(c.n.y) < 0.9) c.t1.set(c.n.z, 0, -c.n.x).normalize(); else c.t1.set(1, 0, 0).sub(new Vector3d(c.n).mul(c.n.x)).normalize();
            c.t2.set(c.n).cross(c.t1);
            double k1 = c.a.invMassAlong(c.ra, c.t1) + (c.b != null && !c.b.asleep ? c.b.invMassAlong(c.rb, c.t1) : 0);
            double k2 = c.a.invMassAlong(c.ra, c.t2) + (c.b != null && !c.b.asleep ? c.b.invMassAlong(c.rb, c.t2) : 0);
            c.mt1 = k1 > 0 ? 1 / k1 : 0;
            c.mt2 = k2 > 0 ? 1 / k2 : 0;
            c.bias = Math.min(MAX_BIAS, BAUMGARTE / dt * Math.max(0, c.depth - SLOP));
            c.target = c.vn0 < -RESTITUTION_FROM ? -RESTITUTION * c.vn0 : 0;
            c.mu = c.leaf ? LEAF_FRICTION : FRICTION;
            c.cap = LEAF_HOLD * dt;
            if (c.block != null && broken.contains(c.block)) { c.dead = true; continue; }
            // листок від сильного удару ламається
            if (c.leaf && c.vn0 < -LEAF_SNAP) { crushLeaf(c.a, c.pa, c.p); c.dead = true; continue; }
            // блок, який можна зламати, — ламає, якщо вистачає енергії удару
            if (c.block != null && !c.leaf && c.vn0 < -0.5) {
                double cost = cost(c.block);
                double e = 0.5 * c.mn * c.vn0 * c.vn0;
                if (DEBUG_BREAK && cost >= 0) System.out.printf("[фізика] ламати %s: енергія %.1f, треба %.1f%n", level.getBlockState(c.block).getBlock().getName().getString(), e, cost);
                if (cost >= 0 && e > cost) {
                    BlockState st = level.getBlockState(c.block);
                    level.destroyBlock(c.block, true);
                    shapes.remove(c.block.asLong());
                    broken.add(c.block.immutable());
                    double vn1 = -Math.sqrt(Math.max(0, c.vn0 * c.vn0 - 2 * cost / c.mn));
                    c.a.impulse(new Vector3d(c.n).mul((vn1 - c.vn0) * c.mn), c.p, ca);
                    c.dead = true;
                    if (!st.isAir()) wakeNear(c.block);
                }
            }
            if (c.vn0 < -THUD_SPEED && c.b == null) thud(c);
        }
        for (Contact c : cs) if (c.block != null && broken.contains(c.block)) c.dead = true;
    }

    private void solve(Contact c) {
        RigidBody a = c.a, b = c.b != null && !c.b.asleep ? c.b : null;
        Vector3d ca = a.comWorld(new Vector3d()), cb = b != null ? b.comWorld(new Vector3d()) : null;
        Vector3d dv = a.velAt(c.p, ca, new Vector3d());
        if (b != null) dv.sub(b.velAt(c.p, cb, new Vector3d()));
        double vn = dv.dot(c.n);
        double lambda = (Math.max(c.bias, c.target) - vn) * c.mn;
        double jn = Math.max(0, c.jn + lambda);
        if (c.leaf) jn = Math.min(jn, c.cap);
        lambda = jn - c.jn;
        c.jn = jn;
        Vector3d j = new Vector3d(c.n).mul(lambda);
        a.impulse(j, c.p, ca);
        if (b != null) b.impulse(j.negate(), c.p, cb);
        // тертя
        dv = a.velAt(c.p, ca, new Vector3d());
        if (b != null) dv.sub(b.velAt(c.p, cb, new Vector3d()));
        double l1 = -dv.dot(c.t1) * c.mt1, l2 = -dv.dot(c.t2) * c.mt2;
        double n1 = c.jt1 + l1, n2 = c.jt2 + l2, lim = c.mu * c.jn, len = Math.sqrt(n1 * n1 + n2 * n2);
        if (len > lim && len > 0) { n1 *= lim / len; n2 *= lim / len; }
        l1 = n1 - c.jt1;
        l2 = n2 - c.jt2;
        c.jt1 = n1;
        c.jt2 = n2;
        Vector3d jt = new Vector3d(c.t1).mul(l1).fma(l2, c.t2);
        a.impulse(jt, c.p, ca);
        if (b != null) b.impulse(jt.negate(), c.p, cb);
    }

    // ---------- шарнір на пні ----------

    private void solveHinge(RigidBody b, double dt) {
        // обертання лише довкола осі шарніра
        double wa = b.w.dot(b.axis);
        b.w.set(b.axis).mul(wa);
        Vector3d cw = b.comWorld(new Vector3d());
        Vector3d anchorW = b.toWorld(b.anchor.x, b.anchor.y, b.anchor.z, new Vector3d());
        Vector3d r = new Vector3d(anchorW).sub(cw);
        Vector3d vel = b.velAt(anchorW, cw, new Vector3d());
        Vector3d drift = new Vector3d(anchorW).sub(b.pivot).mul(BAUMGARTE / dt);
        vel.add(drift);
        // K = 1/m·E − [r]× I⁻¹ [r]×
        Matrix3d rx = new Matrix3d(0, r.z, -r.y, -r.z, 0, r.x, r.y, -r.x, 0);
        Matrix3d k = new Matrix3d(rx).mul(b.iWorldInv).mul(rx);
        k.scale(-1);
        k.m00 += b.invMass;
        k.m11 += b.invMass;
        k.m22 += b.invMass;
        Vector3d j = k.invert().transform(vel.negate());
        b.impulse(j, anchorW, cw);
    }

    /**
     * Перші градуси: зарубка не тримає, дерево ледь-ледь хилиться (тріщить) і щоразу швидше, поки центр ваги не перейде через
     * край пня — далі падає само. Хилиться далі hingeRelease — волокна рвуться, дерево відривається від пня.
     */
    private void kick(RigidBody b, double dt) {
        Vector3d cw = b.comWorld(new Vector3d());
        Vector3d up = b.rot.transform(new Vector3d(0, 1, 0));
        double tilt = Math.acos(Math.max(-1, Math.min(1, up.y)));
        if (tilt > b.hingeRelease || b.hingeTicks > HINGE_STUCK_TICKS && b.w.length() < 0.3 || b.hingeTicks > HINGE_MAX_TICKS) {
            b.hinged = false;
            level.playSound(null, BlockPos.containing(b.pivot.x, b.pivot.y, b.pivot.z), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                    SoundSource.BLOCKS, 1F, 0.6F);
            return;
        }
        double ahead = (cw.x - b.pivot.x) * b.fallDir.x + (cw.z - b.pivot.z) * b.fallDir.z;
        if (ahead > 0.05 || tilt > KICK_MAX) return;               // центр ваги вже над краєм — падає само
        b.kickOmega = Math.max(Felling.START_OMEGA, b.kickOmega + Felling.KICK_ALPHA * dt);
        double wa = b.w.dot(b.axis);
        if (wa < b.kickOmega) {
            b.w.set(b.axis).mul(b.kickOmega);
            Vector3d r = new Vector3d(cw).sub(b.pivot);
            b.v.set(new Vector3d(b.w).cross(r));
        }
    }

    // ---------- згин і злам ----------

    /**
     * Чи не зламалось тіло на якомусь стику: для кожного «містка» (стик, без якого тіло розпадається) — яку силу згину мусив
     * передати стик, щоб частина за ним рухалась разом з тілом (зміна її моменту імпульсу мінус удари й вага на неї). Більше
     * міцності — ламається: частина відлітає окремим тілом.
     */
    private void checkJoints(RigidBody b, Vector3d[] before, List<Contact> cs, double dt) {
        int n = b.parts.size();
        int[] disc = new int[n], low = new int[n], par = new int[n], size = new int[n];
        java.util.Arrays.fill(disc, -1);
        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int e = 0; e < b.ea.length; e++) {
            if (b.parts.get(b.ea[e]).gone || b.parts.get(b.eb[e]).gone) continue;
            adj.get(b.ea[e]).add(new int[]{ b.eb[e], e });
            adj.get(b.eb[e]).add(new int[]{ b.ea[e], e });
        }
        int root = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Part p = b.parts.get(i);
            if (p.gone || p.leaf) continue;
            double d = p.c.distanceSquared(b.com);
            if (d < best) { best = d; root = i; }
        }
        if (root < 0) return;
        // DFS без рекурсії
        int[] order = new int[n];
        int[] parEdge = new int[n];
        int t = 0;
        java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
        st.push(new int[]{ root, -1, 0 });
        disc[root] = low[root] = t;
        order[t++] = root;
        par[root] = -1;
        parEdge[root] = -1;
        while (!st.isEmpty()) {
            int[] f = st.peek();
            int u = f[0];
            if (f[2] < adj.get(u).size()) {
                int[] e = adj.get(u).get(f[2]++);
                int v = e[0];
                if (e[1] == parEdge[u]) continue;
                if (disc[v] < 0) {
                    disc[v] = low[v] = t;
                    order[t++] = v;
                    par[v] = u;
                    parEdge[v] = e[1];
                    st.push(new int[]{ v, e[1], 0 });
                } else low[u] = Math.min(low[u], disc[v]);
            } else {
                st.pop();
                size[u] += 1;
                if (par[u] >= 0) {
                    low[par[u]] = Math.min(low[par[u]], low[u]);
                    size[par[u]] += size[u];
                }
            }
        }
        Vector3d cw = b.comWorld(new Vector3d());
        Vector3d dV = new Vector3d(b.v).sub(before[0]), dW = new Vector3d(b.w).sub(before[1]);
        double worst = 1;
        int worstChild = -1;
        Vector3d pw = new Vector3d(), tmp = new Vector3d();
        for (int i = 0; i < n; i++) {
            if (i == root || disc[i] < 0 || low[i] <= disc[par[i]]) continue;     // не місток
            int lo = disc[i], hi = disc[i] + size[i];
            Part pc = b.parts.get(i), pp = b.parts.get(par[i]);
            Vector3d joint = b.toWorld((pc.c.x + pp.c.x) / 2, (pc.c.y + pp.c.y) / 2, (pc.c.z + pp.c.z) / 2, new Vector3d());
            Vector3d dL = new Vector3d();
            for (int k = 0; k < n; k++) {
                Part p = b.parts.get(k);
                if (p.gone) continue;
                int o = b.owner.length > k ? b.owner[k] : k;
                if (disc[o] < lo || disc[o] >= hi) continue;
                b.toWorld(p.c.x, p.c.y, p.c.z, pw);
                Vector3d r = new Vector3d(pw).sub(joint);
                Vector3d dv = new Vector3d(dW).cross(tmp.set(pw).sub(cw)).add(dV);
                dv.y += GRAVITY * dt;                                     // вага — не з стику
                dL.add(r.cross(dv).mul(p.mass));
            }
            for (Contact c : cs) {
                if (c.a != b || c.dead || c.pa < 0) continue;
                int o = b.owner.length > c.pa ? b.owner[c.pa] : c.pa;
                if (disc[o] < lo || disc[o] >= hi) continue;
                Vector3d jc = new Vector3d(c.n).mul(c.jn).fma(c.jt1, c.t1).fma(c.jt2, c.t2);
                dL.sub(new Vector3d(c.p).sub(joint).cross(jc));
            }
            double m = dL.length() / dt;
            boolean wk = false;
            for (int e = 0; e < b.ea.length; e++)
                if (b.ea[e] == i && b.eb[e] == par[i] || b.eb[e] == i && b.ea[e] == par[i]) { wk = b.weak[e]; break; }
            double ratio = m / (JOINT_STRENGTH * (wk ? JOINT_WEAK : 1));
            if (ratio > worst) { worst = ratio; worstChild = i; }
        }
        if (worstChild < 0) return;
        BitSet side = new BitSet();
        int lo = disc[worstChild], hi = disc[worstChild] + size[worstChild];
        for (int k = 0; k < n; k++) {
            if (b.parts.get(k).gone) continue;
            int o = b.owner.length > k ? b.owner[k] : k;
            if (disc[o] >= lo && disc[o] < hi) side.set(k);
        }
        WoodBodyEntity owner = entityOf(b);
        if (owner == null) return;
        brokeThisTick.add(b);
        Part pc = b.parts.get(worstChild);
        Vector3d at = b.toWorld(pc.c.x, pc.c.y, pc.c.z, new Vector3d());
        RigidBody piece = b.split(side);
        owner.partsChanged();
        WoodBodyEntity e = new WoodBodyEntity(level, piece, owner.kind);
        level.addFreshEntity(e);
        add(e);
        level.playSound(null, BlockPos.containing(at.x, at.y, at.z), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.3F, 0.7F);
        level.playSound(null, BlockPos.containing(at.x, at.y, at.z), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        BlockState chips = pc.state != null ? pc.state : Blocks.OAK_LOG.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chips), at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.1);
    }

    private WoodBodyEntity entityOf(RigidBody b) {
        for (WoodBodyEntity e : bodies) if (e.body == b) return e;
        return null;
    }

    // ---------- листя ----------

    private void crushLeaf(RigidBody b, int i, Vector3d at) {
        Part p = b.parts.get(i);
        if (p.gone) return;
        p.gone = true;
        b.dirty = true;
        WoodBodyEntity e = entityOf(b);
        if (e != null) e.partsChanged();
        BlockPos pos = BlockPos.containing(at.x, at.y + LEAF_R, at.z);
        if (p.state != null && p.state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock && level.random.nextFloat() < 0.5F)
            Block.dropResources(p.state, level, pos, null);
        if (p.state != null && level.random.nextInt(3) == 0)
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, p.state), at.x, at.y + LEAF_R, at.z, 4, 0.3, 0.3, 0.3, 0.05);
    }

    // ---------- гупання ----------

    private void thud(Contact c) {
        WoodBodyEntity e = entityOf(c.a);
        if (e == null) return;
        double energy = -c.vn0 * c.a.mass;
        e.impact(c.p, -c.vn0, energy, c.block);
    }

    // ---------- істоти ----------

    /** Після кроку: хто в брусках — удар (якщо тіло швидке) і виштовхнути; хто йде на легке тіло — штовхає його */
    void entities(WoodBodyEntity owner) {
        RigidBody b = owner.body;
        double[] bb = b.aabb();
        AABB box = new AABB(bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]).inflate(0.1);
        List<Entity> list = level.getEntities(owner, box, x -> x instanceof LivingEntity || x instanceof net.minecraft.world.entity.item.ItemEntity);
        if (list.isEmpty()) return;
        Vector3d cw = b.comWorld(new Vector3d()), lp = new Vector3d(), pw = new Vector3d(), vp = new Vector3d();
        double weight = Math.max(0.5, Math.min(3, Math.sqrt(b.mass / 5)));
        for (Entity en : list) {
            if (en instanceof Player pl && pl.isSpectator()) continue;
            AABB eb = en.getBoundingBox();
            double hw = eb.getXsize() / 2, hh = eb.getYsize() / 2;
            Vector3d center = new Vector3d(eb.getCenter().x, eb.getCenter().y, eb.getCenter().z);
            b.toLocal(center, lp);
            for (int i = 0; i < b.parts.size(); i++) {
                Part p = b.parts.get(i);
                if (p.gone) continue;
                double ex = Math.abs(lp.x - p.c.x) - p.hx, ey = Math.abs(lp.y - p.c.y) - p.hy, ez = Math.abs(lp.z - p.c.z) - p.hz;
                double reach = Math.max(hw, hh * 0.8);
                if (ex > reach || ey > reach || ez > reach) continue;
                if (p.leaf && b.v.lengthSquared() < 1) continue;            // листя крізь себе пропускає
                // куди виштовхувати: найменше занурення
                int ax = ex > ey && ex > ez ? 0 : ey > ez ? 1 : 2;
                double sg = (ax == 0 ? lp.x - p.c.x : ax == 1 ? lp.y - p.c.y : lp.z - p.c.z) >= 0 ? 1 : -1;
                Vector3d nrm = b.rot.transform(new Vector3d(ax == 0 ? sg : 0, ax == 1 ? sg : 0, ax == 2 ? sg : 0));
                b.toWorld(p.c.x, p.c.y, p.c.z, pw);
                b.velAt(center, cw, vp);
                double speed = vp.length();
                Vec3 ev = en.getDeltaMovement();
                if (en instanceof Player) ev = new Vec3(en.getX() - en.xo, en.getY() - en.yo, en.getZ() - en.zo);
                Vector3d evd = new Vector3d(ev.x * 20, ev.y * 20, ev.z * 20);
                // удар
                if (speed > HIT_MIN && en instanceof LivingEntity le && !(en instanceof Player pl && pl.isCreative())) {
                    long now = level.getGameTime();
                    String key = en.getUUID() + ":" + owner.getId();
                    Long last = hitTimes.get(key);
                    if (last == null || now - last >= HIT_COOLDOWN) {
                        double rel = new Vector3d(vp).sub(evd).length();
                        float dmg = (float) Math.min(HIT_MAX, HIT_PER_SPEED * rel * weight * (p.leaf ? LEAF_HIT : 1));
                        if (dmg >= 1) {
                            hitTimes.put(key, now);
                            le.hurt(level.damageSources().fallingBlock(owner), dmg);
                        }
                    }
                }
                if (p.leaf) break;
                // виштовхнути і передати рух тіла
                Vec3 push = new Vec3(nrm.x * PUSH_OUT, Math.max(0, nrm.y) * PUSH_OUT, nrm.z * PUSH_OUT);
                if (speed > 0.5) push = push.add(vp.x * 0.05 * 0.6, Math.max(0, vp.y) * 0.05 * 0.6 + 0.05, vp.z * 0.05 * 0.6);
                en.setDeltaMovement(en.getDeltaMovement().add(push));
                en.hurtMarked = true;
                // істота штовхає тіло (легкі шматки котяться від ноги)
                double into = -(evd.x - vp.x) * nrm.x - (evd.y - vp.y) * nrm.y - (evd.z - vp.z) * nrm.z;
                if (into > 0.3 && !(en instanceof net.minecraft.world.entity.item.ItemEntity)) {
                    double em = eb.getXsize() * eb.getYsize() * eb.getZsize() * ENTITY_DENSITY;
                    Vector3d j = new Vector3d(nrm).mul(-into * em);
                    j.y = Math.max(j.y, 0);
                    Vector3d at = new Vector3d(center).fma(-0.3, nrm);
                    b.impulse(j, at, cw);
                    if (b.asleep && j.length() * b.invMass > 0.05) { b.asleep = false; b.still = 0; }
                }
                break;
            }
        }
    }

    private final Map<String, Long> hitTimes = new HashMap<>();

    /** Трава, квіти, сніг на шляху колод — трощить */
    void crushPlants(WoodBodyEntity owner) {
        RigidBody b = owner.body;
        if (b.v.lengthSquared() + b.w.lengthSquared() * b.radius * b.radius < 0.25) return;
        Vector3d p = new Vector3d();
        int n = 0;
        for (Part part : b.parts) {
            if (part.gone || part.leaf || part.samples == null) continue;
            for (int k = 0; k < part.samples.length && n < 24; k += 3) {
                b.toWorld(part.samples[k], part.samples[k + 1], part.samples[k + 2], p);
                BlockPos pos = BlockPos.containing(p.x, p.y, p.z);
                BlockState s = level.getBlockState(pos);
                if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                if (s.canBeReplaced() || s.getCollisionShape(level, pos).isEmpty() && s.getDestroySpeed(level, pos) >= 0 && !s.hasBlockEntity()) {
                    level.destroyBlock(pos, true);
                    n++;
                }
            }
        }
    }

    /** Чи стоїть тут сплячий бік (для тестів) */
    public static Vec3 vec(Vector3d v) {
        return new Vec3(v.x, v.y, v.z);
    }
}

package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowCharacter.Experience;
import com.gigongibus.hungrycows.CowHome;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

// Плавання (замість ванільного FloatGoal). Корова добре плаває, але воду не любить:
//  - тримається на воді й пливе до найближчого берега;
//  - у воді швидко втомлюється, а з часом нервується (страх росте);
//  - теля плаває гірше: повільніше й швидше втомлюється;
//  - утома 70+ — сил немає: перестає триматися на воді й тоне (далі ванільне задихання);
//  - якщо гравець, що тримає її на повідку, витягне таку корову на берег — довіра й прив'язаність до людей ростуть.
public class SwimGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final float KEEP_AFLOAT_CHANCE = 0.8F;   // щотіку з таким шансом відштовхується вгору (як у ванільній)
    private static final int SHORE_RADIUS = 12;             // шукає берег до 12 блоків
    private static final int SHORE_RECHECK_TICKS = 200;     // обраного берега тримається; шукає заново раз на 10 с або коли шлях скінчився
    private static final double SWIM_SPEED = 1.0;           // швидкість до берега (теля — CALF_SPEED)
    private static final double CALF_SPEED = 0.7;
    private static final int FATIGUE_PER_SECOND = 3;        // утома у воді: +3 щосекунди (звичайно — +1 за ~10 с)
    private static final double CALF_FATIGUE = 1.5;         // теля втомлюється у 1,5 раза швидше
    private static final int SINK_AT_FATIGUE = 70;          // з такої втоми тоне
    private static final int CALM_SECONDS = 5;              // перші 5 с у воді спокійна ...
    private static final int FEAR_PER_SECOND = 4;           // ... потім страх +4 щосекунди (звичайне заспокоєння −1…−3, тож росте) ...
    private static final int MAX_WATER_FEAR = 50;           // ... але від самої води не більше 50
    private static final int RESCUE_TRUST = 30;             // витягнув на повідку тонучу корову: довіра +30
    private static final float DRAGGED_AFLOAT_CHANCE = 0.4F; // тонуча на повідку: гравець тягне, вона трохи допомагає
    // Гребіння: ванільна вода часом майже не пускає корову вперед (залежить від того, як глибоко вона занурена —
    // а це від гена розміру). Тому корова сама гребе до наступної точки шляху, якщо пливе повільніше за свою швидкість.
    private static final double PADDLE_SPEED = 0.02;        // своя швидкість плавання: 0,02 блока/тік ≈ 0,4 блока/с
    private static final double CALF_PADDLE = 0.7;          // теля пливе повільніше
    private static final double PADDLE_PUSH = 0.25;         // за тік надолужує чверть нестачі швидкості
    private static final int OUT_OF_WATER_RESET = 60;       // «скільки вже у воді» обнуляється, лише якщо вийшла з води на 3+ с

    private final HungryCow cow;
    private int ticksInWater = 0;
    private int lastSwimTick = -100000;
    private int nextShoreSearch = 0;
    private BlockPos shore = null;
    private boolean sinking = false;     // утома 70+: тоне
    private UUID rescuer = null;         // гравець, що тримав її на повідку, поки тонула

    public SwimGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        cow.getNavigation().setCanFloat(true);   // шлях може йти по воді
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean inDeepFluid() {
        return (cow.isInWater() && cow.getFluidHeight(FluidTags.WATER) > cow.getFluidJumpThreshold()) || cow.isInLava();
    }

    // Пливе = у воді й не торкається дна (спливла — вода лише до колін, але дна не торкається), або голова під водою, або в лаві.
    // Стоїть на дні, а голова над водою — бреде по мілкому: це не плавання (ходить як звичайно і не тоне)
    private boolean swimming() {
        return (cow.isInWater() && !cow.onGround()) || cow.isEyeInFluid(FluidTags.WATER) || cow.isInLava();
    }

    @Override
    public boolean canUse() {
        return swimming();
    }

    @Override
    public void start() {
        // на мить стала на край і знову у воді — це те саме купання: утома й страх рахуються далі
        if (cow.tickCount - lastSwimTick > OUT_OF_WATER_RESET) {
            ticksInWater = 0;
            sinking = false;
            rescuer = null;
        }
        nextShoreSearch = 0;
        shore = null;
    }

    @Override
    public void tick() {
        ticksInWater++;
        lastSwimTick = cow.tickCount;
        boolean calf = cow.isBaby();
        // щосекунди: утома й страх
        if (ticksInWater % 20 == 0 && cow.isInWater()) {
            int tired = (int) Math.round(FATIGUE_PER_SECOND * (calf ? CALF_FATIGUE : 1.0));
            cow.fatigue += tired;
            if (ticksInWater / 20 > CALM_SECONDS && cow.fear < MAX_WATER_FEAR) {
                cow.fear = Math.min(MAX_WATER_FEAR, cow.fear + FEAR_PER_SECOND);
            }
        }
        // сил немає — тоне (не відштовхується й не пливе); тримає її на повідку гравець — запам'ятати його
        if (cow.fatigue >= SINK_AT_FATIGUE && cow.isInWater()) {
            if (!sinking) {
                sinking = true;
                cow.tell(cow.family.name + " виснажилась у воді й тоне!");
                cow.episodes.remember(com.gigongibus.hungrycows.CowEpisodes.Kind.NEAR_DROWN, "вода");
            }
            Entity holder = cow.getLeashHolder();
            if (holder instanceof Player p) {
                // тримає на повідку гравець: тягне її (рух від повідка не зупиняємо), вона трохи допомагає
                rescuer = p.getUUID();
                if (cow.getRandom().nextFloat() < DRAGGED_AFLOAT_CHANCE) cow.getJumpControl().jump();
            } else {
                cow.getNavigation().stop();
            }
            return;
        }
        // тримається на воді (відштовхується, лише коли вода глибока, як у ванільній)
        if (inDeepFluid() && cow.getRandom().nextFloat() < KEEP_AFLOAT_CHANCE) cow.getJumpControl().jump();
        // пливе до берега
        if (cow.isInLava()) return;
        if (shore == null || cow.tickCount >= nextShoreSearch) {
            nextShoreSearch = cow.tickCount + SHORE_RECHECK_TICKS;
            shore = findShore();
        }
        if (shore != null && (ticksInWater == 1 || cow.getNavigation().isDone())) {
            cow.getNavigation().moveTo(shore.getX() + 0.5, shore.getY(), shore.getZ() + 0.5, calf ? CALF_SPEED : SWIM_SPEED);
        }
        if (cow.isInWater()) paddle(calf);
    }

    // Гребе до наступної точки шляху (або до берега), якщо пливе повільніше за свою швидкість
    private void paddle(boolean calf) {
        net.minecraft.world.level.pathfinder.Path path = cow.getNavigation().getPath();
        net.minecraft.world.phys.Vec3 to;
        if (path != null && !path.isDone()) to = path.getNextEntityPos(cow);
        else if (shore != null) to = net.minecraft.world.phys.Vec3.atBottomCenterOf(shore);
        else return;
        double dx = to.x - cow.getX(), dz = to.z - cow.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return;
        dx /= len;
        dz /= len;
        net.minecraft.world.phys.Vec3 v = cow.getDeltaMovement();
        double along = v.x * dx + v.z * dz;                     // уже пливе в той бік із такою швидкістю
        double want = PADDLE_SPEED * (calf ? CALF_PADDLE : 1.0);
        if (along >= want) return;
        double push = (want - along) * PADDLE_PUSH;
        cow.setDeltaMovement(v.add(dx * push, 0, dz * push));
    }

    // Найближче сухе місце, де можна стати: кільцями від корови назовні, перше кільце з берегом — найближче в ньому
    private BlockPos findShore() {
        BlockPos o = cow.blockPosition();
        for (int r = 1; r <= SHORE_RADIUS; r++) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;   // лише край кільця
                    for (int dy = 2; dy >= -2; dy--) {
                        BlockPos p = o.offset(dx, dy, dz);
                        if (!cow.level().isLoaded(p)) break;
                        if (!CowHome.standable(cow.level(), p)) continue;   // standable не пускає у воду
                        double d = p.distSqr(o);
                        if (d < bestD) {
                            bestD = d;
                            best = p;
                        }
                        break;
                    }
                }
            }
            if (best != null) return best.immutable();
        }
        return null;
    }

    @Override
    public void stop() {
        // вийшла з води після того, як тонула, і її тримав на повідку гравець — він її врятував
        if (sinking && rescuer != null && !swimming() && cow.getLeashHolder() instanceof Player p
                && p.getUUID().equals(rescuer)) {
            cow.addTrust(p, RESCUE_TRUST, "витягнув з води", false);
            cow.experience(Experience.RESCUED_FROM_WATER, 0);
            cow.episodes.remember(com.gigongibus.hungrycows.CowEpisodes.Kind.RESCUED, p.getName().getString());
            sinking = false;
            rescuer = null;
        }
        shore = null;
    }
}

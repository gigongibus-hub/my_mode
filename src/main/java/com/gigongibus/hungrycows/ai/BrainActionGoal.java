package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Прості дії мозку (етап 8): нюхати повітря, прислухатися, мукати, кликати теля, чесатися об паркан/дерево, повернутися додому.
 * Решту нових дій виконують наявні цілі: вилизувати подругу (GroomGoal), лизати сіль (LickSaltGoal), дослідити нове
 * (InvestigateGoal), підійти погладитися (ApproachPlayerGoal).
 */
public class BrainActionGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int SNIFF_TICKS = 60;      // нюхає повітря 3 с
    private static final int LISTEN_TICKS = 60;     // прислухається 3 с
    private static final int MOO_TICKS = 40;        // мукає й стоїть 2 с
    private static final int CALL_TICKS = 40;
    private static final int SCRATCH_TICKS = 100;   // чешеться 5 с
    private static final int HOME_TICKS = 600;      // іде додому щонайдовше 30 с
    private static final double HOME_NEAR = 4;
    private static final double POST_REACH = 1.8;
    private static final double ITCH_PER_TICK = 1.0;  // треться — свербіж −1 за тік (100 → 0 за 5 с)

    private final HungryCow cow;
    private CowBrain.Action doing;
    private int ticks;
    private BlockPos post;

    public BrainActionGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private static boolean mine(CowBrain.Action a) {
        return a == CowBrain.Action.SNIFF || a == CowBrain.Action.LISTEN || a == CowBrain.Action.MOO
                || a == CowBrain.Action.CALL_CALF || a == CowBrain.Action.SCRATCH || a == CowBrain.Action.GO_HOME;
    }

    @Override
    public boolean canUse() {
        if (!CowBrain.enabled || cow.sleeping || cow.heat.unconscious) return false;
        CowBrain.Action a = cow.brain.current();
        if (!mine(a)) return false;
        if (a == CowBrain.Action.SCRATCH && (post = cow.touch.findScratchPost()) == null) return false;
        if (a == CowBrain.Action.GO_HOME && cow.home.home() == null) return false;
        doing = a;
        return true;
    }

    private int limit() {
        return switch (doing) {
            case SNIFF -> SNIFF_TICKS;
            case LISTEN -> LISTEN_TICKS;
            case MOO -> MOO_TICKS;
            case CALL_CALF -> CALL_TICKS;
            case SCRATCH -> SCRATCH_TICKS + 200;
            default -> HOME_TICKS;
        };
    }

    @Override
    public boolean canContinueToUse() {
        // почала тертися об паркан — дочухується до кінця, навіть якщо мозок уже вибрав інше (до 5 с)
        boolean rubbing = doing == CowBrain.Action.SCRATCH && post != null && cow.distanceToSqr(Vec3.atCenterOf(post)) <= POST_REACH * POST_REACH;
        return doing != null && (cow.brain.is(doing) || rubbing) && ticks < limit() && !cow.sleeping;
    }

    @Override
    public void start() {
        ticks = 0;
        switch (doing) {
            case SNIFF -> cow.status("нюхає повітря");
            case LISTEN -> cow.status("прислухається");
            case MOO -> {
                cow.playSound(SoundEvents.COW_AMBIENT, 1.2F, 0.9F + cow.getRandom().nextFloat() * 0.2F);
                cow.status("мукає");
            }
            case CALL_CALF -> {
                cow.playSound(SoundEvents.COW_AMBIENT, 2.0F, 0.75F);   // низько й гучно: теля чує й іде
                cow.status("кличе теля");
            }
            case SCRATCH -> cow.status("чешеться");
            case GO_HOME -> cow.status("іде додому");
            default -> { }
        }
    }

    @Override
    public void tick() {
        if (doing == null) return;
        ticks++;
        switch (doing) {
            case SNIFF -> {
                cow.getNavigation().stop();
                // піднімає й крутить голову, ніби нюхає вітер
                double a = ticks * 0.15;
                cow.getLookControl().setLookAt(cow.getX() + Math.cos(a) * 3, cow.getEyeY() + 0.6, cow.getZ() + Math.sin(a) * 3);
            }
            case LISTEN -> cow.getNavigation().stop();
            case MOO, CALL_CALF -> cow.getNavigation().stop();
            case SCRATCH -> {
                if (post == null) return;
                Vec3 c = Vec3.atCenterOf(post);
                if (cow.distanceToSqr(c) > POST_REACH * POST_REACH) {
                    if (ticks % 10 == 1) cow.getNavigation().moveTo(c.x, c.y, c.z, 0.9);
                    return;
                }
                cow.getNavigation().stop();
                cow.getLookControl().setLookAt(c.x, c.y, c.z);
                if (ticks % 20 == 0 && cow.level() instanceof ServerLevel s)
                    s.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0);
                if (ticks % 30 == 0) cow.setYBodyRot(cow.yBodyRot + (cow.getRandom().nextBoolean() ? 8 : -8));   // треться боком
                cow.touch.itch = Math.max(0, cow.touch.itch - ITCH_PER_TICK);   // свербіж спадає, поки треться
                if (cow.touch.itch <= 0) {
                    cow.touch.scratched();
                    ticks = limit();
                }
            }
            case GO_HOME -> {
                BlockPos h = cow.home.home();
                if (h == null) return;
                if (ticks % 20 == 1) cow.getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 1.0);
                if (cow.blockPosition().distSqr(h) < HOME_NEAR * HOME_NEAR) ticks = limit();
            }
            default -> { }
        }
    }

    @Override
    public void stop() {
        doing = null;
        cow.getNavigation().stop();
    }
}

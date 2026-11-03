package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowTrust;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;

// Корова з довірою від +50 сама підходить до гравця, коли спокійна.
// Корова з довірою +200 (вигодувана гравцем) ходить біля нього весь час, поки він поруч (у загоні).
public class ApproachPlayerGoal extends Goal {
    private static final double SEE_RANGE = 12.0;     // з якої відстані помічає друга
    private static final double STOP_DISTANCE = 3.0;  // на скільки блоків підходить
    private static final int STAY_TICKS = 200;        // скільки стоїть поруч (10 сек)
    private static final int PAUSE_TICKS = 400;       // пауза перед наступним підходом (20 сек)
    private static final int BOND_PAUSE_TICKS = 40;   // для корови з довірою 200 (2 сек)
    private static final double BOND_RANGE = 16.0;    // з якої відстані корова з довірою 200 іде до гравця
    private final HungryCow cow;
    private Player player;
    private int stayTicks = 0;
    private int pauseUntil = 0;

    public ApproachPlayerGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // викликати tick() кожного тіку, а не раз на два
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // корова спокійна: не голодна, не втомлена, не подавилась
    private boolean calm() {
        return cow.hunger < 30 && cow.fatigue < 40 && !cow.choking;
    }

    @Override
    public boolean canUse() {
        if (cow.tickCount < pauseUntil || !calm()) return false;
        player = findFriend();
        return player != null;
    }

    @Override
    public boolean canContinueToUse() {
        return calm()
                && player != null
                && player.isAlive()
                && cow.trustOf(player) >= CowTrust.FRIEND_FROM
                && cow.distanceToSqr(player) < 16.0 * 16.0
                && (stayTicks < STAY_TICKS || bonded());
    }

    @Override
    public void start() {
        stayTicks = 0;
    }

    @Override
    public void tick() {
        if (player == null) return;
        cow.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (cow.distanceToSqr(player) > STOP_DISTANCE * STOP_DISTANCE) {
            if (cow.tickCount % 10 == 0) {
                cow.getNavigation().moveTo(player, 1.0);
            }
        } else {
            cow.getNavigation().stop();
            stayTicks++;
        }
    }

    @Override
    public void stop() {
        boolean wasBonded = bonded();
        player = null;
        cow.getNavigation().stop();
        stayTicks = 0;
        pauseUntil = cow.tickCount + (wasBonded ? BOND_PAUSE_TICKS : PAUSE_TICKS);
    }

    private boolean bonded() {
        return player != null && cow.trustOf(player) >= CowTrust.BOND_FROM;
    }

    // Найближчий видимий гравець, якому корова довіряє
    private Player findFriend() {
        Player best = null;
        double bestDist = SEE_RANGE * SEE_RANGE;
        for (Player p : cow.level().players()) {
            if (p.isSpectator() || p.hasEffect(MobEffects.INVISIBILITY)) continue;
            int t = cow.trustOf(p);
            if (t < CowTrust.FRIEND_FROM) continue;
            double d = cow.distanceToSqr(p);
            if (t >= CowTrust.BOND_FROM && d < BOND_RANGE * BOND_RANGE) return p;   // найкращий друг важливіший
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}

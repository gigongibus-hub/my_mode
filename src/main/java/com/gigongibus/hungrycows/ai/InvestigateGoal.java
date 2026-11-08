package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

// Допитливість: корова обережно підходить до незнайомого гравця (не друга й не ворога), зупиняється за 4 блоки й роздивляється
public class InvestigateGoal extends Goal {
    private static final double STOP_DISTANCE = 4.0;
    private static final double SPEED = 0.7;            // повільно, обережно
    private static final int MAX_TICKS = 300;
    private static final int NOVEL_TICKS = 160;        // нове роздивляється 8 с           // роздивляється до 15 с
    private final HungryCow cow;
    private net.minecraft.world.entity.LivingEntity target;
    private boolean novel;   // етап 7: роздивляється нову істоту (не гравця)
    private static final double NOVEL_RANGE = 10;      // нову істоту помічає до 10 бл. (якщо бачить)
    private int ticks = 0;

    public InvestigateGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        boolean curious = cow.brain.is(CowBrain.Action.CURIOUS), explore = cow.brain.is(CowBrain.Action.EXPLORE);
        if (!curious && !explore) return false;
        target = curious ? cow.brain.stranger() : null;
        novel = false;
        if (target == null && explore) {   // етап 8: «дослідити нове» — окрема дія мозку
            // етап 7: нове (такого ще не бачила) трохи приємне — підходить роздивитись; схоже на небезпечне — ні
            for (net.minecraft.world.entity.LivingEntity e : cow.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    cow.getBoundingBox().inflate(NOVEL_RANGE), e -> !(e instanceof HungryCow) && !(e instanceof Player) && e.isAlive()
                            && !cow.isScary(e) && cow.vision.canSee(e) && cow.memory.isNovel(e))) {
                target = e;
                novel = true;
                break;
            }
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return (cow.brain.is(CowBrain.Action.CURIOUS) || cow.brain.is(CowBrain.Action.EXPLORE)) && target != null && target.isAlive()
                && !target.isSpectator() && ticks < MAX_TICKS && (!novel || ticks < NOVEL_TICKS);
    }

    @Override
    public void start() {
        ticks = 0;
        if (novel) {
            cow.memory.onInvestigate(target);
            cow.tell(cow.family.name + " роздивляється нове: " + target.getName().getString());
        }
    }

    @Override
    public void tick() {
        if (target == null) return;
        ticks++;
        cow.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (cow.distanceToSqr(target) > STOP_DISTANCE * STOP_DISTANCE) {
            if (ticks == 1 || ticks % 10 == 0) cow.getNavigation().moveTo(target, SPEED);
        } else {
            cow.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        if (novel && target != null && ticks >= NOVEL_TICKS / 2) cow.memory.doneInvestigating(target);
        target = null;
        cow.getNavigation().stop();
    }
}

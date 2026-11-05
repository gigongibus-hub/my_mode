package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

public class RestGoal extends Goal {
    // з якої втоми йде відпочивати: HungryCow.wantsRest() (відчуття втоми ≥ 40 або справжня ≥ 100)
    private static final int RECOVER_PER_SECOND = 5;  // на скільки зменшується втома за секунду відпочинку
    private static final int MAX_REST_TICKS = 400;    // максимум відпочинку за раз (20 сек)
    private final HungryCow cow;
    private int restTimer = 0;

    public RestGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // викликати tick() кожного тіку, а не раз на два
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // втомлена корова з режимом розмноження не відпочиває: втома не заважає розмноженню
    @Override
    public boolean canUse() {
        return cow.wantsRest() && !cow.cycle.inEstrus();
    }

    @Override
    public boolean canContinueToUse() {
        return cow.fatigue > 0 && restTimer < MAX_REST_TICKS && !cow.cycle.inEstrus();
    }

    @Override
    public void start() {
        restTimer = 0;
        cow.resting = true;
        cow.getNavigation().stop();
    }

    // втома зменшується поступово, тож перерваний відпочинок не пропадає
    @Override
    public void tick() {
        restTimer++;
        if (restTimer % 20 == 0) {
            int recover = (int) Math.round(RECOVER_PER_SECOND * cow.gene(CowGenes.Gene.REST_RECOVERY));
            cow.fatigue = Math.max(0, cow.fatigue - recover);
        }
    }

    @Override
    public void stop() {
        restTimer = 0;
        cow.resting = false;
    }
}

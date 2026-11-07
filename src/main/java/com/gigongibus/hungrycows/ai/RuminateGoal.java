package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Ремигання (жуйка): після їжі корова стоїть і жує — заспокоюється й трохи відпочиває. Лише коли так вирішив мозок.
public class RuminateGoal extends Goal {
    private static final int CHEW_SOUND_TICKS = 80;     // звук жування раз на 4 с
    private static final int FATIGUE_EVERY_TICKS = 40;  // втома −1 раз на 2 с (повільніше, ніж відпочинок)
    private final HungryCow cow;
    private int ticks = 0;

    public RuminateGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return cow.brain.is(CowBrain.Action.RUMINATE);
    }

    @Override
    public boolean canContinueToUse() {
        return cow.brain.is(CowBrain.Action.RUMINATE);
    }

    @Override
    public void start() {
        ticks = 0;
        cow.resting = true;
        cow.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        cow.getNavigation().stop();
        if (ticks % 20 == 0) cow.chem.onRuminate();
        if (ticks % FATIGUE_EVERY_TICKS == 0) cow.fatigue = Math.max(0, cow.fatigue - 1);
        if (ticks % CHEW_SOUND_TICKS == 0) cow.playSound(SoundEvents.GENERIC_EAT, 0.3F, 0.7F);
    }

    @Override
    public void stop() {
        cow.resting = false;
    }
}

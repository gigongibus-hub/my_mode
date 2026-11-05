package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowBrain;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Сонячний день: перед відпочинком чи ремиганням корова йде в тінь (дерева, навіс), бо на сонці швидше хоче пити
public class SeekShadeGoal extends Goal {
    private static final int SHADE_RADIUS = 16;         // як далеко шукає тінь
    private static final int SHADE_TRIES = 40;          // скільки випадкових місць перевіряє за раз
    private static final int MAX_WALK_TICKS = 400;      // іде до тіні щонайбільше 20 с
    private static final int RETRY_TICKS = 400;         // тіні немає — наступна спроба за 20 с
    private final HungryCow cow;
    private BlockPos shade;
    private int walkTicks = 0;
    private int nextTry = 0;

    public SeekShadeGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // хоче відпочити чи ремигати, а стоїть на сонці
    private boolean wantsShade() {
        if (cow.isBaby() || !cow.inSun()) return false;
        if (CowBrain.enabled) return cow.brain.is(CowBrain.Action.REST) || cow.brain.is(CowBrain.Action.RUMINATE);
        return cow.wantsRest();
    }

    @Override
    public boolean canUse() {
        if (cow.tickCount < nextTry || !wantsShade()) return false;
        nextTry = cow.tickCount + RETRY_TICKS;
        shade = cow.home.findShade(SHADE_RADIUS, SHADE_TRIES);
        return shade != null;
    }

    @Override
    public boolean canContinueToUse() {
        return shade != null && cow.inSun() && walkTicks < MAX_WALK_TICKS && wantsShade();
    }

    @Override
    public void start() {
        walkTicks = 0;
    }

    @Override
    public void tick() {
        if (shade == null) return;
        walkTicks++;
        if (walkTicks == 1 || walkTicks % 20 == 0) {
            cow.getNavigation().moveTo(shade.getX() + 0.5, shade.getY(), shade.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public void stop() {
        shade = null;
        cow.getNavigation().stop();
    }
}

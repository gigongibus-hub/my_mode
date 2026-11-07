package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Сонячний день: перегріта корова (перегрівання 30+) іде в тінь — спершу дерев, бо на сонці перегрівається й хоче пити
public class SeekShadeGoal extends Goal {
    private static final int SHADE_RADIUS = 16;         // як далеко шукає тінь
    private static final int SHADE_TRIES = 40;          // скільки випадкових місць перевіряє за раз
    private static final int MAX_WALK_TICKS = 400;      // іде до тіні щонайбільше 20 с
    private static final int RETRY_TICKS = 400;         // тіні немає — наступна спроба за 20 с
    // Спершу тінь дерев: найближчою тінню на пасовищі часто є дах біля входу, і корови стояли біля виходу (Fij)
    private static final boolean TREES_FIRST = true;
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

    // Тінь шукає лише тоді, коли їй жарко (перегрівання 30+), а стоїть на сонці. Раніше — щоразу перед відпочинком
    // чи ремиганням, і корови надто часто йшли в тінь (Fij)
    private boolean wantsShade() {
        return cow.inSun() && cow.heat.wantsShade() && !cow.heat.unconscious;
    }

    @Override
    public boolean canUse() {
        if (cow.tickCount < nextTry || !wantsShade()) return false;
        nextTry = cow.tickCount + RETRY_TICKS;
        shade = cow.home.findShade(SHADE_RADIUS, SHADE_TRIES, TREES_FIRST);           // спершу тінь дерев ...
        if (shade == null) shade = cow.home.findShade(SHADE_RADIUS, SHADE_TRIES, false); // ... немає — будь-яка (навіс, хлів)
        return shade != null;
    }

    @Override
    public boolean canContinueToUse() {
        return shade != null && cow.inSun() && walkTicks < MAX_WALK_TICKS && !cow.heat.unconscious;
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

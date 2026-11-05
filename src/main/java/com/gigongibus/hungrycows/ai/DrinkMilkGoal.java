package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.CowFamily;
import com.gigongibus.hungrycows.CowGenes;
import com.gigongibus.hungrycows.CowMilk;
import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Голодне теля йде до матері і 10 секунд п'є молоко, поки вона стоїть і відпочиває
public class DrinkMilkGoal extends Goal {
    // з якого голоду теля йде пити: HungryCow.hungryAt() (30, ген ±30%)
    private static final int DRINK_TICKS = 200;       // скільки п'є (10 сек)
    private static final int DRINK_HUNGER = 35;       // скільки голоду знімає один раз (5 одиниць молока)
    private static final double REACH = 2.0;          // з якої відстані п'є
    private static final int MAX_RUN_TICKS = 600;     // скільки максимум іти до матері (30 сек)
    private final HungryCow cow;
    private HungryCow mother;
    private int drinkTicks = 0;
    private int runTicks = 0;
    private int cooldown = 0;

    public DrinkMilkGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean motherOk() {
        return mother != null && mother.isAlive()
                && mother.milk.amount() >= drinkUnits() && mother.milk.isLactating()
                && cow.distanceToSqr(mother) <= CowFamily.MOTHER_RANGE * CowFamily.MOTHER_RANGE;
    }

    // скільки одиниць випиває за раз: 5 × ген теляти «скільки випиває теля»
    private int drinkUnits() {
        return Math.max(1, (int) Math.round(CowMilk.CALF_DRINK * cow.gene(CowGenes.Gene.CALF_DRINK)));
    }

    @Override
    public boolean canUse() {
        if (!cow.isBaby() || !cow.shouldSeekFood()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 20;
        mother = cow.family.findMother();
        return motherOk();
    }

    @Override
    public boolean canContinueToUse() {
        return motherOk() && drinkTicks < DRINK_TICKS && runTicks < MAX_RUN_TICKS;
    }

    @Override
    public void start() {
        drinkTicks = 0;
        runTicks = 0;
    }

    @Override
    public void tick() {
        if (mother == null) return;
        cow.getLookControl().setLookAt(mother, 30.0F, 30.0F);
        if (cow.distanceToSqr(mother) > REACH * REACH) {
            runTicks++;
            if (runTicks == 1 || runTicks % 10 == 0) cow.getNavigation().moveTo(mother, 1.2);
            return;
        }
        cow.getNavigation().stop();
        mother.nurseUntil = mother.tickCount + 10;   // мати стоїть, поки теля п'є
        drinkTicks++;
        if (drinkTicks % 40 == 0) cow.playSound(SoundEvents.GENERIC_DRINK, 0.5F, 1.2F);
        if (drinkTicks >= DRINK_TICKS && mother.milk.take(drinkUnits())) {
            cow.hunger = Math.max(0, cow.hunger - (int) Math.round(DRINK_HUNGER * cow.gene(CowGenes.Gene.CALF_DRINK)));
            cow.chem.onNursing();
            cow.onAte();
            mother.chem.onNursing();
            cow.tell("Теля " + cow.family.name + " попило молока в " + mother.family.name
                    + " (лишилось " + mother.milk.amount() + ")");
        }
    }

    @Override
    public void stop() {
        mother = null;
        drinkTicks = 0;
        runTicks = 0;
        cow.getNavigation().stop();
    }
}

package com.gigongibus.hungrycows.ai;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

// Роззирання — пильність (замість ванільного RandomLookAroundGoal). Корова час від часу піднімає голову й оглядається:
//  - під час пасіння теж (ціль займає лише погляд, не рух — корова не перестає їсти);
//  - напружена (страх, адреналін понад спокійний рівень), самотня чи вночі — оглядається частіше;
//  - ватажок стада — частіше за інших;
//  - спокійна в стаді (3+ корови поруч, нічого не лякає) — рідко.
public class ScanGoal extends Goal {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    private static final int CHECK_EVERY = 10;          // вирішує, чи оглянутися, раз на пів секунди
    private static final double BASE_CHANCE = 0.05;     // звичайний шанс оглянутися за перевірку (≈ раз на 10 с)
    private static final double ALERT = 3.0;            // напружена — утричі частіше
    private static final double LONELY = 2.0;           // самотня — удвічі частіше
    private static final double NIGHT = 1.5;            // уночі — у 1,5 раза частіше
    private static final double LEADER = 1.5;           // ватажок — у 1,5 раза частіше
    private static final double CALM_HERD = 0.5;        // спокійна в стаді — удвічі рідше
    private static final int CALM_HERD_SIZE = 3;        // «у стаді» = щонайменше 3 корови поруч (разом з нею)
    private static final int LOOK_MIN = 20, LOOK_MAX = 40;   // один погляд у бік: 1–2 с
    private static final float HEAD_CALM = 10F, HEAD_ALERT = 25F;

    private final HungryCow cow;
    private int cooldown = 0;
    private int lookTime;
    private double relX, relZ;
    private boolean alert;

    public ScanGoal(HungryCow cow) {
        this.cow = cow;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = CHECK_EVERY / 2;   // canUse викликається раз на 2 тіки
        if (cow.sleeping) return false;
        alert = cow.chem.fearFelt() > 0;
        double chance = BASE_CHANCE;
        if (alert) chance *= ALERT;
        if (cow.chem.isLonely()) chance *= LONELY;
        if (cow.isNightTime()) chance *= NIGHT;
        if (cow.herd.isLeader()) chance *= LEADER;
        if (!alert && cow.herd.size() >= CALM_HERD_SIZE) chance *= CALM_HERD;
        return cow.getRandom().nextDouble() < chance;
    }

    @Override
    public boolean canContinueToUse() {
        return lookTime > 0 && !cow.sleeping;
    }

    @Override
    public void start() {
        double angle = Math.PI * 2 * cow.getRandom().nextDouble();
        relX = Math.cos(angle);
        relZ = Math.sin(angle);
        lookTime = LOOK_MIN + cow.getRandom().nextInt(LOOK_MAX - LOOK_MIN + 1);
    }

    @Override
    public void tick() {
        lookTime--;
        float speed = alert ? HEAD_ALERT : HEAD_CALM;
        cow.getLookControl().setLookAt(cow.getX() + relX, cow.getEyeY(), cow.getZ() + relZ, speed, speed);
    }
}

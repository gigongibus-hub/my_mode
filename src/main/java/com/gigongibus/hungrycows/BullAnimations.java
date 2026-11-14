package com.gigongibus.hungrycows;

import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Які анімації бика (модель Fij з Blockbench, GeckoLib) коли грати (Fij, 14.11):
 * спокій 1 — час від часу, коли просто стоїть; спокій 2 — жує жуйку; спокій 3 — п'є воду (голова донизу);
 * підготовка до атаки — готується напасти (на гравця чи на іншого бика); атака — біжить нападати; ходьба й біг — коли йде/біжить.
 * Назви анімацій — як у файлах Fij (`assets/hungrycows/animations/bull.animation.json`).
 */
public final class BullAnimations {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float RUN_FROM = 0.55F;            // «швидкість ніг» (limbSwingAmount) від 0,55 — біг, менше — ходьба
    public static final float WALK_NORMAL = 0.4F;          // звичайна хода: анімація ходьби з рідною швидкістю
    public static final float RUN_NORMAL = 0.6F;           // звичайний біг ...
    public static final float RUN_FASTER = 1.4F;           // ... грається в 1,4 раза швидше за файл (у файлі біг 2 с, як ходьба)
    public static final int IDLE_LENGTH_TICKS = 80;        // спокій 1 триває 4 с ...
    public static final int IDLE_PAUSE_MIN = 200, IDLE_PAUSE_MAX = 500;   // ... і повторюється раз на 10–25 с, поки стоїть

    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("jty");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("bigh");
    private static final RawAnimation PREPARE = RawAnimation.begin().thenLoop("pidhutovka_atak");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenLoop("atac");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("is_standing");
    private static final RawAnimation CHEW = RawAnimation.begin().thenLoop("is_standing2");
    private static final RawAnimation DRINK = RawAnimation.begin().thenLoop("is_standing3");

    private BullAnimations() {
    }

    public static PlayState predicate(AnimationState<HungryCow> state) {
        HungryCow cow = state.getAnimatable();
        float legs = Math.abs(state.getLimbSwingAmount());
        state.setControllerSpeed(1.0F);
        switch (cow.animNow()) {
            case PREPARE -> { return state.setAndContinue(PREPARE); }
            case ATTACK -> {
                state.setControllerSpeed(Math.max(1.0F, legs / RUN_NORMAL * RUN_FASTER));
                return state.setAndContinue(ATTACK);
            }
            case CHEW -> { return state.setAndContinue(CHEW); }
            case DRINK -> { return state.setAndContinue(DRINK); }
            default -> { }
        }
        if (state.isMoving()) {
            cow.idleAnimNext = 0;
            if (legs >= RUN_FROM) {
                state.setControllerSpeed(Math.max(1.0F, legs / RUN_NORMAL * RUN_FASTER));
                return state.setAndContinue(RUN);
            }
            state.setControllerSpeed(Math.max(0.5F, Math.min(1.5F, legs / WALK_NORMAL)));
            return state.setAndContinue(WALK);
        }
        // стоїть: час від часу «спокій 1»
        int t = cow.tickCount;
        if (cow.idleAnimNext == 0) cow.idleAnimNext = t + IDLE_PAUSE_MIN + cow.getRandom().nextInt(IDLE_PAUSE_MAX - IDLE_PAUSE_MIN + 1);
        if (t >= cow.idleAnimNext) {
            cow.idleAnimUntil = t + IDLE_LENGTH_TICKS;
            cow.idleAnimNext = cow.idleAnimUntil + IDLE_PAUSE_MIN + cow.getRandom().nextInt(IDLE_PAUSE_MAX - IDLE_PAUSE_MIN + 1);
        }
        if (t < cow.idleAnimUntil) return state.setAndContinue(IDLE);
        return PlayState.STOP;
    }
}

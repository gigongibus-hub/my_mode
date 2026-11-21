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
    public static final float RUN_FASTER = 2.0F;           // ... грається вдвічі швидше за файл (у файлі біг 2 с, як ходьба; Fij: «ковзають»)
    public static final float WALK_FASTER = 1.6F;          // хода — в 1,6 раза швидше за файл (Fij: «ковзають по льоду», було 1,0)
    public static final int TRANSITION_TICKS = 10;         // перехід між анімаціями 0,5 с (було 0,2 с — «топорно», Fij)
    // поріг з запасом, щоб на межі анімація не смикалась туди-сюди: почати йти з 0,08, зупинитись нижче 0,03;
    // почати бігти з 0,6, перестати нижче 0,45
    public static final float WALK_START = 0.08F, WALK_STOP = 0.03F, RUN_START = 0.6F, RUN_STOP = 0.45F;
    public static final float SPEED_SMOOTH = 0.08F;        // швидкість програвання міняється плавно (8% за кадр), а не стрибком
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

    private static PlayState play(AnimationState<HungryCow> state, HungryCow cow, RawAnimation anim, float speed) {
        cow.animSpeed += (speed - cow.animSpeed) * SPEED_SMOOTH;
        state.setControllerSpeed(cow.animSpeed);
        return state.setAndContinue(anim);
    }

    public static PlayState predicate(AnimationState<HungryCow> state) {
        HungryCow cow = state.getAnimatable();
        float legs = Math.abs(state.getLimbSwingAmount());
        // іде / біжить — з запасом (гістерезис)
        cow.animMoving = cow.animMoving ? legs > WALK_STOP : legs > WALK_START;
        cow.animRunning = cow.animMoving && (cow.animRunning ? legs > RUN_STOP : legs > RUN_START);
        switch (cow.animNow()) {
            case PREPARE -> { return play(state, cow, PREPARE, 1.0F); }
            case ATTACK -> { return play(state, cow, ATTACK, Math.max(1.0F, legs / RUN_NORMAL * RUN_FASTER)); }
            case CHEW -> { return play(state, cow, CHEW, 1.0F); }
            case DRINK -> { return play(state, cow, DRINK, 1.0F); }
            default -> { }
        }
        if (cow.animMoving) {
            cow.idleAnimNext = 0;
            cow.idleAnimUntil = 0;
            if (cow.animRunning) return play(state, cow, RUN, Math.max(1.0F, legs / RUN_NORMAL * RUN_FASTER));
            return play(state, cow, WALK, Math.max(0.8F, Math.min(2.4F, legs / WALK_NORMAL * WALK_FASTER)));
        }
        // стоїть: час від часу «спокій 1»
        int t = cow.tickCount;
        if (cow.idleAnimNext == 0) cow.idleAnimNext = t + IDLE_PAUSE_MIN + cow.getRandom().nextInt(IDLE_PAUSE_MAX - IDLE_PAUSE_MIN + 1);
        if (t >= cow.idleAnimNext) {
            cow.idleAnimUntil = t + IDLE_LENGTH_TICKS;
            cow.idleAnimNext = cow.idleAnimUntil + IDLE_PAUSE_MIN + cow.getRandom().nextInt(IDLE_PAUSE_MAX - IDLE_PAUSE_MIN + 1);
        }
        if (t < cow.idleAnimUntil) return play(state, cow, IDLE, 1.0F);
        cow.animSpeed = 1.0F;
        return PlayState.STOP;
    }
}

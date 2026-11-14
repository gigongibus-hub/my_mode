package com.gigongibus.hungrycows.nav;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Рух корови. Для бика (Fij: «бики занадто швидко повертаються й бігають повільно»): повертає тулуб повільніше,
 * а біг (швидкість понад звичайну ходу) — швидший. Корови й телята — як у ванільній грі.
 */
public class CowMoveControl extends MoveControl {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float BULL_TURN_DEG = 15F;     // бик повертається не більше ніж на 15° за тік (ванільно 90°) — важкий
    public static final double BULL_RUN_BOOST = 1.3;   // бик біжить на 30% швидше (лише біг: швидкість понад 1,0) ...
    public static final double BULL_RUN_MAX = 2.0;     // ... але не швидше 2,0 (розгін на гравця й так 2,4 — його не чіпаємо)

    private final HungryCow cow;

    public CowMoveControl(HungryCow cow) {
        super(cow);
        this.cow = cow;
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        if (cow.bullModel() && speed > 1.0) speed = Math.min(speed * BULL_RUN_BOOST, Math.max(speed, BULL_RUN_MAX));
        super.setWantedPosition(x, y, z, speed);
    }

    @Override
    protected float rotlerp(float from, float to, float maxTurn) {
        return super.rotlerp(from, to, cow.bullModel() ? Math.min(maxTurn, BULL_TURN_DEG) : maxTurn);
    }
}

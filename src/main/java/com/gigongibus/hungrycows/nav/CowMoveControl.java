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
    // Fij «тікаючи, бики крутять кола»: бик повертає повільно, а біг уперед не чекав повороту — коло ширше за точку шляху,
    // і бик кружляв довкола неї. Тепер, поки ціль збоку чи позаду, бик іде повільніше (спершу розвертається).
    public static final float BULL_SLOW_FROM_DEG = 30F;   // ціль більше ніж на 30° убік — бик сповільнюється
    public static final float BULL_TURN_SPEED_MIN = 0.1F;  // ціль збоку (90°) чи позаду — 10% швидкості (розворот майже на місці)

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
    public void tick() {
        boolean moving = cow.bullModel() && this.operation == Operation.MOVE_TO;
        double wx = this.wantedX, wz = this.wantedZ;
        super.tick();
        if (!moving) return;
        double dx = wx - cow.getX(), dz = wz - cow.getZ();
        if (dx * dx + dz * dz < 2.5E-7) return;
        float want = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        float off = Math.abs(net.minecraft.util.Mth.wrapDegrees(want - cow.getYRot()));
        if (off <= BULL_SLOW_FROM_DEG) return;
        // швидкість × косинус кута: 45° → 71%, 60° → 50%, 90° і більше → найменша (інакше обходить точку колом)
        float k = Math.max(BULL_TURN_SPEED_MIN, net.minecraft.util.Mth.cos(off * ((float) Math.PI / 180F)));
        cow.setSpeed(cow.getSpeed() * k);
    }

    @Override
    protected float rotlerp(float from, float to, float maxTurn) {
        return super.rotlerp(from, to, cow.bullModel() ? Math.min(maxTurn, BULL_TURN_DEG) : maxTurn);
    }
}

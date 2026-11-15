package com.gigongibus.hungrycows.nav;

import com.gigongibus.hungrycows.HungryCow;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Рух корови (Fij, «жвавість»): тварина не телепортує намір у рух —
 * - розганяється за ~0,4 с (у біг — швидше), а не з місця на повній швидкості;
 * - повертає тулуб обмежено (корова до 40° за тік, бик 15°; ванільно 90°) і, поки ціль збоку чи позаду, іде повільніше
 *   (спершу розвертається — інакше обходить точку колом: Fij «бики крутять кола»);
 * - вагається між двома справами — іде повільніше; коротка зупинка шару простою — стоїть.
 * Бик ще біжить швидше (Fij «бігають повільно»).
 */
public class CowMoveControl extends MoveControl {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float BULL_TURN_DEG = 15F;     // бик повертається не більше ніж на 15° за тік (ванільно 90°) — важкий
    public static final float COW_TURN_DEG = 40F;      // корова й теля — до 40° за тік
    public static final double BULL_RUN_BOOST = 1.3;   // бик біжить на 30% швидше (лише біг: швидкість понад 1,0) ...
    public static final double BULL_RUN_MAX = 2.0;     // ... але не швидше 2,0 (розгін на гравця й так 2,4 — його не чіпаємо)
    // Fij «тікаючи, бики крутять кола»: повільний поворот + повний біг уперед → коло ширше за точку шляху.
    // Поки ціль збоку, тварина іде повільніше (швидкість × косинус кута), спершу розвертається.
    public static final float BULL_SLOW_FROM_DEG = 30F;    // бик: ціль більше ніж на 30° убік — сповільнюється ...
    public static final float BULL_TURN_SPEED_MIN = 0.1F;  // ... збоку (90°) чи позаду — 10% швидкості (розворот майже на місці)
    public static final float COW_SLOW_FROM_DEG = 50F;     // корова: з 50° ...
    public static final float COW_TURN_SPEED_MIN = 0.3F;   // ... не менше 30% (корова повертає швидше за бика)
    // розгін: швидкість росте до потрібної за стільки тіків (гальмує одразу — щоб не налітати на стіни й корів)
    public static final int ACCEL_TICKS = 8;           // іде — 0,4 с
    public static final int ACCEL_RUN_TICKS = 4;       // біжить (страх, напад) — 0,2 с
    public static final double RUN_FROM = 1.2;         // «біг» — множник швидкості від 1,2
    public static final int STOPPED_RESET = 3;         // стояла 3+ тіки — розгін знову з нуля
    public static final double HESITATE_SPEED = 0.75;  // вагається — 75% швидкості (лише неспішна хода, не біг)

    private final HungryCow cow;
    private float smoothSpeed = 0;
    private int waitTicks = 0;

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
        boolean moving = this.operation == Operation.MOVE_TO;
        double wx = this.wantedX, wz = this.wantedZ, mod = this.speedModifier;
        super.tick();
        if (!moving) {
            if (++waitTicks >= STOPPED_RESET) smoothSpeed = 0;
            return;
        }
        waitTicks = 0;
        float target = cow.getSpeed();   // скільки поставила ванільна логіка
        double dx = wx - cow.getX(), dz = wz - cow.getZ();
        if (dx * dx + dz * dz >= 2.5E-7) {
            // ціль збоку — повільніше (спершу розвернутись)
            float want = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
            float off = Math.abs(Mth.wrapDegrees(want - cow.getYRot()));
            boolean bull = cow.bullModel();
            float from = bull ? BULL_SLOW_FROM_DEG : COW_SLOW_FROM_DEG;
            if (off > from) {
                float min = bull ? BULL_TURN_SPEED_MIN : COW_TURN_SPEED_MIN;
                // косинус від «порогу»: на порозі — уся швидкість, на 90° і більше — найменша
                float k = Mth.cos((off - from) / (90F - from) * ((float) Math.PI / 2F));
                target *= off >= 90F ? min : Math.max(min, k);
            }
        }
        // вагання й коротка зупинка (шар простою) — лише неспішна хода
        if (mod < RUN_FROM) {
            if (cow.life.paused()) target = 0;
            else if (cow.brain.hesitating()) target *= HESITATE_SPEED;
        }
        // розгін (гальмування — одразу)
        float full = (float) (mod * cow.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
        float step = full / (mod >= RUN_FROM ? ACCEL_RUN_TICKS : ACCEL_TICKS);
        smoothSpeed = target <= smoothSpeed ? target : Math.min(target, smoothSpeed + step);
        cow.setSpeed(smoothSpeed);
    }

    @Override
    protected float rotlerp(float from, float to, float maxTurn) {
        return super.rotlerp(from, to, Math.min(maxTurn, cow.bullModel() ? BULL_TURN_DEG : COW_TURN_DEG));
    }
}

package com.gigongibus.hungrycows.life;

/**
 * «Стале бажання» для цілей поза мозком (Fij, «жвавість»): ціль запускається, лише коли її привід тримається хоча б
 * onTicks поспіль (коротке зникнення до offTicks не скидає). Раніше, напр., повернення в стадо смикало корову, щойно вона
 * на мить відходила за межу. Клас не знає, яка це тварина й ціль.
 */
public final class Steady {
    private final int onTicks;
    private final int offTicks;
    private long since = -1;
    private long lastTrue = -1;

    public Steady(int onTicks, int offTicks) {
        this.onTicks = onTicks;
        this.offTicks = offTicks;
    }

    /** raw — чи є привід зараз; now — лічильник тіків тварини */
    public boolean test(boolean raw, long now) {
        if (raw) {
            if (since < 0 || now - lastTrue > offTicks) since = now;
            lastTrue = now;
            return now - since >= onTicks;
        }
        if (since >= 0 && now - lastTrue > offTicks) since = -1;
        return false;
    }
}

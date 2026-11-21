package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Земля здригається, коли поруч гупнуло важке дерево (Fij: «тяжкість»): коротке тремтіння камери, що згасає */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, value = Dist.CLIENT)
public final class TreeShakeClient {
    private TreeShakeClient() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float MAX_DEG = 2.2F;       // найсильніше тремтіння, градусів
    public static final int TICKS = 14;             // згасає за 0,7 с
    public static final double RANGE = 28;          // далі не відчувається

    private static long start = -1000;
    private static float strength;

    /** Гупнуло в точці (x, y, z) дерево вагою weight (0…1) */
    public static void thud(double x, double y, double z, float weight) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double d = mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
        float s = (float) (weight * Math.max(0, 1 - d / RANGE));
        if (s <= 0.02F) return;
        start = mc.level.getGameTime();
        strength = Math.max(s, strength * 0.5F);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double t = mc.level.getGameTime() - start + e.getPartialTick();
        if (t < 0 || t > TICKS) return;
        float fade = (float) (1 - t / TICKS), amp = MAX_DEG * strength * fade * fade;
        e.setPitch(e.getPitch() + amp * (float) Math.sin(t * 2.9));
        e.setRoll(e.getRoll() + amp * 0.6F * (float) Math.sin(t * 2.3 + 1));
    }
}

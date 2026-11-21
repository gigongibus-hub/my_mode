package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.phys.WoodBodyEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Налагодження фізики (вмикається властивістю -Dhungrycows.physdebug=true): щотіку пише в журнал висоту гравця біля фізичних тіл */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, value = Dist.CLIENT)
public final class PhysDebugClient {
    private static final boolean ON = Boolean.getBoolean("hungrycows.physdebug");

    private PhysDebugClient() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ON || e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var near = mc.level.getEntitiesOfClass(WoodBodyEntity.class, mc.player.getBoundingBox().inflate(4));
        if (near.isEmpty()) return;
        var p = mc.player;
        System.out.printf("[гравець] %d y %.4f x %.3f z %.3f на землі %s, стоїть на тілі %s, тіл поруч %d%n", mc.level.getGameTime(), p.getY(), p.getX(), p.getZ(),
                p.onGround(), com.gigongibus.hungrycows.phys.BodyCollision.standsOn(p) != null, near.size());
    }
}

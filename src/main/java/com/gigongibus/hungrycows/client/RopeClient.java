package com.gigongibus.hungrycows.client;

import com.gigongibus.hungrycows.HungryCowsMod;
import com.gigongibus.hungrycows.ModNetwork;
import com.gigongibus.hungrycows.phys.BodyChop;
import com.gigongibus.hungrycows.phys.BodyCollision;
import com.gigongibus.hungrycows.phys.Rope;
import com.gigongibus.hungrycows.phys.RopeUntiePacket;
import com.gigongibus.hungrycows.phys.WoodBodyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;

/** Присів + ПКМ порожньою рукою по колоді, прив'язаній до коня, — відв'язати (Fij, 21.11). Гра гравця бачить, куди дивишся. */
@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, value = Dist.CLIENT)
public final class RopeClient {
    private RopeClient() {}

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isShiftKeyDown() || !mc.player.getMainHandItem().isEmpty()) return;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        double limit = Rope.REACH;
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) limit = Math.min(limit, bh.getLocation().distanceTo(eye));
        WoodBodyEntity best = null;
        for (WoodBodyEntity b : BodyCollision.near(mc.level, mc.player.getBoundingBox().inflate(Rope.REACH))) {
            if (b.ropeHorseClient(new Vector3d()) < 0) continue;
            int[] h = BodyChop.pick(b.body, eye, look, limit);
            if (h == null) continue;
            limit = h[2] / 1000.0;
            best = b;
        }
        if (best == null) return;
        ModNetwork.CHANNEL.sendToServer(new RopeUntiePacket(best.getId()));
        e.setCanceled(true);
        e.setSwingHand(true);
    }
}

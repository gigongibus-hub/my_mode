package com.gigongibus.hungrycows.mixin;

import com.gigongibus.hungrycows.phys.BodyCollision;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Гравець стоїть на фізичній колоді над повітрям — сервер не вважає, що він «літає» (інакше викидав би з гри) */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void hungrycows$onBody(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (BodyCollision.nearBody(entity)) cir.setReturnValue(false);
    }
}

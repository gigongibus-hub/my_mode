package com.gigongibus.hungrycows.mixin;

import com.gigongibus.hungrycows.phys.BodyCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Крок будь-якої істоти (`Entity.collide`): спершу тіло під ногами везе її (якщо стоїть на колоді, що рухається), потім після
 * ванільних блоків — ще й точне зіткнення з фізичними тілами (`BodyCollision`; як у Valkyrien Skies / Create Aeronautics).
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "collide", at = @At("HEAD"))
    private void hungrycows$carry(Vec3 move, CallbackInfoReturnable<Vec3> cir) {
        BodyCollision.carry((Entity) (Object) this);
    }

    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void hungrycows$bodies(Vec3 move, CallbackInfoReturnable<Vec3> cir) {
        Vec3 r = cir.getReturnValue();
        Vec3 out = BodyCollision.collide((Entity) (Object) this, r);
        if (out != r) cir.setReturnValue(out);
    }
}

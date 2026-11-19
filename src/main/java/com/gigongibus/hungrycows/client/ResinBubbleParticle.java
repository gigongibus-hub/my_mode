package com.gigongibus.hungrycows.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Бульбашка на розплавленій смолі (Fij: «бульбашки помаранчеві»): вилазить на поверхні, трохи росте й лопається (кадри ванільної
 * бульбашки), смола густа — майже не підіймається.
 */
public class ResinBubbleParticle extends TextureSheetParticle {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float R = 1.0F, G = 0.55F, B = 0.12F;   // колір смоли
    public static final float SIZE = 0.06F;                     // розмір (ванільна бульбашка ~0,1)

    private final SpriteSet sprites;

    protected ResinBubbleParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        lifetime = 10 + random.nextInt(10);
        gravity = 0;
        xd = zd = 0;
        yd = 0.002;
        quadSize = SIZE * (0.7F + random.nextFloat() * 0.6F);
        float shade = 0.85F + random.nextFloat() * 0.15F;
        setColor(R * shade, G * shade, B * shade);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new ResinBubbleParticle(level, x, y, z, sprites);
        }
    }
}

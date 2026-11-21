package com.gigongibus.hungrycows;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

// Мережа мода: повідомлення між сервером і грою гравця
public final class ModNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HungryCowsMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenCowBookPacket.class,
                OpenCowBookPacket::encode, OpenCowBookPacket::decode, OpenCowBookPacket::handle);
        // ремесло (Fij): вікно різьблення
        CHANNEL.registerMessage(id++, com.gigongibus.hungrycows.carve.OpenCarvingPacket.class, com.gigongibus.hungrycows.carve.OpenCarvingPacket::encode,
                com.gigongibus.hungrycows.carve.OpenCarvingPacket::decode, com.gigongibus.hungrycows.carve.OpenCarvingPacket::handle);
        CHANNEL.registerMessage(id++, com.gigongibus.hungrycows.carve.CarveUpdatePacket.class, com.gigongibus.hungrycows.carve.CarveUpdatePacket::encode,
                com.gigongibus.hungrycows.carve.CarveUpdatePacket::decode, com.gigongibus.hungrycows.carve.CarveUpdatePacket::handle);
        // розрубка дерева (Fij): удар сокирою
        CHANNEL.registerMessage(id++, com.gigongibus.hungrycows.chop.ChopPacket.class, com.gigongibus.hungrycows.chop.ChopPacket::encode,
                com.gigongibus.hungrycows.chop.ChopPacket::decode, com.gigongibus.hungrycows.chop.ChopPacket::handle);
        CHANNEL.registerMessage(id++, com.gigongibus.hungrycows.phys.ChopBodyPacket.class, com.gigongibus.hungrycows.phys.ChopBodyPacket::encode,
                com.gigongibus.hungrycows.phys.ChopBodyPacket::decode, com.gigongibus.hungrycows.phys.ChopBodyPacket::handle);
    }
}

package com.gigongibus.hungrycows.phys;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Гравець → сервер: присів + ПКМ порожньою рукою по колоді на мотузці — відв'язати (Fij, 21.11) */
public record RopeUntiePacket(int entity) {
    public static void encode(RopeUntiePacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entity);
    }

    public static RopeUntiePacket decode(FriendlyByteBuf buf) {
        return new RopeUntiePacket(buf.readVarInt());
    }

    public static void handle(RopeUntiePacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (p == null || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()) return;
            if (p.level().getEntity(m.entity) instanceof WoodBodyEntity e && e.ropeHorse != null
                    && e.body.comWorld(new org.joml.Vector3d()).distance(p.getX(), p.getY(), p.getZ()) < Rope.REACH + e.body.radius) {
                Rope.untie(e, p);
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

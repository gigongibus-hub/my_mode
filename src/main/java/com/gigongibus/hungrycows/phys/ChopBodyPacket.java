package com.gigongibus.hungrycows.phys;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Гравець → сервер: удар сокирою по фізичному тілу — яке тіло, котра частина, як лягла лінія (у системі тіла) і з якою силою */
public record ChopBodyPacket(int entity, int index, int axis, int at, float power) {
    public static void encode(ChopBodyPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entity);
        buf.writeVarInt(m.index);
        buf.writeVarInt(m.axis);
        buf.writeVarInt(m.at);
        buf.writeFloat(m.power);
    }

    public static ChopBodyPacket decode(FriendlyByteBuf buf) {
        return new ChopBodyPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ChopBodyPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (p == null || m.axis < 0 || m.axis > 2) return;
            if (p.level().getEntity(m.entity) instanceof WoodBodyEntity e)
                BodyChop.chop((ServerLevel) p.level(), p, e, m.index, Direction.Axis.values()[m.axis], m.at, m.power);
        });
        ctx.get().setPacketHandled(true);
    }
}

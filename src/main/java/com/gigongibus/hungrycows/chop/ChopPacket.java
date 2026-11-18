package com.gigongibus.hungrycows.chop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Гравець → сервер: удар сокирою — де, який шматок, як лягла лінія і з якою силою (шкала) */
public record ChopPacket(BlockPos pos, int index, int axis, int at, float power) {
    public static void encode(ChopPacket m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeVarInt(m.index);
        buf.writeVarInt(m.axis);
        buf.writeVarInt(m.at);
        buf.writeFloat(m.power);
    }

    public static ChopPacket decode(FriendlyByteBuf buf) {
        return new ChopPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ChopPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (p == null || m.axis < 0 || m.axis > 2) return;
            Chopping.chop((ServerLevel) p.level(), p, m.pos, m.index, Direction.Axis.values()[m.axis], m.at, m.power);
        });
        ctx.get().setPacketHandled(true);
    }
}

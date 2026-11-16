package com.gigongibus.hungrycows.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/** Сервер → гравець: відкрити вікно різьблення цієї заготовки (колода й що вже вирізано) */
public class OpenCarvingPacket {
    final BlockPos pos;
    final Item log;
    final byte[] mask;

    public OpenCarvingPacket(BlockPos pos, Item log, byte[] mask) {
        this.pos = pos;
        this.log = log;
        this.mask = mask;
    }

    public static void encode(OpenCarvingPacket m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeRegistryId(ForgeRegistries.ITEMS, m.log);
        buf.writeByteArray(m.mask);
    }

    public static OpenCarvingPacket decode(FriendlyByteBuf buf) {
        return new OpenCarvingPacket(buf.readBlockPos(), buf.readRegistryId(), buf.readByteArray(4096));
    }

    public static void handle(OpenCarvingPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.gigongibus.hungrycows.client.CarvingScreen.open(m.pos, m.log, CarveMask.fromBytes(m.mask))));
        ctx.get().setPacketHandled(true);
    }
}

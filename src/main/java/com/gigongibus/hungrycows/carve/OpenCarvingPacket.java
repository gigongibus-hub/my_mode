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
    final byte[] mask, keep;

    /** keep — шаблон креслення (що має лишитись): гравець бачить його під деревом, коли вирізає (Fij) */
    public OpenCarvingPacket(BlockPos pos, Item log, byte[] mask, byte[] keep) {
        this.pos = pos;
        this.log = log;
        this.mask = mask;
        this.keep = keep;
    }

    public static void encode(OpenCarvingPacket m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeRegistryId(ForgeRegistries.ITEMS, m.log);
        buf.writeByteArray(m.mask);
        buf.writeByteArray(m.keep);
    }

    public static OpenCarvingPacket decode(FriendlyByteBuf buf) {
        return new OpenCarvingPacket(buf.readBlockPos(), buf.readRegistryId(), buf.readByteArray(4096), buf.readByteArray(4096));
    }

    private static CarveMask keepFrom(byte[] b) {
        CarveMask k = CarveMask.empty();
        java.util.BitSet bits = java.util.BitSet.valueOf(b);
        for (int i = bits.nextSetBit(0); i >= 0 && i < CarveMask.SIZE * CarveMask.SIZE; i = bits.nextSetBit(i + 1)) k.set(i % CarveMask.SIZE, i / CarveMask.SIZE);
        return k;
    }

    public static void handle(OpenCarvingPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.gigongibus.hungrycows.client.CarvingScreen.open(m.pos, m.log, CarveMask.fromBytes(m.mask),
                        m.keep.length == 0 ? CarveMask.empty() : keepFrom(m.keep))));
        ctx.get().setPacketHandled(true);
    }
}

package com.gigongibus.hungrycows;

import com.gigongibus.hungrycows.client.CowBookClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// Сервер → гравець: відкрити книгу «Корововедення» з цими рядками
public class OpenCowBookPacket {
    private static final int MAX_LINE = 1024;
    private final List<String> lines;

    public OpenCowBookPacket(List<String> lines) {
        this.lines = lines;
    }

    public static void encode(OpenCowBookPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.lines.size());
        for (String line : msg.lines) buf.writeUtf(line, MAX_LINE);
    }

    public static OpenCowBookPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readUtf(MAX_LINE));
        return new OpenCowBookPacket(lines);
    }

    public static void handle(OpenCowBookPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CowBookClient.open(msg.lines)));
        ctx.get().setPacketHandled(true);
    }
}

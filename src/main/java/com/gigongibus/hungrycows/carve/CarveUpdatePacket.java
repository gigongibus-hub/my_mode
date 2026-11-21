package com.gigongibus.hungrycows.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Гравець → сервер: що вирізано (раз на секунду й коли закрив вікно); thrown — викинув зубило (Fij: крафт кінчається, зубило випадає) */
public class CarveUpdatePacket {
    final BlockPos pos;
    final byte[] mask;
    final boolean thrown;
    final int strikes;

    /** strikes — скільки ударів з минулого разу (знос зубила: −1 за удар, Fij) */
    public CarveUpdatePacket(BlockPos pos, byte[] mask, boolean thrown, int strikes) {
        this.pos = pos;
        this.mask = mask;
        this.thrown = thrown;
        this.strikes = strikes;
    }

    public static void encode(CarveUpdatePacket m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeByteArray(m.mask);
        buf.writeBoolean(m.thrown);
        buf.writeVarInt(m.strikes);
    }

    public static CarveUpdatePacket decode(FriendlyByteBuf buf) {
        return new CarveUpdatePacket(buf.readBlockPos(), buf.readByteArray(4096), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(CarveUpdatePacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> { if (p != null) apply(p, m.pos, CarveMask.fromBytes(m.mask), m.thrown, m.strikes); });
        ctx.get().setPacketHandled(true);
    }

    /** Сервер: зберегти вирізане (лише вибите), якщо гравець поруч; викинув — зубило з руки летить на землю */
    public static void apply(net.minecraft.world.entity.player.Player p, BlockPos pos, CarveMask mask, boolean thrown, int strikes) {
        if (p.distanceToSqr(pos.getCenter()) > Carving.MAX_REACH * Carving.MAX_REACH) return;
        if (!(p.level().getBlockEntity(pos) instanceof WorkpieceBlockEntity be) || !be.hasLog()) return;
        be.applyCarve(mask);
        ItemStack chisel = p.getItemInHand(InteractionHand.MAIN_HAND);
        int wear = Math.min(Math.max(0, strikes), Carving.MAX_STRIKES_PER_PACKET);
        if (wear > 0 && chisel.is(Carving.CHISEL.get()) && !p.getAbilities().instabuild)
            chisel.hurtAndBreak(wear, p, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));   // зламалось — вікно закриється само
        if (thrown) {
            ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
            if (held.is(Carving.CHISEL.get())) {
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                p.drop(held, true);
            }
        }
    }
}

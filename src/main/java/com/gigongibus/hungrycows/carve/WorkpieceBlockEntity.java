package com.gigongibus.hungrycows.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Заготовка: з якої колоди (колір) і що від неї лишилось (маска; Fij: зберігається, можна продовжити) */
public class WorkpieceBlockEntity extends BlockEntity {
    private Item log = Items.STRIPPED_OAK_LOG;
    private CarveMask mask = new CarveMask();

    public WorkpieceBlockEntity(BlockPos pos, BlockState state) {
        super(Carving.WORKPIECE_BE.get(), pos, state);
    }

    public Item log() {
        return log;
    }

    public void setLog(Item item) {
        log = item;
        changed();
    }

    public CarveMask mask() {
        return mask;
    }

    /** Вирізане від гравця: лише вибивати (що вже вибито — не повертається) */
    public void applyCarve(CarveMask fromPlayer) {
        mask.keepOnly(fromPlayer);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(log);
        tag.putString("Log", id == null ? "minecraft:stripped_oak_log" : id.toString());
        tag.putByteArray("Mask", mask.toBytes());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.getString("Log")));
        log = i == null || i == Items.AIR ? Items.STRIPPED_OAK_LOG : i;
        mask = tag.contains("Mask") ? CarveMask.fromBytes(tag.getByteArray("Mask")) : new CarveMask();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

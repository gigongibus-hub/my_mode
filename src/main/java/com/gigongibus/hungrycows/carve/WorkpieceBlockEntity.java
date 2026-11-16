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

/**
 * Заготовка на верстаку: креслення (Fij: кладеться першим), колода (колір) і що від неї лишилось (маска; зберігається, можна
 * продовжити). Старі збереження без креслення — колода є, креслення немає (вийдуть лише палиці).
 */
public class WorkpieceBlockEntity extends BlockEntity {
    private Item blueprint = null;
    private Item log = null;
    private CarveMask mask = new CarveMask();

    public WorkpieceBlockEntity(BlockPos pos, BlockState state) {
        super(Carving.WORKPIECE_BE.get(), pos, state);
    }

    public Item log() {
        return log == null ? Items.STRIPPED_OAK_LOG : log;
    }

    public boolean hasLog() {
        return log != null;
    }

    public Item blueprint() {
        return blueprint;
    }

    public void setBlueprint(Item item) {
        blueprint = item;
        changed();
    }

    public void setLog(Item item) {
        log = item;
        mask = new CarveMask();
        changed();
    }

    /** Зняли колоду (готова деталь або палиці): креслення лишається на верстаку */
    public void clearLog() {
        log = null;
        mask = new CarveMask();
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

    private static String id(Item i) {
        ResourceLocation r = i == null ? null : ForgeRegistries.ITEMS.getKey(i);
        return r == null ? "" : r.toString();
    }

    private static Item item(String s) {
        if (s == null || s.isEmpty()) return null;
        Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(s));
        return i == null || i == Items.AIR ? null : i;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Log", id(log));
        tag.putString("Blueprint", id(blueprint));
        tag.putByteArray("Mask", mask.toBytes());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        log = item(tag.getString("Log"));
        blueprint = item(tag.getString("Blueprint"));
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

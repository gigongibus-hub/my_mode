package com.gigongibus.hungrycows.fell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Зарубка на стовбурі (Fij): з якого боку рубають, на якій висоті й наскільки глибоко; після падіння — пень (`felled`) */
public class FellingCutBlockEntity extends BlockEntity {
    private Direction side = Direction.NORTH;   // бік, з якого рубають (куди дивиться зарубка)
    private int at = 8;                          // висота розрубу, пікс. від низу колоди
    private float depth;                         // глибина зарубки, пікс. (0…16)
    private boolean felled;                      // дерево впало — лишився пень заввишки at
    private String wood = "oak";                 // вид дерева (дуб, ялина)

    public FellingCutBlockEntity(BlockPos pos, BlockState state) {
        super(Felling.FELLING_CUT_BE.get(), pos, state);
    }

    public Direction side() { return side; }
    public int at() { return at; }
    public float depth() { return depth; }
    public boolean felled() { return felled; }
    public String wood() { return wood; }

    public void start(Direction side, int at, String wood) {
        this.side = side;
        this.at = at;
        this.wood = wood;
        changed();
    }

    public void deepen(float px) {
        depth = Math.min(16, depth + px);
        changed();
    }

    public void fell() {
        felled = true;
        changed();
    }

    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Side", side.getName());
        tag.putInt("At", at);
        tag.putFloat("Depth", depth);
        tag.putBoolean("Felled", felled);
        tag.putString("Wood", wood);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        Direction d = Direction.byName(tag.getString("Side"));
        side = d == null || d.getAxis().isVertical() ? Direction.NORTH : d;
        at = tag.contains("At") ? tag.getInt("At") : 8;
        depth = tag.getFloat("Depth");
        felled = tag.getBoolean("Felled");
        wood = tag.contains("Wood") ? tag.getString("Wood") : "oak";
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(0.25);
    }
}

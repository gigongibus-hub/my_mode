package com.gigongibus.hungrycows.glue;

import com.gigongibus.hungrycows.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Кружка, яку склеюють (Fij): точність кожної деталі (з різьби) і скільки сохне смола після ручки */
public class MugAssemblyBlockEntity extends BlockEntity {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int DRY_TICKS = 12000;      // пів ігрової доби (Fij; прискорюється /hungrycows speed)

    public float qBody = 0.5F, qEars = 0.5F, qHandle = 0.5F;
    private int dry;

    public MugAssemblyBlockEntity(BlockPos pos, BlockState state) {
        super(Glue.MUG_ASSEMBLY_BE.get(), pos, state);
    }

    public float quality() {
        return (qBody + qEars + qHandle) / 3;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MugAssemblyBlockEntity be) {
        if (state.getValue(MugAssemblyBlock.STAGE) != MugAssemblyBlock.DRYING) return;
        be.dry += WorldClock.speed((ServerLevel) level);
        if (be.dry >= DRY_TICKS) level.setBlock(pos, state.setValue(MugAssemblyBlock.STAGE, MugAssemblyBlock.DONE), 3);
        if (level.getGameTime() % 20 == 0) be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("QBody", qBody);
        tag.putFloat("QEars", qEars);
        tag.putFloat("QHandle", qHandle);
        tag.putInt("Dry", dry);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        qBody = tag.getFloat("QBody");
        qEars = tag.getFloat("QEars");
        qHandle = tag.getFloat("QHandle");
        dry = tag.getInt("Dry");
    }
}

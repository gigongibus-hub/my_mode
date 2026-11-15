package com.gigongibus.hungrycows.salt;

import com.gigongibus.hungrycows.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Вміст корзини: скільки прожареної солі (до 9), прогрес, чи вже блок */
public class SaltBasketBlockEntity extends BlockEntity {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int CAPACITY = 9;            // 9 прожареної (Fij)
    public static final int DRIP_TICKS = 24000;      // ігрова доба (прискорюється /hungrycows speed)
    public static final int DROP_EVERY = 10;         // крапля знизу раз на пів секунди

    private int count = 0;
    private int progress = 0;
    private boolean done = false;

    public SaltBasketBlockEntity(BlockPos pos, BlockState state) {
        super(Salt.SALT_BASKET_BE.get(), pos, state);
    }

    public int count() { return count; }
    public boolean done() { return done; }
    public int progress() { return progress; }

    public void add(int n) {
        count += n;
        progress = 0;
        setChanged();
    }

    public ItemStack takeAll() {
        ItemStack out = done ? new ItemStack(Salt.SALT_BLOCK_ITEM.get()) : new ItemStack(Salt.GROUND_SALT_ROASTED.get(), count);
        count = 0;
        progress = 0;
        done = false;
        setChanged();
        return out;
    }

    /** Під корзиною порожньо — вода стікає (Fij «хоча б один блок пустоти») */
    public static boolean airBelow(Level level, BlockPos pos) {
        return level.getBlockState(pos.below()).isAir();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SaltBasketBlockEntity be) {
        if (be.done || be.count < CAPACITY || !SaltBasketBlock.hanging(level, pos) || !airBelow(level, pos)) return;   // лише на ланцюгу (Fij)
        be.progress += WorldClock.speed((ServerLevel) level);
        if (level.getGameTime() % DROP_EVERY == 0)
            ((ServerLevel) level).sendParticles(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.3 + level.random.nextDouble() * 0.4, pos.getY() - 0.02,
                    pos.getZ() + 0.3 + level.random.nextDouble() * 0.4, 1, 0, 0, 0, 0);
        if (be.progress >= DRIP_TICKS) {
            be.done = true;
            be.count = 0;
            be.progress = 0;
        }
        be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Count", count);
        tag.putInt("Progress", progress);
        tag.putBoolean("Done", done);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        count = tag.getInt("Count");
        progress = tag.getInt("Progress");
        done = tag.getBoolean("Done");
    }
}

package com.gigongibus.hungrycows.salt;

import com.gigongibus.hungrycows.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Вміст сковороди: скільки солі (до 20) і прогрес прожарки */
public class SaltPanBlockEntity extends BlockEntity {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int CAPACITY = 20;          // 20 штук (Fij)
    public static final int PER_PILE = 5;           // на моделі: купка за кожні 5
    public static final int ROAST_TICKS = 12000;    // пів ігрової доби (прискорюється /hungrycows speed)
    public static final float COOL_DOWN = 0.25F;    // багаття згасло — прогрес спадає вчетверо повільніше, ніж ріс (Fij «потихеньку»)
    public static final int STEAM_EVERY = 20;       // пара над сковородою раз на секунду, поки смажиться

    private int count = 0;
    private int progress = 0;
    private float cool = 0;

    public SaltPanBlockEntity(BlockPos pos, BlockState state) {
        super(Salt.SALT_PAN_BE.get(), pos, state);
    }

    public int count() {
        return count;
    }

    public int progress() {
        return progress;
    }

    public void add() {
        count++;
        progress = 0;
        refresh();
    }

    /** Забрати все: прожарену — прожареною, інакше — назад недоочищеною */
    public ItemStack takeAll() {
        boolean roasted = getBlockState().getValue(SaltPanBlock.ROASTED);
        ItemStack out = new ItemStack(roasted ? Salt.GROUND_SALT_ROASTED.get() : Salt.GROUND_SALT_HALF_PURIFIED.get(), count);
        count = 0;
        progress = 0;
        if (level != null && !level.isClientSide && getBlockState().is(Salt.SALT_PAN.get()))
            level.setBlock(worldPosition, getBlockState().setValue(SaltPanBlock.PILES, 0).setValue(SaltPanBlock.ROASTED, false), 3);
        setChanged();
        return out;
    }

    private void refresh() {
        if (level != null && !level.isClientSide) {
            int piles = (count + PER_PILE - 1) / PER_PILE;
            if (getBlockState().getValue(SaltPanBlock.PILES) != piles) level.setBlock(worldPosition, getBlockState().setValue(SaltPanBlock.PILES, piles), 3);
        }
        setChanged();
    }

    public static boolean fireLit(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(Blocks.CAMPFIRE) && below.getValue(CampfireBlock.LIT);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SaltPanBlockEntity be) {
        if (state.getValue(SaltPanBlock.ROASTED) || be.count < CAPACITY) return;
        int speed = WorldClock.speed((ServerLevel) level);
        if (fireLit(level, pos)) {
            be.progress += speed;
            if (level.getGameTime() % STEAM_EVERY == 0)
                ((ServerLevel) level).sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() - 0.2, pos.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.01);
            if (be.progress >= ROAST_TICKS) {
                be.progress = 0;
                level.setBlock(pos, state.setValue(SaltPanBlock.ROASTED, true), 3);
            }
        } else if (be.progress > 0) {
            be.cool += speed * COOL_DOWN;
            int drop = (int) be.cool;
            be.cool -= drop;
            be.progress = Math.max(0, be.progress - drop);
        }
        be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Count", count);
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        count = tag.getInt("Count");
        progress = tag.getInt("Progress");
    }
}

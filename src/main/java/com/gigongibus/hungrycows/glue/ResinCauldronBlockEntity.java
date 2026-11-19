package com.gigongibus.hungrycows.glue;

import com.gigongibus.hungrycows.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Котел зі смолою (Fij): скільки смоли (1–30 = стільки намазувань), скільки прогріто. Запалене багаття під котлом — за ігрову
 * добу смола плавиться; згасло, поки не розплавилась, — прогрів потроху спадає; розплавлена без вогню застигає.
 */
public class ResinCauldronBlockEntity extends BlockEntity {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int MAX_RESIN = 30;            // до 30 шматків смоли (Fij)
    public static final int MELT_TICKS = 24000;        // одна ігрова доба на багатті (Fij; прискорюється /hungrycows speed)
    public static final float COOL_DOWN = 0.25F;       // згасло — прогрів спадає вчетверо повільніше, ніж ріс (Fij «потихеньку»)
    public static final int HARDEN_TICKS = 6000;       // розплавлена без вогню застигає за чверть доби (ПРОПОЗИЦІЯ)

    private int resin;
    private float heat;
    private int cold;                                  // скільки розплавлена стоїть без вогню

    public ResinCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(Glue.RESIN_CAULDRON_BE.get(), pos, state);
    }

    public int resin() {
        return resin;
    }

    public float heat() {
        return heat;
    }

    public void setResin(int n) {
        resin = Math.max(0, Math.min(MAX_RESIN, n));
        changed();
    }

    public static boolean hasFire(Level level, BlockPos pos) {
        return CampfireBlock.isLitCampfire(level.getBlockState(pos.below()));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ResinCauldronBlockEntity be) {
        int speed = WorldClock.speed((ServerLevel) level);
        boolean molten = state.getValue(ResinCauldronBlock.MOLTEN);
        if (hasFire(level, pos)) {
            be.cold = 0;
            if (!molten) {
                be.heat = Math.min(MELT_TICKS, be.heat + speed);
                if (be.heat >= MELT_TICKS) level.setBlock(pos, state.setValue(ResinCauldronBlock.MOLTEN, true), 3);
                else be.showMelt(level, pos, state);
            }
        } else if (molten) {
            be.cold += speed;
            if (be.cold >= HARDEN_TICKS) {            // застигла: щоб знову розплавити — знову доба на вогні
                be.cold = 0;
                be.heat = 0;
                level.setBlock(pos, state.setValue(ResinCauldronBlock.MOLTEN, false).setValue(ResinCauldronBlock.MELT, 0), 3);
            }
        } else if (be.heat > 0) {
            be.heat = Math.max(0, be.heat - speed * COOL_DOWN);
            be.showMelt(level, pos, state);
        }
        if (level.getGameTime() % 20 == 0) be.setChanged();
    }

    /** Стадія плавлення за прогрівом: до ⅓ — застигла, до ⅔ — перші плями, далі — майже розплавлена */
    public static int meltStage(float heat) {
        return Math.min(2, (int) (heat * 3 / MELT_TICKS));
    }

    private void showMelt(Level level, BlockPos pos, BlockState state) {
        int m = meltStage(heat);
        if (state.getValue(ResinCauldronBlock.MELT) != m) level.setBlock(pos, state.setValue(ResinCauldronBlock.MELT, m), 3);
    }

    public void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState st = getBlockState();
            int lvl = ResinCauldronBlock.levelFor(resin);
            if (st.getValue(ResinCauldronBlock.LEVEL) != lvl) level.setBlock(worldPosition, st.setValue(ResinCauldronBlock.LEVEL, lvl), 3);
            else level.sendBlockUpdated(worldPosition, st, st, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Resin", resin);
        tag.putFloat("Heat", heat);
        tag.putInt("Cold", cold);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        resin = tag.getInt("Resin");
        heat = tag.getFloat("Heat");
        cold = tag.getInt("Cold");
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

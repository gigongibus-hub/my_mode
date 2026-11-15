package com.gigongibus.hungrycows.salt;

import com.gigongibus.hungrycows.CowHome;
import com.gigongibus.hungrycows.HungryCow;
import com.gigongibus.hungrycows.WorldClock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Скільки пучок пролежав на сонці й скільки мокрим */
public class WickerBundleBlockEntity extends BlockEntity {
    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final int CHECK_TICKS = 20;          // перевіряє раз на секунду
    public static final int SUN_DAY = 12000;           // «день на сонці» = 12000 тіків світла (уночі не сохне)
    public static final int WILT_DAYS = 1;             // Fij: 1-й день свіжий, 2-й підсохлий, 3-й сухий ...
    public static final int DRY_DAYS = 2;              // ... тобто сухий після 2 сонячних днів (на третій уже сухий)
    public static final int ROT_TICKS = 24000;         // мокрий ще доба (дощ) — гниє

    private int sun = 0;
    private int wet = 0;

    public WickerBundleBlockEntity(BlockPos pos, BlockState state) {
        super(Salt.WICKER_BUNDLE_BE.get(), pos, state);
    }

    public int sun() {
        return sun;
    }

    /** Чи падає дощ на пучок (за блоками над ним, як для корів) */
    public static boolean rainedOn(Level level, BlockPos pos) {
        return level.isRaining() && HungryCow.rainReaches(level, pos);
    }

    /** Сонце: день, не дощ, без даху */
    public static boolean sunny(Level level, BlockPos pos) {
        long t = level.getDayTime() % 24000;
        return t < 12000 && !level.isRaining() && !CowHome.hasRoof(level, pos);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WickerBundleBlockEntity be) {
        if (level.getGameTime() % CHECK_TICKS != 0) return;
        WickerBundleBlock.Stage stage = state.getValue(WickerBundleBlock.STAGE);
        if (stage == WickerBundleBlock.Stage.ROTTEN) return;
        int amount = CHECK_TICKS * WorldClock.speed((ServerLevel) level);
        WickerBundleBlock.Stage next = stage;
        if (rainedOn(level, pos)) {
            next = WickerBundleBlock.Stage.WET;
            be.wet += amount;
            if (be.wet >= ROT_TICKS) next = WickerBundleBlock.Stage.ROTTEN;
        } else if (stage == WickerBundleBlock.Stage.WET) {
            // дощ скінчився раніше, ніж згнив — сохне з початку (Fij)
            next = WickerBundleBlock.Stage.FRESH;
            be.sun = 0;
            be.wet = 0;
        } else if (sunny(level, pos)) {
            // покладений підсохлим чи сухим — уже «відлежав» своє (інакше сухий на сонці ставав свіжим: лічильник був 0)
            int had = stage == WickerBundleBlock.Stage.DRY ? DRY_DAYS * SUN_DAY : stage == WickerBundleBlock.Stage.WILTED ? WILT_DAYS * SUN_DAY : 0;
            be.sun = Math.max(be.sun, had) + amount;
            next = be.sun >= DRY_DAYS * SUN_DAY ? WickerBundleBlock.Stage.DRY
                    : be.sun >= WILT_DAYS * SUN_DAY ? WickerBundleBlock.Stage.WILTED : WickerBundleBlock.Stage.FRESH;
        }
        if (next != stage) level.setBlock(pos, state.setValue(WickerBundleBlock.STAGE, next), 3);
        be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Sun", sun);
        tag.putInt("Wet", wet);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        sun = tag.getInt("Sun");
        wet = tag.getInt("Wet");
    }
}

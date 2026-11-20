package com.gigongibus.hungrycows.phys;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

/**
 * Тверда частина фізичного тіла (Fij, 20.11: «через них можна проходити — зроби, щоб по фізичних блоках можна було бігати»).
 * Гра знає зіткнення з істотою лише однією прямою рамкою — тож кожна колода (шматок) тіла має свою рамку, як частини дракона:
 * гравець і моби в неї впираються й стоять на ній. Повернута колода — рамка, що її обіймає (трохи ширша за саму колоду).
 */
public class WoodBodyPart extends PartEntity<WoodBodyEntity> {
    public final int index;      // котра частина тіла

    public WoodBodyPart(WoodBodyEntity parent, int index) {
        super(parent);
        this.index = index;
        noPhysics = true;
    }

    /** Поставити рамку; null — частини вже немає (рамку геть з дороги) */
    public void place(AABB box) {
        if (box == null) box = new AABB(0, -10000, 0, 0, -10000, 0);
        setPosRaw((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
        xo = getX();
        yo = getY();
        zo = getZ();
        setBoundingBox(box);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}

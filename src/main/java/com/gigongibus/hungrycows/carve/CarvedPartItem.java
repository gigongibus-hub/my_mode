package com.gigongibus.hungrycows.carve;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Вирізана деталь (заготовка кружки, вушка, ручка): у NBT «Quality» 0…1 — наскільки точно вирізано (гравцю не показується) */
public class CarvedPartItem extends Item {
    public CarvedPartItem(Properties p) {
        super(p.stacksTo(1));
    }

    public static float quality(ItemStack s) {
        return s.hasTag() && s.getTag().contains("Quality") ? s.getTag().getFloat("Quality") : 0.5F;
    }

    public static ItemStack withQuality(ItemStack s, float q) {
        s.getOrCreateTag().putFloat("Quality", q);
        return s;
    }
}

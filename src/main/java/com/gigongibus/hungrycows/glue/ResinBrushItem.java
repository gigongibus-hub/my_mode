package com.gigongibus.hungrycows.glue;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Пензлик зі щетини свині (Fij): набирає смолу з котла (ПКМ), намазує нею деталь (−1 міцності за раз, 20 разів) */
public class ResinBrushItem extends Item {
    public ResinBrushItem(Properties p) {
        super(p);
    }

    public static boolean hasResin(ItemStack s) {
        return s.getTag() != null && s.getTag().getBoolean("Resin");
    }

    public static void setResin(ItemStack s, boolean on) {
        if (on) s.getOrCreateTag().putBoolean("Resin", true);
        else if (s.getTag() != null) s.getTag().remove("Resin");
    }
}

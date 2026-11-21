package com.gigongibus.hungrycows.carve;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Вирізана деталь (заготовка кружки, вушка, ручка): у NBT «Quality» 0…1 — наскільки точно вирізано (гравцю не показується) */
public class CarvedPartItem extends Item {
    public CarvedPartItem(Properties p) {
        super(p.stacksTo(1));
    }

    /** Склеювання (Fij): заготовку кружки ставлять будь-де (ПКМ по верху блока) — далі смола, вушка, ручка */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        if (this != Carving.MUG_BODY.get()) return net.minecraft.world.InteractionResult.PASS;
        var level = ctx.getLevel();
        var at = level.getBlockState(ctx.getClickedPos()).canBeReplaced() ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace());
        if (!com.gigongibus.hungrycows.glue.MugAssemblyBlock.place(level, at, ctx.getPlayer(), ctx.getItemInHand()))
            return net.minecraft.world.InteractionResult.FAIL;
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static float quality(ItemStack s) {
        return s.hasTag() && s.getTag().contains("Quality") ? s.getTag().getFloat("Quality") : 0.5F;
    }

    public static ItemStack withQuality(ItemStack s, float q) {
        s.getOrCreateTag().putFloat("Quality", q);
        return s;
    }
}

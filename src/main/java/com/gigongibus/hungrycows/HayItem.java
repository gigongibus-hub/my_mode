package com.gigongibus.hungrycows;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

// Предмет «Сіно»: у назві видно стадію і стан, з яким блок поставиться («Сіно (сухе, трохи брудне, зв'язане)»)
public class HayItem extends BlockItem {
    public HayItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        CompoundTag bs = tag != null ? tag.getCompound("BlockStateTag") : null;
        String d = bs == null || bs.isEmpty() ? "свіже, незв'язане"
                : CowHayBlock.describe(bs.getString("stage"), bs.getString("condition"), bs.contains("bound") ? bs.getString("bound") : "false");
        return Component.translatable(this.getDescriptionId()).append(" (" + d + ")");
    }
}

package com.gigongibus.hungrycows;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

// Блоки мода. Соляний лизунець (смак, етап 4): корови його лижуть — бажання солі гасне, +1 серце, задоволення.
// Рецепт: 8 кісткового борошна довкола каменю (data/hungrycows/recipes/salt_lick.json). Не зношується.
public class ModBlocks {
    public static final RegistryObject<Block> SALT_LICK = HungryCowsMod.BLOCKS.register("salt_lick", () ->
            new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5F).sound(SoundType.STONE)));

    public static final RegistryObject<Item> SALT_LICK_ITEM = HungryCowsMod.ITEMS.register("salt_lick", () ->
            new BlockItem(SALT_LICK.get(), new Item.Properties()));

    public static void init() {
    }
}

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

    // Сіно мода (план Fij): один блок із властивостями — стадія, стан, нитки (див. CowHayBlock)
    public static final RegistryObject<Block> HAY = HungryCowsMod.BLOCKS.register("hay", () ->
            new CowHayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.GRASS)
                    .randomTicks().lightLevel(CowHayBlock::light)));

    public static final RegistryObject<Item> HAY_ITEM = HungryCowsMod.ITEMS.register("hay", () ->
            new HayItem(HAY.get(), new Item.Properties()));

    // Пучок свіжої трави (текстура Fij): 9 пучків — блок свіжого незв'язаного сіна; сам пучок — 4 трави (рецепти в data/)
    public static final RegistryObject<Item> HAY_BUNDLE = HungryCowsMod.ITEMS.register("hay_bundle", () ->
            new Item(new Item.Properties()));

    public static void init() {
    }
}

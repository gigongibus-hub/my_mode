package com.gigongibus.hungrycows.salt;

import com.gigongibus.hungrycows.HungryCowsMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * СІЛЬ (план Fij, 15.11; текстури Fij — `textures/block/salt/`, `textures/item/salt/`). Ланцюжок:
 * 1) сольова руда (жили 10–25 бл. на межі каменю й глибинного сланцю) → 1–4 шматки рудної солі;
 * 2) ступка + шматок → 1–2 «перемолена, не чиста»; 3) діжка: 30 + 10 відер води + кришка, доба → 10 відер розчину;
 * 4) відро розчину + сито → 5 «недоочищена»; 5) сковорода на багатті: 20, пів доби → 20 «прожарена»;
 * 6) корзина (висить): 9 прожареної, доба → блок солі (лизунець корів; 9 чистих купок).
 */
public final class Salt {
    private Salt() {
    }

    // ================= ТАБЛИЦЯ ЧИСЕЛ =================
    public static final float ORE_STRENGTH = 3.0F;        // міцність руди — як у залізної
    public static final float ORE_RESISTANCE = 3.0F;

    // 1. сольова руда (Fij: глибинносланцевий варіант — згодом, коли буде текстура)
    public static final RegistryObject<Block> SALT_ORE = HungryCowsMod.BLOCKS.register("salt_ore", () ->
            new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(ORE_STRENGTH, ORE_RESISTANCE)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final RegistryObject<Item> SALT_ORE_ITEM = HungryCowsMod.ITEMS.register("salt_ore", () ->
            new BlockItem(SALT_ORE.get(), new Item.Properties()));

    // предмети ланцюжка (назви — за порядком Fij)
    public static final RegistryObject<Item> ROCK_SALT = HungryCowsMod.ITEMS.register("rock_salt", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GROUND_SALT_DIRTY = HungryCowsMod.ITEMS.register("ground_salt_dirty", () -> new Item(new Item.Properties()));

    public static void init() {
    }
}

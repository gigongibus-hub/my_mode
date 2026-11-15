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

    // 3. діжка (модель Fij), кришка (окремий предмет), відро сольового розчину
    public static final net.minecraftforge.registries.DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            net.minecraftforge.registries.DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES, HungryCowsMod.MODID);
    public static final net.minecraftforge.registries.DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            net.minecraftforge.registries.DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.MENU_TYPES, HungryCowsMod.MODID);

    public static final RegistryObject<Block> TUB = HungryCowsMod.BLOCKS.register("tub", () ->
            new TubBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> TUB_ITEM = HungryCowsMod.ITEMS.register("tub", () -> new BlockItem(TUB.get(), new Item.Properties()));
    public static final RegistryObject<Item> TUB_LID = HungryCowsMod.ITEMS.register("tub_lid", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SALT_SOLUTION = HungryCowsMod.ITEMS.register("salt_solution", () ->
            new Item(new Item.Properties().stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<TubBlockEntity>> TUB_BE = BLOCK_ENTITIES.register("tub", () ->
            net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(TubBlockEntity::new, TUB.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<TubMenu>> TUB_MENU = MENUS.register("tub", () ->
            net.minecraftforge.common.extensions.IForgeMenuType.create(TubMenu::new));

    public static void init() {
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}

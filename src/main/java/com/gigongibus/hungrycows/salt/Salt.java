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
    public static final int SIEVE_USES = 20;              // сито — 20 разів (Fij)
    public static final int SIEVE_OUTPUT = 5;             // 1 відро розчину → 5 недоочищеної (Fij, варіант 1)
    public static final int NEEDLE_USES = 15;             // спиця — 15 разів (Fij)
    public static final float ROTTEN_COMPOST = 0.65F;     // гнилий пучок у компостер — як сіно

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
    // кришка — і предмет (закрити діжку), і блок (Fij: «щоб можна було ставити»)
    public static final RegistryObject<Block> TUB_LID_BLOCK = HungryCowsMod.BLOCKS.register("tub_lid", () ->
            new TubLidBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> TUB_LID = HungryCowsMod.ITEMS.register("tub_lid", () -> new BlockItem(TUB_LID_BLOCK.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SALT_SOLUTION = HungryCowsMod.ITEMS.register("salt_solution", () ->
            new Item(new Item.Properties().stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<TubBlockEntity>> TUB_BE = BLOCK_ENTITIES.register("tub", () ->
            net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(TubBlockEntity::new, TUB.get()).build(null));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<SaltBasketMenu>> SALT_BASKET_MENU = MENUS.register("salt_basket", () ->
            net.minecraftforge.common.extensions.IForgeMenuType.create(SaltBasketMenu::new));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<TubMenu>> TUB_MENU = MENUS.register("tub", () ->
            net.minecraftforge.common.extensions.IForgeMenuType.create(TubMenu::new));

    // 4. сито (текстура Fij) і недоочищена сіль; мішковина — для рецепта сита (її рецепт — крок 5, лоза)
    public static final RegistryObject<Item> SIEVE = HungryCowsMod.ITEMS.register("sieve", () ->
            new WornToolItem(new Item.Properties().durability(SIEVE_USES)));
    public static final RegistryObject<Item> BURLAP = HungryCowsMod.ITEMS.register("burlap", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GROUND_SALT_HALF_PURIFIED = HungryCowsMod.ITEMS.register("ground_salt_half_purified", () ->
            new Item(new Item.Properties()));

    // 5. сольова сковорода (модель Fij) і прожарена сіль
    public static final RegistryObject<Block> SALT_PAN = HungryCowsMod.BLOCKS.register("salt_pan", () ->
            new SaltPanBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(st -> st.getValue(SaltPanBlock.CAMPFIRE) == SaltPanBlock.Fire.LIT ? 15 : 0)));
    public static final RegistryObject<Item> SALT_PAN_ITEM = HungryCowsMod.ITEMS.register("salt_pan", () -> new SaltPanItem(SALT_PAN.get(), new Item.Properties()));
    public static final RegistryObject<Item> GROUND_SALT_ROASTED = HungryCowsMod.ITEMS.register("ground_salt_roasted", () -> new Item(new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SaltPanBlockEntity>> SALT_PAN_BE = BLOCK_ENTITIES.register("salt_pan", () ->
            net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SaltPanBlockEntity::new, SALT_PAN.get()).build(null));

    // 6. лоза: з ліан мечем/ножицями; 8 лоз + павутина → пучок (блок на землі, сохне); спиця; мішковина
    public static final RegistryObject<Item> WICKER = HungryCowsMod.ITEMS.register("wicker", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Block> WICKER_BUNDLE = HungryCowsMod.BLOCKS.register("wicker_bundle", () ->
            new WickerBundleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.3F).sound(SoundType.GRASS).noOcclusion()));
    public static final RegistryObject<Item> WICKER_BUNDLE_ITEM = HungryCowsMod.ITEMS.register("wicker_bundle", () ->
            new WickerBundleItem(WICKER_BUNDLE.get(), WickerBundleBlock.Stage.FRESH, new Item.Properties()));
    public static final RegistryObject<Item> WICKER_BUNDLE_WILTED = HungryCowsMod.ITEMS.register("wicker_bundle_wilted", () ->
            new WickerBundleItem(WICKER_BUNDLE.get(), WickerBundleBlock.Stage.WILTED, new Item.Properties()));
    public static final RegistryObject<Item> WICKER_BUNDLE_DRY = HungryCowsMod.ITEMS.register("wicker_bundle_dry", () ->
            new WickerBundleItem(WICKER_BUNDLE.get(), WickerBundleBlock.Stage.DRY, new Item.Properties()));
    public static final RegistryObject<Item> WICKER_BUNDLE_WET = HungryCowsMod.ITEMS.register("wicker_bundle_wet", () ->
            new WickerBundleItem(WICKER_BUNDLE.get(), WickerBundleBlock.Stage.WET, new Item.Properties()));
    public static final RegistryObject<Item> WICKER_BUNDLE_ROTTEN = HungryCowsMod.ITEMS.register("wicker_bundle_rotten", () ->
            new WickerBundleItem(WICKER_BUNDLE.get(), WickerBundleBlock.Stage.ROTTEN, new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<WickerBundleBlockEntity>> WICKER_BUNDLE_BE = BLOCK_ENTITIES.register("wicker_bundle", () ->
            net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(WickerBundleBlockEntity::new, WICKER_BUNDLE.get()).build(null));
    public static final RegistryObject<Item> KNITTING_NEEDLE = HungryCowsMod.ITEMS.register("knitting_needle", () ->
            new WornToolItem(new Item.Properties().durability(NEEDLE_USES)));

    // 7. корзина для солі (модель Fij), блок солі (лизунець для корів, як старий), чиста купка солі (на потім)
    public static final RegistryObject<Block> SALT_BASKET = HungryCowsMod.BLOCKS.register("salt_basket", () ->
            new SaltBasketBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Item> SALT_BASKET_ITEM = HungryCowsMod.ITEMS.register("salt_basket", () -> new BlockItem(SALT_BASKET.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<SaltBasketBlockEntity>> SALT_BASKET_BE = BLOCK_ENTITIES.register("salt_basket", () ->
            net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(SaltBasketBlockEntity::new, SALT_BASKET.get()).build(null));
    public static final RegistryObject<Block> SALT_BLOCK = HungryCowsMod.BLOCKS.register("salt_block", () ->
            new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5F).sound(SoundType.STONE)));
    public static final RegistryObject<Item> SALT_BLOCK_ITEM = HungryCowsMod.ITEMS.register("salt_block", () -> new BlockItem(SALT_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> CLEAN_SALT = HungryCowsMod.ITEMS.register("clean_salt", () -> new Item(new Item.Properties()));
    /** Що корови лижуть: старий лизунець і блок солі (`data/hungrycows/tags/blocks/salt_licks.json`) */
    public static final net.minecraft.tags.TagKey<Block> SALT_LICKS = net.minecraft.tags.BlockTags.create(
            new net.minecraft.resources.ResourceLocation(HungryCowsMod.MODID, "salt_licks"));

    public static Item bundleItem(WickerBundleBlock.Stage stage) {
        return switch (stage) {
            case FRESH -> WICKER_BUNDLE_ITEM.get();
            case WILTED -> WICKER_BUNDLE_WILTED.get();
            case DRY -> WICKER_BUNDLE_DRY.get();
            case WET -> WICKER_BUNDLE_WET.get();
            case ROTTEN -> WICKER_BUNDLE_ROTTEN.get();
        };
    }

    public static void commonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.put(WICKER_BUNDLE_ROTTEN.get(), ROTTEN_COMPOST));
    }

    public static void init() {
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener(Salt::commonSetup);
    }
}

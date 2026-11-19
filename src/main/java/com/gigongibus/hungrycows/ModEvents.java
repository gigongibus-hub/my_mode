package com.gigongibus.hungrycows;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        // FOLLOW_RANGE = найдовша дорога, яку будує пошук шляху гри (PathFinder: walkedDistance < maxRange). 32 замало для
        // великого хліва (з середини надвір в обхід стін довше), тому 64. Корова ні на кого не полює, тож більше він ні на що не впливає.
        event.put(ModEntities.HUNGRY_COW.get(), Cow.createAttributes().add(Attributes.FOLLOW_RANGE, 64.0).build());
    }

    @SubscribeEvent
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModBlocks.SALT_LICK_ITEM);   // соляний лизунець
            if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) event.accept(com.gigongibus.hungrycows.salt.Salt.SALT_ORE_ITEM);
            event.accept(com.gigongibus.hungrycows.salt.Salt.SALT_BLOCK_ITEM);
        }
        // сіно мода (Fij): лише 4 стадії — з нитками й без; і пучок свіжої трави
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS || event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            net.minecraft.world.level.block.state.BlockState hay = ModBlocks.HAY.get().defaultBlockState();
            for (CowHayBlock.Stage st : CowHayBlock.Stage.values()) {
                event.accept(CowHayBlock.stackOf(hay.setValue(CowHayBlock.STAGE, st).setValue(CowHayBlock.BOUND, true)));
                event.accept(CowHayBlock.stackOf(hay.setValue(CowHayBlock.STAGE, st).setValue(CowHayBlock.BOUND, false)));
            }
            event.accept(ModBlocks.HAY_BUNDLE);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModEntities.HUNGRY_COW_SPAWN_EGG);
        }
        // книга «Корововедення» є лише у творчому інвентарі
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.COW_BOOK);
            event.accept(ModItems.BRAIN_STICK);
            event.accept(ModItems.NEEDLE);
            event.accept(ModItems.MORTAR);
            event.accept(ModItems.SOUL_SAND_PINCH);              // щіпка піску душ (раніше лише крафтом — у вкладці її не було)
            event.accept(com.gigongibus.hungrycows.carve.Carving.CHISEL_SMALL);
            event.accept(com.gigongibus.hungrycows.carve.Carving.CHISEL);
            event.accept(com.gigongibus.hungrycows.carve.Carving.CHISEL_WIDE);
            event.accept(com.gigongibus.hungrycows.carve.Carving.MALLET);
            event.accept(com.gigongibus.hungrycows.chop.Chopping.WOOD_PIECE);
            event.accept(com.gigongibus.hungrycows.chop.Chopping.WOOD_BUNDLE);
            event.accept(com.gigongibus.hungrycows.glue.Glue.RESIN);
            event.accept(com.gigongibus.hungrycows.glue.Glue.PIG_BRISTLE);
            event.accept(com.gigongibus.hungrycows.glue.Glue.BRISTLE_BRUSH);
            event.accept(com.gigongibus.hungrycows.carve.Carving.BLUEPRINT_MUG_BODY);
            event.accept(com.gigongibus.hungrycows.carve.Carving.BLUEPRINT_MUG_EARS);
            event.accept(com.gigongibus.hungrycows.carve.Carving.BLUEPRINT_MUG_HANDLE);
            event.accept(com.gigongibus.hungrycows.carve.Carving.MUG_BODY);
            event.accept(com.gigongibus.hungrycows.carve.Carving.MUG_EARS);
            event.accept(com.gigongibus.hungrycows.carve.Carving.MUG_HANDLE);
            event.accept(com.gigongibus.hungrycows.carve.Carving.MUG);
            event.accept(com.gigongibus.hungrycows.salt.Salt.TUB_ITEM);
            event.accept(com.gigongibus.hungrycows.salt.Salt.TUB_LID);
            event.accept(com.gigongibus.hungrycows.salt.Salt.SIEVE);
            event.accept(com.gigongibus.hungrycows.salt.Salt.SALT_PAN_ITEM);
            event.accept(com.gigongibus.hungrycows.salt.Salt.KNITTING_NEEDLE);
            event.accept(com.gigongibus.hungrycows.salt.Salt.SALT_BASKET_ITEM);
        }
        // гриби й зілля з грибів (Fij): ванільна вкладка кладе кожне зілля в усі пляшки — зайві (не та тривалість) прибираємо
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(com.gigongibus.hungrycows.salt.Salt.ROCK_SALT);
            event.accept(com.gigongibus.hungrycows.salt.Salt.GROUND_SALT_DIRTY);
            event.accept(com.gigongibus.hungrycows.salt.Salt.SALT_SOLUTION);
            event.accept(com.gigongibus.hungrycows.salt.Salt.GROUND_SALT_HALF_PURIFIED);
            event.accept(com.gigongibus.hungrycows.salt.Salt.GROUND_SALT_ROASTED);
            event.accept(com.gigongibus.hungrycows.salt.Salt.CLEAN_SALT);
            event.accept(com.gigongibus.hungrycows.salt.Salt.BURLAP);
            event.accept(com.gigongibus.hungrycows.salt.Salt.WICKER);
            event.accept(com.gigongibus.hungrycows.salt.Salt.WICKER_BUNDLE_ITEM);
            event.accept(com.gigongibus.hungrycows.salt.Salt.WICKER_BUNDLE_WILTED);
            event.accept(com.gigongibus.hungrycows.salt.Salt.WICKER_BUNDLE_DRY);
            event.accept(com.gigongibus.hungrycows.salt.Salt.WICKER_BUNDLE_WET);
            event.accept(com.gigongibus.hungrycows.salt.Salt.WICKER_BUNDLE_ROTTEN);
            event.accept(ModItems.FIRE_FUNGUS);
            event.accept(ModItems.STAR_FUNGUS);
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS || event.getTabKey() == CreativeModeTabs.COMBAT) {
            java.util.List<net.minecraft.world.item.ItemStack> wrong = new java.util.ArrayList<>();
            for (var en : event.getEntries()) if (ModPotions.wrongBottle(en.getKey())) wrong.add(en.getKey());
            for (var s : wrong) event.getEntries().remove(s);
        }
        // молоко кожної якості (поки для тестів)
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            for (MilkQuality q : MilkQuality.values()) {
                event.accept(QualityMilkBucketItem.create(q));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = HungryCowsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.HUNGRY_COW.get(), com.gigongibus.hungrycows.client.HungryCowDispatchRenderer::new);   // бик — модель Fij
            event.registerBlockEntityRenderer(com.gigongibus.hungrycows.salt.Salt.WICKER_BUNDLE_BE.get(),
                    com.gigongibus.hungrycows.client.WickerBundleRenderer::new);
            event.registerBlockEntityRenderer(com.gigongibus.hungrycows.carve.Carving.WORKPIECE_BE.get(),
                    com.gigongibus.hungrycows.client.WorkpieceRenderer::new);   // колода на верстаку   // пучки лози, розкидані по блоку
            event.registerBlockEntityRenderer(com.gigongibus.hungrycows.chop.Chopping.WOOD_PILE_BE.get(),
                    com.gigongibus.hungrycows.client.WoodPileRenderer::new);    // купа шматків колоди (розрубка, Fij)
        }

        // розрубка (Fij): шкала сили біля прицілу
        @SubscribeEvent
        public static void registerOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
            com.gigongibus.hungrycows.client.ChopClient.registerOverlay(event);
        }

        // Предмет «Сіно» виглядає так, як блок, що з нього поставиться (стадія, стан, нитки) — інакше всі були однакові (Fij)
        @SubscribeEvent
        public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            // кружка: що в ній налито (0 — порожня, 0,5 — вода, 1 — молоко) → модель з рідиною
            event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.gigongibus.hungrycows.carve.Carving.MUG.get(),
                    new net.minecraft.resources.ResourceLocation(HungryCowsMod.MODID, "drink"),
                    (stack, level, entity, seed) -> com.gigongibus.hungrycows.carve.MugItem.drinkKind(stack) * 0.5F));
            event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(ModBlocks.HAY_ITEM.get(),
                    new net.minecraft.resources.ResourceLocation(HungryCowsMod.MODID, "variant"),
                    (stack, level, entity, seed) -> CowHayBlock.variant(stack) * 0.01F + 0.001F));
            // шматок колоди: картинка за розміром (0 ціла … 4 скіпка)
            event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.gigongibus.hungrycows.chop.Chopping.WOOD_PIECE.get(),
                    new net.minecraft.resources.ResourceLocation(HungryCowsMod.MODID, "size"),
                    (stack, level, entity, seed) -> com.gigongibus.hungrycows.chop.WoodPieceItem.sizeClass(
                            com.gigongibus.hungrycows.chop.WoodPieceItem.piece(stack).fraction()) * 0.25F));
            // пензлик зі смолою (Fij): щетина помаранчева
            event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(com.gigongibus.hungrycows.glue.Glue.BRISTLE_BRUSH.get(),
                    new net.minecraft.resources.ResourceLocation(HungryCowsMod.MODID, "resin"),
                    (stack, level, entity, seed) -> com.gigongibus.hungrycows.glue.ResinBrushItem.hasResin(stack) ? 1F : 0F));
            // сіль: інтерфейс діжки
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(com.gigongibus.hungrycows.salt.Salt.TUB_MENU.get(),
                    com.gigongibus.hungrycows.client.TubScreen::new));
            event.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(com.gigongibus.hungrycows.salt.Salt.SALT_BASKET_MENU.get(),
                    com.gigongibus.hungrycows.client.SaltBasketScreen::new));
        }

        // сіль: рідина в діжці — вода синя, розчин світлий
        public static final int TUB_WATER_COLOR = 0x3F76E4, TUB_BRINE_COLOR = 0xC9DDE6;

        @SubscribeEvent
        public static void itemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
            // рідина в кружці: вода синя, молоко біле
            event.register((stack, tint) -> tint != 0 ? -1 : com.gigongibus.hungrycows.carve.MugItem.drinkKind(stack) == 1 ? 0x3F76E4 : 0xF4F1E8,
                    com.gigongibus.hungrycows.carve.Carving.MUG.get());
        }

        @SubscribeEvent
        public static void blockColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
            event.register((state, level, pos, tint) -> state.getValue(com.gigongibus.hungrycows.salt.TubBlock.LIQUID)
                    == com.gigongibus.hungrycows.salt.TubBlock.Liquid.BRINE ? TUB_BRINE_COLOR : TUB_WATER_COLOR,
                    com.gigongibus.hungrycows.salt.Salt.TUB.get());
        }
    }
}

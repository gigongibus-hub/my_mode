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
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModEntities.HUNGRY_COW_SPAWN_EGG);
        }
        // книга «Корововедення» є лише у творчому інвентарі
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.COW_BOOK);
            event.accept(ModItems.BRAIN_STICK);
            event.accept(ModItems.NEEDLE);
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
            event.registerEntityRenderer(ModEntities.HUNGRY_COW.get(), com.gigongibus.hungrycows.client.HungryCowRenderer::new);
        }
    }
}

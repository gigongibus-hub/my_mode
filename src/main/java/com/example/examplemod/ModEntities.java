package com.example.examplemod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final RegistryObject<EntityType<HungryCow>> HUNGRY_COW =
            ENTITIES.register("hungry_cow", () ->
                    EntityType.Builder.of(HungryCow::new, MobCategory.CREATURE)
                            .sized(0.9F, 1.4F)
                            .clientTrackingRange(10)
                            .build("hungry_cow"));

    public static final RegistryObject<Item> HUNGRY_COW_SPAWN_EGG =
            ExampleMod.ITEMS.register("hungry_cow_spawn_egg", () ->
                    new ForgeSpawnEggItem(HUNGRY_COW, 0x443626, 0xA1A1A1, new Item.Properties()));
}
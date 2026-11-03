package com.gigongibus.hungrycows;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

// Значення MODID має збігатися з mod_id у gradle.properties
@Mod(HungryCowsMod.MODID)
public class HungryCowsMod {
    public static final String MODID = "hungrycows";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public HungryCowsMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ITEMS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
    }
}

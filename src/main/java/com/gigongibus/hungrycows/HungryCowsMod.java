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
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public HungryCowsMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModBlocks.init();
        ModItems.init();
        com.gigongibus.hungrycows.salt.Salt.init();                    // сіль (план Fij)
        com.gigongibus.hungrycows.salt.Salt.register(modEventBus);
        com.gigongibus.hungrycows.carve.Carving.init();               // ремесло: зубило й заготовка (Fij)
        com.gigongibus.hungrycows.carve.Carving.register(modEventBus);
        com.gigongibus.hungrycows.chop.Chopping.init();               // розрубка дерева (Fij)
        com.gigongibus.hungrycows.glue.Glue.init();                   // склеювання кружки смолою (Fij)
        com.gigongibus.hungrycows.fell.Felling.init();                // падіння дерева (Fij)
        com.gigongibus.hungrycows.fell.Felling.register(modEventBus);
        com.gigongibus.hungrycows.phys.Physics.register(modEventBus);   // фізичні тіла (Fij)
        com.gigongibus.hungrycows.glue.Glue.register(modEventBus);
        com.gigongibus.hungrycows.chop.Chopping.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BloodPaperRecipe.SERIALIZERS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModPotions.POTIONS.register(modEventBus);                       // зілля з грибів (Fij)
        modEventBus.addListener(ModPotions::setup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ModPotions::onBrewed);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ModPotions::onCrafted);
        ModNetwork.register();
    }
}

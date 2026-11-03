package com.gigongibus.hungrycows;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final RegistryObject<Item> QUALITY_MILK_BUCKET =
            HungryCowsMod.ITEMS.register("quality_milk_bucket", () ->
                    new QualityMilkBucketItem(new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    // викликається з головного класу, щоб предмети зареєструвалися вчасно
    static void init() {
    }
}

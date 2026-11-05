package com.gigongibus.hungrycows;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final RegistryObject<Item> QUALITY_MILK_BUCKET =
            HungryCowsMod.ITEMS.register("quality_milk_bucket", () ->
                    new QualityMilkBucketItem(new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    // Книга «Корововедення»: ПКМ по корові відкриває сторінки про неї (лише у творчому режимі, без рецепта)
    public static final RegistryObject<Item> COW_BOOK =
            HungryCowsMod.ITEMS.register("cow_book", () -> new Item(new Item.Properties().stacksTo(1)));

    // викликається з головного класу, щоб предмети зареєструвалися вчасно
    static void init() {
    }
}

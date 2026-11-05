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

    // Палиця налагодження мозку: ПКМ по корові показує над хотбаром її рішення й шанси (лише у творчому режимі)
    public static final RegistryObject<Item> BRAIN_STICK =
            HungryCowsMod.ITEMS.register("brain_stick", () -> new Item(new Item.Properties().stacksTo(1)));

    // викликається з головного класу, щоб предмети зареєструвалися вчасно
    static void init() {
    }
}

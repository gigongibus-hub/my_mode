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

    // Теля від мандрівного торговця (етап 10): у інвентарі одразу стає телям біля гравця
    public static final RegistryObject<Item> CALF_TICKET =
            HungryCowsMod.ITEMS.register("calf_ticket", () -> new CalfTicketItem(new Item.Properties().stacksTo(1)));

    // Духовний документ (ідея Fij), крок 1: голка (3 залізні самородки) і голка з кров'ю корови
    public static final RegistryObject<Item> NEEDLE =
            HungryCowsMod.ITEMS.register("needle", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BLOODY_NEEDLE =
            HungryCowsMod.ITEMS.register("bloody_needle", () -> new BloodNeedleItem(new Item.Properties().stacksTo(1)));

    // крок 2: папір з кров'ю (голка з кров'ю + папір; голка повертається чистою — BloodPaperRecipe)
    public static final RegistryObject<Item> BLOODY_PAPER =
            HungryCowsMod.ITEMS.register("bloody_paper", () -> new BloodNeedleItem(new Item.Properties().stacksTo(1)));

    // крок 3: ступка з товкачиком (діорит; 34 використання — Fij) і щіпка піску душ (ступка + пісок душ → 9 щіпок)
    public static final RegistryObject<Item> MORTAR =
            HungryCowsMod.ITEMS.register("mortar", () -> new MortarItem(new Item.Properties().durability(MortarItem.USES)));
    public static final RegistryObject<Item> SOUL_SAND_PINCH =
            HungryCowsMod.ITEMS.register("soul_sand_pinch", () -> new Item(new Item.Properties()));

    // крок 4: «Корововед» (8 щіпок піску душ навколо паперу з кров'ю) — головне про корову, чия кров
    public static final RegistryObject<Item> COW_READER =
            HungryCowsMod.ITEMS.register("cow_reader", () -> new CowReaderItem(new Item.Properties().stacksTo(1)));

    // викликається з головного класу, щоб предмети зареєструвалися вчасно
    static void init() {
    }
}

package com.gigongibus.hungrycows;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.event.brewing.PotionBrewEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Зілля з грибів (Fij, 14.11). Варіння як у ванільній грі: незграбне зілля + вогнегриб / зірочник → зілля I;
 * + редстоун → II; + порох → вибухове (того ж рівня); + дихання дракона → осідаюче; вибухове й осідаюче II + світлопил → III;
 * осідаюче + 8 стріл → 8 стріл. Тривалість у кожного виду своя (таблиці нижче), тому в кожного виду й рівня — свій запис зілля:
 * ванільна гра міняє лише пляшку (порох, дихання дракона), а `onBrewed` ставить правильний запис.
 */
public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(ForgeRegistries.POTIONS, HungryCowsMod.MODID);

    public enum Form { DRINK, SPLASH, LINGERING, ARROW }

    // ================= ТАБЛИЦЯ ЧИСЕЛ (Fij), секунди =================
    // [рівень I, II, III]; звичайне зілля — лише I і II
    public static final int[] BURN_DRINK = { 45, 105 }, BURN_SPLASH = { 45, 105, 180 }, BURN_LINGERING = { 15, 20, 30 };
    public static final int BURN_ARROW = 60;
    public static final int[] GLOW_DRINK = { 35, 105 }, GLOW_SPLASH = { 35, 60, 120 }, GLOW_LINGERING = { 10, 15, 25 };
    public static final int GLOW_ARROW = 30;
    // ванільна хмара осідаючого зілля дає ¼ тривалості, стріла — ⅛: записуємо більше, щоб вийшли числа Fij
    private static final int CLOUD_FACTOR = 4, ARROW_FACTOR = 8;

    /** Родина зілля: вогнегриб (грибогоріння) чи зірочник (грибне сяйво) */
    public static final class Family {
        final String name;
        final Supplier<MobEffect> effect;
        final Supplier<Item> fungus;
        final List<RegistryObject<Potion>> drink = new ArrayList<>(), splash = new ArrayList<>(), lingering = new ArrayList<>();
        RegistryObject<Potion> arrow;

        Family(String name, Supplier<MobEffect> effect, Supplier<Item> fungus, int[] d, int[] s, int[] l, int a) {
            this.name = name;
            this.effect = effect;
            this.fungus = fungus;
            for (int i = 0; i < d.length; i++) drink.add(reg(name + (i == 0 ? "" : "_" + (i + 1)), d[i], i));
            for (int i = 0; i < s.length; i++) splash.add(reg(name + "_splash" + (i == 0 ? "" : "_" + (i + 1)), s[i], i));
            for (int i = 0; i < l.length; i++) lingering.add(reg(name + "_lingering" + (i == 0 ? "" : "_" + (i + 1)), l[i] * CLOUD_FACTOR, i));
            arrow = reg(name + "_arrow", a * ARROW_FACTOR, 0);
        }

        // назва для перекладу — спільна (name), рівень показує «II», «III»
        private RegistryObject<Potion> reg(String id, int seconds, int amplifier) {
            return POTIONS.register(id, () -> new Potion(name, new MobEffectInstance(effect.get(), seconds * 20, amplifier)));
        }

        List<RegistryObject<Potion>> of(Form f) {
            return switch (f) { case DRINK -> drink; case SPLASH -> splash; case LINGERING -> lingering; case ARROW -> List.of(arrow); };
        }

        /** Рівень (0, 1, 2) зілля цієї родини, або −1 — не наше */
        int level(Potion p) {
            for (Form f : Form.values()) {
                List<RegistryObject<Potion>> list = of(f);
                for (int i = 0; i < list.size(); i++) if (list.get(i).get() == p) return f == Form.ARROW ? 0 : i;
            }
            return -1;
        }

        Potion get(Form f, int level) {
            List<RegistryObject<Potion>> list = of(f);
            return list.get(Math.min(level, list.size() - 1)).get();
        }
    }

    public static final Family BURN = new Family("fire_fungus", () -> ModEffects.FUNGUS_BURN.get(), () -> ModItems.FIRE_FUNGUS.get(),
            BURN_DRINK, BURN_SPLASH, BURN_LINGERING, BURN_ARROW);
    public static final Family GLOW = new Family("star_fungus", () -> ModEffects.FUNGUS_GLOW.get(), () -> ModItems.STAR_FUNGUS.get(),
            GLOW_DRINK, GLOW_SPLASH, GLOW_LINGERING, GLOW_ARROW);
    private static final Family[] FAMILIES = { BURN, GLOW };

    private ModPotions() {
    }

    static Form formOf(ItemStack s) {
        if (s.is(Items.SPLASH_POTION)) return Form.SPLASH;
        if (s.is(Items.LINGERING_POTION)) return Form.LINGERING;
        if (s.is(Items.TIPPED_ARROW)) return Form.ARROW;
        return s.is(Items.POTION) ? Form.DRINK : null;
    }

    /** Правильне зілля для цієї пляшки (ванільна гра після пороху чи дихання дракона лишає старий запис) */
    public static void fix(ItemStack s) {
        Form f = formOf(s);
        if (f == null) return;
        Potion p = PotionUtils.getPotion(s);
        for (Family fam : FAMILIES) {
            int lvl = fam.level(p);
            if (lvl < 0) continue;
            Potion right = fam.get(f, lvl);
            if (right != p) PotionUtils.setPotion(s, right);
            return;
        }
    }

    public static ItemStack stack(Item bottle, Potion p) {
        return PotionUtils.setPotion(new ItemStack(bottle), p);
    }

    // ---------- варіння ----------
    private record Step(Family fam, Form form, int fromLevel, Item ingredient, int toLevel) implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack input) {
            if (fromLevel < 0) return input.is(Items.POTION) && PotionUtils.getPotion(input) == Potions.AWKWARD;
            return formOf(input) == form && PotionUtils.getPotion(input) == fam.get(form, fromLevel);
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            return ingredient.is(this.ingredient);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            if (!isInput(input) || !isIngredient(ingredient)) return ItemStack.EMPTY;
            Item bottle = switch (form) { case SPLASH -> Items.SPLASH_POTION; case LINGERING -> Items.LINGERING_POTION; default -> Items.POTION; };
            return stack(bottle, fam.get(form, toLevel));
        }
    }

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Family fam : FAMILIES) {
                Item fungus = fam.fungus.get();
                BrewingRecipeRegistry.addRecipe(new Step(fam, Form.DRINK, -1, fungus, 0));                  // незграбне + гриб → I
                BrewingRecipeRegistry.addRecipe(new Step(fam, Form.DRINK, 0, Items.REDSTONE, 1));           // I + редстоун → II
                for (Form f : new Form[] { Form.SPLASH, Form.LINGERING }) {
                    BrewingRecipeRegistry.addRecipe(new Step(fam, f, 0, Items.REDSTONE, 1));                // I → II
                    BrewingRecipeRegistry.addRecipe(new Step(fam, f, 1, Items.GLOWSTONE_DUST, 2));          // II → III
                }
            }
        });
    }

    // порох / дихання дракона робить ванільна гра — тут ставимо правильну тривалість
    public static void onBrewed(PotionBrewEvent.Post event) {
        for (int i = 0; i < event.getLength(); i++) fix(event.getItem(i));
    }

    // осідаюче + 8 стріл (ванільний рецепт) → стріли зі «стрілецькою» тривалістю (Fij: 1 хв / 30 с)
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        fix(event.getCrafting());
    }

    /** Усі наші зілля й стріли у правильних пляшках (для творчої вкладки) */
    public static List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>();
        for (Family fam : FAMILIES) {
            for (RegistryObject<Potion> p : fam.drink) out.add(stack(Items.POTION, p.get()));
            for (RegistryObject<Potion> p : fam.splash) out.add(stack(Items.SPLASH_POTION, p.get()));
            for (RegistryObject<Potion> p : fam.lingering) out.add(stack(Items.LINGERING_POTION, p.get()));
            out.add(stack(Items.TIPPED_ARROW, fam.arrow.get()));
        }
        return out;
    }

    /** Наше зілля не в тій пляшці (ванільна вкладка додає кожне в усі пляшки) */
    public static boolean wrongBottle(ItemStack s) {
        Form f = formOf(s);
        if (f == null) return false;
        Potion p = PotionUtils.getPotion(s);
        for (Family fam : FAMILIES) {
            if (fam.level(p) < 0) continue;
            for (RegistryObject<Potion> r : fam.of(f)) if (r.get() == p) return false;
            return true;
        }
        return false;
    }
}

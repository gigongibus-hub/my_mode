package com.gigongibus.hungrycows;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Духовний документ, крок 2 (Fij): голка з кров'ю + папір (будь-де у верстаті) → папір з кров'ю тієї самої корови.
 * Голка лишається — повертається чистою (Fij). Звичайний рецепт не переносить «чия кров», тому свій.
 */
public class BloodPaperRecipe extends CustomRecipe {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, HungryCowsMod.MODID);
    public static final RegistryObject<RecipeSerializer<BloodPaperRecipe>> SERIALIZER =
            SERIALIZERS.register("blood_paper", () -> new SimpleCraftingRecipeSerializer<>(BloodPaperRecipe::new));
    public static final RegistryObject<RecipeSerializer<CowReaderRecipe>> READER_SERIALIZER =
            SERIALIZERS.register("cow_reader", () -> new SimpleCraftingRecipeSerializer<>(CowReaderRecipe::new));

    public BloodPaperRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    // рівно одна голка з кров'ю і рівно один папір, більше нічого
    private static int[] find(CraftingContainer c) {
        int needle = -1, paper = -1;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.BLOODY_NEEDLE.get()) && needle < 0) needle = i;
            else if (s.is(Items.PAPER) && paper < 0) paper = i;
            else return null;
        }
        return needle >= 0 && paper >= 0 ? new int[] { needle, paper } : null;
    }

    @Override
    public boolean matches(CraftingContainer c, Level level) {
        return find(c) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer c, RegistryAccess access) {
        int[] f = find(c);
        if (f == null) return ItemStack.EMPTY;
        ItemStack paper = new ItemStack(ModItems.BLOODY_PAPER.get());
        ItemStack needle = c.getItem(f[0]);
        if (needle.hasTag()) paper.setTag(needle.getTag().copy());   // чия кров
        return paper;
    }

    // голка повертається чистою
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer c) {
        NonNullList<ItemStack> left = NonNullList.withSize(c.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(ModItems.BLOODY_NEEDLE.get())) left.set(i, new ItemStack(ModItems.NEEDLE.get()));
        }
        return left;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }
}

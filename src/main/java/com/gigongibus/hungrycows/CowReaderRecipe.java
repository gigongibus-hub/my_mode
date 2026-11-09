package com.gigongibus.hungrycows;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * «Корововед» (Fij): верстат 3×3 — посередині папір з кров'ю, у решті 8 клітинок щіпки піску душ.
 * Свій рецепт, бо треба перенести знімок корови з паперу.
 */
public class CowReaderRecipe extends CustomRecipe {
    public CowReaderRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer c, Level level) {
        if (c.getWidth() != 3 || c.getHeight() != 3) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = c.getItem(i);
            if (i == 4 ? !s.is(ModItems.BLOODY_PAPER.get()) : !s.is(ModItems.SOUL_SAND_PINCH.get())) return false;
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer c, RegistryAccess access) {
        ItemStack out = new ItemStack(ModItems.COW_READER.get());
        ItemStack paper = c.getItem(4);
        if (paper.hasTag()) out.setTag(paper.getTag().copy());
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w >= 3 && h >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BloodPaperRecipe.READER_SERIALIZER.get();
    }
}

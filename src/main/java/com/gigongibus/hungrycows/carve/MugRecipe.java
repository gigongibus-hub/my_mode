package com.gigongibus.hungrycows.carve;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Кружка (Fij «все це разом»): заготовка + вушка + ручка будь-де у верстаті → кружка; використань — за середньою точністю деталей */
public class MugRecipe extends CustomRecipe {
    public MugRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private static ItemStack[] find(CraftingContainer c) {
        ItemStack body = null, ears = null, handle = null;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(Carving.MUG_BODY.get()) && body == null) body = s;
            else if (s.is(Carving.MUG_EARS.get()) && ears == null) ears = s;
            else if (s.is(Carving.MUG_HANDLE.get()) && handle == null) handle = s;
            else return null;
        }
        return body != null && ears != null && handle != null ? new ItemStack[]{ body, ears, handle } : null;
    }

    @Override
    public boolean matches(CraftingContainer c, Level level) {
        return find(c) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer c, RegistryAccess access) {
        ItemStack[] f = find(c);
        if (f == null) return ItemStack.EMPTY;
        float q = (CarvedPartItem.quality(f[0]) + CarvedPartItem.quality(f[1]) + CarvedPartItem.quality(f[2])) / 3;
        return MugItem.withUses(new ItemStack(Carving.MUG.get()), Carving.usesFor(q));
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Carving.MUG_RECIPE.get();
    }
}

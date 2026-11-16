package com.gigongibus.hungrycows.carve;

import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/** Креслення (Fij): кладеться на верстак першим; визначає, яку деталь вирізаєш і з яким шаблоном порівнюють */
public class BlueprintItem extends Item {
    public final String template;
    public final Supplier<Item> result;

    public BlueprintItem(String template, Supplier<Item> result, Properties p) {
        super(p);
        this.template = template;
        this.result = result;
    }
}

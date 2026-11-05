package com.gigongibus.hungrycows;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Книга «Корововедення»: збирає сторінки про корову на сервері й надсилає гравцю.
 * Кожен розділ починається з рядка "#Назва" і відкривається з нової сторінки.
 * Нова механіка = новий розділ у pages().
 */
public final class CowBook {

    private CowBook() {
    }

    public static void open(ServerPlayer player, HungryCow cow) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenCowBookPacket(pages(player, cow)));
    }

    static List<String> pages(Player reader, HungryCow cow) {
        List<String> out = new ArrayList<>();
        boolean female = cow.repro.isFemale();

        // 1. Загальне
        out.add("#" + cow.family.name);
        out.add("Стать: " + (female ? "самка" : "бик"));
        out.add("Вік: " + (cow.isBaby() ? "теля" : "доросла"));
        out.add("Здоров'я: " + Math.round(cow.getHealth()) + " з " + Math.round(cow.getMaxHealth()));
        if (cow.repro.isExhausted()) out.add("Більше не народжує, ходить повільніше");

        // 2. Потреби
        out.add("#Потреби");
        out.add("Голод: " + cow.hunger + " (шукає їжу з 30, голодує з " + HungryCow.STARVE_AT + ")");
        out.add("Втома: " + cow.fatigue + " (відпочиває з 40)");
        out.add("Страх: " + cow.fear + " (тікає з " + cow.scaredAt() + ")");
        if (cow.choking) out.add("ПОДАВИЛАСЯ! Вдар її, щоб урятувати");
        if (cow.isBaby()) out.add("Теля п'є лише молоко");

        // 3. Довіра
        out.add("#Довіра");
        int t = cow.trustOf(reader);
        out.add("До тебе: " + t);
        out.add(trustStatus(t));
        out.add("Пам'ятає гравців: " + cow.trust.knownPlayers());

        // 4. Розмноження й молоко
        out.add("#Розмноження і молоко");
        if (cow.isBaby()) {
            out.add("Теля ще не розмножується");
        } else {
            if (female) {
                out.add("Народжень: " + cow.repro.births() + " з " + cow.repro.birthLimit());
                if (cow.repro.inLabor()) {
                    out.add("Пологи: ще " + cow.repro.laborSecondsLeft() + " с");
                } else if (cow.repro.isPregnant()) {
                    int s = cow.repro.pregnantSecondsLeft();
                    out.add("Вагітна: ще " + (s / 60) + ":" + String.format("%02d", s % 60));
                }
                String milk = cow.milk.label();
                out.add(milk != null ? milk + " з " + CowMilk.MAX : "Молока немає (лише після пологів)");
                out.add("Раціон з останніх пологів: " + cow.milk.dietText());
            } else {
                out.add("Бик: молока не дає");
            }
        }

        // 5. Родовід
        out.add("#Родовід");
        out.addAll(cow.family.familyLines());

        // 6. Характер
        out.add("#Характер");
        out.add("Риса = ген " + Math.round(CowCharacter.GENE_SHARE * 100) + "% + досвід");
        for (CowCharacter.Trait trait : CowCharacter.Trait.values()) {
            out.add(trait.title + ": " + cow.character.get(trait));
        }
        return out;
    }

    private static String trustStatus(int t) {
        if (t >= CowTrust.BOND_FROM) return "Найкращий друг: ходить за тобою";
        if (t >= CowTrust.FRIEND_FROM) return "Друг: не боїться, підходить";
        if (t > 0) return "Трохи довіряє";
        if (t == 0) return "Не знає тебе";
        return "Ворожа: може напасти";
    }
}

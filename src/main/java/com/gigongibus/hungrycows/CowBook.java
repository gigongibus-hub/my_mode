package com.gigongibus.hungrycows;

import net.minecraft.core.BlockPos;
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
        out.add(cow.aging.line());
        out.add("Масть: " + cow.genes.coatName());
        out.add("Здоров'я: " + Math.round(cow.getHealth()) + " з " + Math.round(cow.getMaxHealth()));
        if (cow.repro.isExhausted()) out.add("Більше не народжує, ходить повільніше");

        // 2. Потреби
        out.add("#Потреби");
        BlockPos home = cow.home.home();
        BlockPos tree = cow.home.treeSpot();
        out.add("Дім: " + (home == null ? (tree != null ? "немає, спить під деревом (" + tree.getX() + " " + tree.getY() + " " + tree.getZ() + ")" : "ще не знайшла") : home.getX() + " " + home.getY() + " " + home.getZ()
                + " (" + Math.round(Math.sqrt(cow.distanceToSqr(home.getX(), home.getY(), home.getZ()))) + " бл.)")
                + (home != null ? ", бали " + cow.home.homeScore() + " з 13" : "")
                + (cow.sleeping ? " — спить" : ""));
        out.add("Спрага: " + cow.thirst + " (п'є з " + HungryCow.THIRSTY_AT + ", зневоднення з " + HungryCow.DEHYDRATE_AT + ")"
                + (cow.inSun() ? " — на сонці" : ""));
        out.add("Голод: " + cow.hunger + " (шукає їжу з " + cow.hungryAt() + " з урахуванням апетиту, голодує з "
                + HungryCow.STARVE_AT + ")");
        out.add("Втома: " + cow.fatigue + " (відпочиває за відчуттям з " + HungryCow.TIRED_AT + ")");
        out.add("Страх: " + cow.fear + " (тікає за відчуттям з " + cow.scaredAt() + ")");
        out.add(cow.heat.line());
        out.add(cow.disease.line());
        if (cow.choking) out.add("ПОДАВИЛАСЯ! Вдар її, щоб урятувати");
        if (cow.isBaby()) out.add("Теля п'є лише молоко");

        // 2б. Самопочуття і речовини
        out.addAll(cow.chem.lines());

        // 3. Довіра
        out.add("#Довіра");
        int t = cow.realTrustOf(reader);
        if (HungryCow.isDisguised(reader)) out.add("Ти в гарбузі: корова тебе не впізнає");
        out.add("До тебе: " + t);
        out.add(trustStatus(t));
        out.add("Пам'ятає гравців: " + cow.trust.knownPlayers());

        // 4. Розмноження й молоко
        out.add("#Розмноження і молоко");
        if (cow.isBaby()) {
            out.add("Теля ще не розмножується");
        } else {
            if (female) {
                out.add("Телят народила: " + cow.repro.births() + " з " + cow.repro.birthLimit());
                out.add("Цикл: " + cow.cycle.phase().title
                        + (cow.cycle.phase() != CowCycle.Phase.NONE && cow.cycle.phase() != CowCycle.Phase.PREGNANT
                        ? ", ще " + cow.cycle.daysLeft() + " діб" : ""));
                if (cow.repro.isFreemartin()) out.add("Безплідна: фримартинізм (сестра бика-двійняти)");
                else if (cow.repro.isInfertile()) out.add("Безплідна від народження");
                if (cow.repro.inLabor()) {
                    out.add("Пологи: ще " + cow.repro.laborSecondsLeft() + " с"
                            + (cow.repro.isAssisted() ? " (допомогли)" : ""));
                    out.add("Допомогти: підійди ззаду, присядь і тримай ПКМ порожньою рукою 3 с");
                } else if (cow.cycle.pregnancyVisible()) {
                    out.add("Вагітна: ще " + cow.repro.pregnancyText());
                } else if ((cow.cycle.inEstrus() && cow.cycle.matedThisEstrus()) || cow.cycle.inMetestrus()) {
                    out.add("Чи вагітна: поки невідомо (стане видно в діеструсі)");
                } else if (cow.cycle.phase() == CowCycle.Phase.DIESTRUS && cow.cycle.matedThisEstrus()) {
                    out.add("Цього разу не завагітніла");
                }
                String milk = cow.milk.label();
                out.add(milk != null ? milk + " з " + cow.milk.max() : "Молока немає (лише після пологів)");
                out.add("Раціон з останніх пологів: " + cow.milk.dietText());
            } else {
                out.add("Бик: молока не дає");
            }
        }

        // 5. Родовід
        out.add("#Родовід");
        out.addAll(cow.family.familyLines());
        out.addAll(cow.inbreeding.lines());

        // 6. Характер
        out.add("#Характер");
        out.add("Риса = ген " + Math.round(CowCharacter.GENE_SHARE * 100) + "% + досвід");
        for (CowCharacter.Trait trait : CowCharacter.Trait.values()) {
            out.add(trait.title + ": " + cow.character.get(trait));
        }
        out.add("Дощ: " + cow.rainAttitude());
        if (cow.inHotBiome()) out.add("Жаркий біом: удень спить, вночі пасеться");
        // 6а. Зір
        out.addAll(cow.vision.lines());
        out.addAll(cow.smell.lines());
        out.addAll(cow.hearing.lines());
        out.addAll(cow.taste.lines());
        // 6б. Мозок
        out.addAll(cow.brain.lines());

        // 6г. Стадо
        out.addAll(cow.herd.lines());

        // 6в. Пам'ять
        out.addAll(cow.memory.lines(reader));

        // 7. Гени
        out.add("#Гени");
        out.add("Число = середнє двох копій (від матері / від батька).");
        out.add("50 — як у звичайної корови. ▲ — вище 65, ▼ — нижче 35.");
        out.add("Ген 100 або 0 змінює звичайне число на ±30%.");
        out.addAll(cow.genes.lines());
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

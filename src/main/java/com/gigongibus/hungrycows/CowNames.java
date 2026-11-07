package com.gigongibus.hungrycows;

import net.minecraft.util.RandomSource;

// Імена для корів (бирка з іменем їх змінює)
public final class CowNames {
    private static final String[] FEMALE = {
            "Зірка", "Ласка", "Мілка", "Квітка", "Ромашка", "Красуля", "Бурьонка", "Маня",
            "Лиска", "Чорнушка", "Білянка", "Калина", "Вишня", "Пава", "Зоряна", "Ягідка",
            "Липка", "Росинка", "Марта", "Голубка", "Веселка", "Ночка", "Мальва", "Дуся"
    };
    private static final String[] MALE = {
            "Буян", "Грім", "Борька", "Мартин", "Дуб", "Вітер", "Байрак", "Чорниш",
            "Бровко", "Козак", "Орлик", "Тарасик", "Яшко", "Бурий", "Сокіл", "Граф"
    };

    private CowNames() {
    }

    public static String random(boolean female, RandomSource random) {
        String[] list = female ? FEMALE : MALE;
        return list[random.nextInt(list.length)];
    }
}

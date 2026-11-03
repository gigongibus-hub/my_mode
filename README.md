# Hungry Cows

A Minecraft Forge mod (1.20.1, Forge 47.4.x) that adds the hungry cow (`hungrycows:hungry_cow`).
Мод Minecraft Forge, що додає голодну корову з голодом, втомою, страхом, довірою до гравців,
подавленням їжею та вагітністю.

## Структура

```
src/main/java/com/gigongibus/hungrycows/
  HungryCowsMod.java     головний клас мода
  ModEntities.java       реєстрація корови та яйця призову
  ModEvents.java         атрибути, вкладка творчого режиму, рендерер
  HungryCow.java         сама корова: показники, годування, довіра, напади, збереження
  CowFood.java           таблиця їжі та її ефектів
  CowTrust.java          довіра до кожного гравця
  CowReproduction.java   стать, вагітність, пологи
  ai/                    цілі поведінки (страх, пошук їжі, відпочинок, пологи тощо)
src/main/resources/assets/hungrycows/
  lang/                  назви en_us, uk_ua
  models/item/           модель яйця призову
```

## Тестові налаштування

Числа для тестів зібрані на початку `HungryCow.java`, `CowTrust.java` і `CowReproduction.java`
(`TEST_MULTIPLIER`, `CHOKE_TEST_CHANCE`, `CALF_DEATH_CHANCE`, `DEBUG_MESSAGES` тощо).

## Збірка

```
./gradlew build
```

Готовий файл: `build/libs/hungrycows-<версія>.jar`.

# Hungry Cows — нотатки для Claude

Автор мода — Fij, спілкується українською. Пояснювати простими словами, з погляду гравця.
Якщо щось незрозуміло, спершу спитати, потім писати код.

## Технічні дані
- Minecraft 1.20.1, Forge 47.4.x, Java 17, маппінги `official`.
- Пакет `com.gigongibus.hungrycows`, mod id `hungrycows`, сутність `hungrycows:hungry_cow`.
- Головний клас: `HungryCowsMod`.
- Збірка: `./gradlew build` → `build/libs/hungrycows-<версія>.jar`.

## Файли (`src/main/java/com/gigongibus/hungrycows/`)
- `HungryCowsMod`: реєстрація предметів, сутностей, ефектів.
- `ModEntities`: корова та яйце призову. `ModItems`: відро молока з якістю. `ModEffects`: ефект «Друг корови».
- `ModEvents`: атрибути, вкладки творчого режиму, рендерер.
- `HungryCow`: сама корова (голод, втома, страх, годування, довіра, напади, захист теляти, доїння, збереження).
- `CowFood`: таблиця їжі та ефектів. `CowTrust`: довіра до кожного гравця (від −100 до +200).
- `CowReproduction`: стать, вагітність, пологи, ліміт телят, виснаження.
- `CowMilk`: шкала молока, лактація, раціон → якість. `MilkQuality`: що робить молоко кожної якості.
- `QualityMilkBucketItem`: предмет «Відро молока (якість X)».
- `CowFamily`: ім'я, мати (корова або гравець), забування матері, родинне дерево, діти. `CowNames`: імена.
- `ai/`: 11 цілей: `LaborGoal`, `DefendCalfGoal`, `FearGoal`, `NurseGoal`, `DrinkMilkGoal`, `SeekGrassGoal`,
  `RestGoal`, `FindFosterGoal`, `AvoidBluetGoal`, `ApproachPlayerGoal`, `FollowMotherGoal`.
- Ресурси `assets/hungrycows/`: `lang/` (en_us, uk_ua), `models/item/`, `textures/mob_effect/cow_friend.png`.

## Пріоритети цілей (з `HungryCow.registerGoals()`, менше число = важливіше)
0 Float · 1 Labor · 2 DefendCalf · 3 Fear · 4 FirePanic · 5 Nurse · 6 DrinkMilk · 7 SeekGrass · 8 Breed ·
9 Rest · 10 FindFoster · 11 Tempt (кавун, гарбуз, скибка, яблуко, морква) · 12 AvoidBluet · 13 ApproachPlayer ·
14 FollowMother · 15 WaterAvoidingRandomStroll · 16 LookAtPlayer · 17 RandomLookAround.
`super.registerGoals()` не викликається: усі ванільні цілі задано явно.

## Зроблено
- Голод, втома, страх, пошук їжі з вибором за вигодою, квіти за апетитом (мак 15%).
- Таблиця їжі з ефектами, подавлення, довіра, ворожа корова.
- Стать, вагітність, пологи; ліміт 5–7 телят (5%: 10–12), потім корова повільніша на 30%.
- Молоко лише після пологів: шкала 100, відро 25 (10 з «Другом корови»), якість A/В/Б/С/отруєне за раціоном.
- Теля: лише молоко, тримається біля матері (25 блоків), забуває її за 5 хв окремо, шукає прийомну самку
  (50% відмова), гравець може вигодувати сироту → довіра +200, коли виросте.
- Захист теляти: мати й інші корови б'ють гравця 7 с, потім тікають.
- Імена й родинне дерево (присісти + ПКМ порожньою рукою, поки для тестів).
- Ефект «Друг корови». Бирка з іменем обробляється (`lastLabel` у `HungryCow.aiStep`).

## Правила
- Нові числа класти в таблицю констант на початку файлу з коментарем українською.
- Кожна ціль, у якої в `tick()` є таймери за `tickCount`, має
  `@Override public boolean requiresUpdateEveryTick() { return true; }`. Інакше `tick()` викликається раз на два тіки,
  і в частини корів умова `tickCount % N == 0` ніколи не спрацьовує.
- Порядок полів у `HungryCow` не змінювати: `repro` перед `milk` і `family` (`CowFamily` у конструкторі читає стать).
- У `readAdditionalSaveData` спершу `repro.load`, потім `family.load`.
- `ageBoundaryReached()` спрацьовує лише при переході теля ↔ доросла, і `isBaby()` там уже новий.

## Тестові значення: повернути перед релізом
- `CowTrust.TEST_MULTIPLIER = 25` → 1; `CowTrust.ONE_FEED_PER_DAY = false` → true.
- `CowReproduction.CALF_DEATH_CHANCE = 0.5F` → 0.02F.
- `HungryCow.CHOKE_TEST_CHANCE = 0.5F` → 0; `HungryCow.CHOKE_MULTIPLIER = 100` → 1.
- `HungryCow.DEBUG_MESSAGES = true` → false; `SeekGrassGoal.DEBUG = true` → false.
- Службовий напис над коровою (`setCustomName` щосекунди в `aiStep`) і повідомлення «Корова з'їла» / «Корова померла» прибрати або сховати за `DEBUG_MESSAGES`.
- Перегляд дерева через присідання + ПКМ і відра молока у вкладці творчого режиму: вирішити, чи лишати.

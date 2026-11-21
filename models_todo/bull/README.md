# Модель бика з анімаціями від Fij (для Claude Code)

Зробив Fij у Blockbench. Формат — **Bedrock / GeckoLib** (`.geo.json` 1.12.0 + анімації 1.8.0),
тобто готово для GeckoLib. Файли **не змінені**, лише перейменовані (оригінал — папка «бик» на робочому столі Fij).

## Файли
| Файл | Оригінал | Що це |
|---|---|---|
| `bull.geo.json` | `модель бика/cow_eats.geo.json` | модель (identifier зараз `geometry.unknown` — можна перейменувати) |
| `bull.png` | `текстура бика/byk.png` | текстура 128×128 (збігається з texture_width/height моделі) |
| `bull_walk.animation.json` | `ходьба.json` | анімація `jty` — ходьба, 2 с, цикл |
| `bull_run.animation.json` | `біг.json` | анімація `bigh` — біг, 2 с, цикл (ноги ±37–40°, у ходьби ±17–20°) |
| `bull_attack_prepare.animation.json` | `підготовка_до_атаки.json` | `pidhutovka_atak` — копає передньою правою ногою й рухає головою, 1 с, цикл |
| `bull_attack.animation.json` | `атака.json` | `atac` — біг з опущеною мордою (25°), 2 с, цикл |
| `bull_idle_all.animation.json` | `спокій_1.json` | усі три анімації спокою разом: `is_standing` (4 с), `is_standing2` (4,75 с), `is_standing3` (4,75 с) |
| `bull_idle_2.animation.json` | `спокій_2.json` | лише `is_standing2` (те саме, що в idle_all) |
| `bull_idle_3.animation.json` | `спокій_3.json` | лише `is_standing3` (те саме, що в idle_all) |

Достатньо `bull_idle_all` — idle_2 та idle_3 його дублюють.

## Кістки (bones)
`byk` (корінь) → `tilo` (тіло) → `tilo1`, `hwist` (хвіст);
`nogi` → `peredni` → `prawa_perednr_kopyto`, `liwa_peredne_kopyto`; `zadne` → `prawa_zadne_kopyto`, `liwe_zadne_kopyto`;
`golowa` → `sija` (шия), `golowabez` → `morda` → `nis` (ніс); `vuho1`, `vuho2` (вуха).
Усі кістки з анімацій є в моделі (перевірено).

## Спитай у Fij перед кодом
- Робимо через **GeckoLib** (формат уже під нього) чи переносимо у ванільні Java-анімації?
- Це модель лише для **бика** (самця), а корови лишаються з ванільною моделлю?
- Коли яка анімація: спокій — випадково одна з трьох? `attack_prepare` — перед нападом при довірі −100? `attack` — під час нападу?
- `атака` і `підготовка` зараз зациклені (loop: true) — так задумано?
- Біг і ходьба однакової тривалості (2 с) — біг, мабуть, програвати швидше.
- Анімацій їжі, сну/лежання, жування немає (назва моделі `cow_eats` — можливо, планувалась).

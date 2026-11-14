# Текстури сіна від Fij (для Claude Code)

Намалював Fij. Усі текстури 16×16 PNG. **Це стадії й стани ОДНОГО блоку сіна**, а не окремі блоки.
Назви: `hay_<стадія>_<стан>_<сторона>.png`.

## Стадії висихання
| Код | Стадія |
|---|---|
| `fresh` | 1 свіже (зелене) |
| `wilted` | 2 підсохле |
| `dry` | 3 сухе |
| `overdried` | 4 пересохле |

## Стани
| Код | Стан | Є на стадіях |
|---|---|---|
| `clean` | чисте | усі |
| `slightly_dirty` | трохи брудне | усі |
| `very_dirty` | дуже брудне | усі |
| `mold_dirt` | з зеленою пліснявою та брудом | усі |
| `mold` | з зеленою пліснявою | усі |
| `rotten` | гниле | усі |
| `wet` | мокре | усі |
| `star_fungus` | гниле, заражене зірковим грибом | лише fresh |
| `fire_fungus` | гниле, заражене вогняним грибом | лише dry |
| `apple` | з фруктами (яблуко) | лише fresh |
| `carrot` | з овочами (морква) | лише fresh |

## Сторони
| Код | Що це |
|---|---|
| `side_strings` | бік з нитками |
| `side_plain` | бік без ниток |
| `top` | верх і низ |

## Особливості
- `hay_bundle_item.png` — предмет (пучок свіжої трави), **з якого крафтиться блок свіжого сіна**.
- У стану `wet` (мокре) **немає верху/низу** на жодній стадії — спитай у Fij, яку текстуру брати.
- У `apple` і `carrot` верх такий самий, як у свіжого чистого сіна (у Fij це той самий файл).
- Механіки (як сіно висихає, брудниться, гниє, як впливає на корів, коли нитки, а коли без) — **спитай у Fij перед кодом**.

Блок у код ще НЕ додано. Стара папка `textures_todo/*.png` (7 файлів, перша версія сіна) — застаріла, ця її замінює.

## Відповідність файлів (нова назва ← оригінал у папці «сіно» на робочому столі Fij)
| Нова назва | Оригінал |
|---|---|
| `hay_fresh_clean_side_strings.png` | `1 свіже сіно/1 свіже сіно/1bik.png` |
| `hay_fresh_clean_side_plain.png` | `1 свіже сіно/1 свіже сіно/2bikbez.png` |
| `hay_fresh_clean_top.png` | `1 свіже сіно/1 свіже сіно/3verhtanyz.png` |
| `hay_fresh_slightly_dirty_side_strings.png` | `1 свіже сіно/2 свіже трохи грязне сіно/1bik.png` |
| `hay_fresh_slightly_dirty_side_plain.png` | `1 свіже сіно/2 свіже трохи грязне сіно/2bikbez.png` |
| `hay_fresh_slightly_dirty_top.png` | `1 свіже сіно/2 свіже трохи грязне сіно/3verhtanyz.png` |
| `hay_fresh_very_dirty_side_strings.png` | `1 свіже сіно/3 свіже дуже грязне сіно/1bik.png` |
| `hay_fresh_very_dirty_side_plain.png` | `1 свіже сіно/3 свіже дуже грязне сіно/2bikbez.png` |
| `hay_fresh_very_dirty_top.png` | `1 свіже сіно/3 свіже дуже грязне сіно/3verhtanyz.png` |
| `hay_fresh_mold_dirt_side_strings.png` | `1 свіже сіно/4 свіже сіно з зеленою плесеню та грязю/1bik.png` |
| `hay_fresh_mold_dirt_side_plain.png` | `1 свіже сіно/4 свіже сіно з зеленою плесеню та грязю/2bikbez.png` |
| `hay_fresh_mold_dirt_top.png` | `1 свіже сіно/4 свіже сіно з зеленою плесеню та грязю/3verhtanyz.png` |
| `hay_fresh_mold_side_strings.png` | `1 свіже сіно/5 свіже сіно з зеленою плесеню/1bik.png` |
| `hay_fresh_mold_side_plain.png` | `1 свіже сіно/5 свіже сіно з зеленою плесеню/2bikbez.png` |
| `hay_fresh_mold_top.png` | `1 свіже сіно/5 свіже сіно з зеленою плесеню/3verhtanyz.png` |
| `hay_fresh_rotten_side_strings.png` | `1 свіже сіно/6 свіже гніле сіно/1bik.png` |
| `hay_fresh_rotten_side_plain.png` | `1 свіже сіно/6 свіже гніле сіно/2bikbez.png` |
| `hay_fresh_rotten_top.png` | `1 свіже сіно/6 свіже гніле сіно/3verhtanyz.png` |
| `hay_fresh_wet_side_strings.png` | `1 свіже сіно/7 свіже мокре сіно/1bik.png` |
| `hay_fresh_wet_side_plain.png` | `1 свіже сіно/7 свіже мокре сіно/2bikbez.png` |
| `hay_fresh_star_fungus_side_strings.png` | `1 свіже сіно/8 свіже гніле заражине зірко грибом сіно/1bik.png` |
| `hay_fresh_star_fungus_side_plain.png` | `1 свіже сіно/8 свіже гніле заражине зірко грибом сіно/2bikbez.png` |
| `hay_fresh_star_fungus_top.png` | `1 свіже сіно/8 свіже гніле заражине зірко грибом сіно/3verhtanyz.png` |
| `hay_fresh_apple_side_strings.png` | `1 свіже сіно/9 свіже сіно з фруктами яблуко/1bik.png` |
| `hay_fresh_apple_side_plain.png` | `1 свіже сіно/9 свіже сіно з фруктами яблуко/2bikbez.png` |
| `hay_fresh_carrot_side_strings.png` | `1 свіже сіно/10 свіже сіно з овочами морква/1bik.png` |
| `hay_fresh_carrot_side_plain.png` | `1 свіже сіно/10 свіже сіно з овочами морква/2bikbez.png` |
| `hay_wilted_clean_side_strings.png` | `2 під сохле сіно/1 під сохле сіно/1bik.png` |
| `hay_wilted_clean_side_plain.png` | `2 під сохле сіно/1 під сохле сіно/2bikbez.png` |
| `hay_wilted_clean_top.png` | `2 під сохле сіно/1 під сохле сіно/2verhtanyz.png` |
| `hay_wilted_slightly_dirty_side_strings.png` | `2 під сохле сіно/2 під сохле трохи грязне сіно/1bik.png` |
| `hay_wilted_slightly_dirty_side_plain.png` | `2 під сохле сіно/2 під сохле трохи грязне сіно/2bikbez.png` |
| `hay_wilted_slightly_dirty_top.png` | `2 під сохле сіно/2 під сохле трохи грязне сіно/3verhtanyz.png` |
| `hay_wilted_very_dirty_side_strings.png` | `2 під сохле сіно/3 під сохле дуже грязне сіно/1bik.png` |
| `hay_wilted_very_dirty_side_plain.png` | `2 під сохле сіно/3 під сохле дуже грязне сіно/2bikbez.png` |
| `hay_wilted_very_dirty_top.png` | `2 під сохле сіно/3 під сохле дуже грязне сіно/3verhtanyz.png` |
| `hay_wilted_mold_dirt_side_strings.png` | `2 під сохле сіно/4 під сохле сіно з зеленою плесеню та грязю/1bik.png` |
| `hay_wilted_mold_dirt_side_plain.png` | `2 під сохле сіно/4 під сохле сіно з зеленою плесеню та грязю/2bikbez.png` |
| `hay_wilted_mold_dirt_top.png` | `2 під сохле сіно/4 під сохле сіно з зеленою плесеню та грязю/3verhtanyz.png` |
| `hay_wilted_mold_side_strings.png` | `2 під сохле сіно/5 під сохле сіно з зеленою плесеню/1bik.png` |
| `hay_wilted_mold_side_plain.png` | `2 під сохле сіно/5 під сохле сіно з зеленою плесеню/2bikbez.png` |
| `hay_wilted_mold_top.png` | `2 під сохле сіно/5 під сохле сіно з зеленою плесеню/3verhtanyz.png` |
| `hay_wilted_rotten_side_strings.png` | `2 під сохле сіно/6 під сохле гніле сіно/1bik.png` |
| `hay_wilted_rotten_side_plain.png` | `2 під сохле сіно/6 під сохле гніле сіно/2bikbez.png` |
| `hay_wilted_rotten_top.png` | `2 під сохле сіно/6 під сохле гніле сіно/3verhtanyz.png` |
| `hay_wilted_wet_side_strings.png` | `2 під сохле сіно/7 під сохле мокре сіно/1bik.png` |
| `hay_wilted_wet_side_plain.png` | `2 під сохле сіно/7 під сохле мокре сіно/2bikbez.png` |
| `hay_dry_clean_side_strings.png` | `3 сухе сіно/1 сухе сіно/1bik.png` |
| `hay_dry_clean_side_plain.png` | `3 сухе сіно/1 сухе сіно/2bikbez.png` |
| `hay_dry_clean_top.png` | `3 сухе сіно/1 сухе сіно/3vefhtanyz.png` |
| `hay_dry_slightly_dirty_side_strings.png` | `3 сухе сіно/2 сухе трохи грязне сіно/1bik.png` |
| `hay_dry_slightly_dirty_side_plain.png` | `3 сухе сіно/2 сухе трохи грязне сіно/2bikbrz.png` |
| `hay_dry_slightly_dirty_top.png` | `3 сухе сіно/2 сухе трохи грязне сіно/3verhtanyz.png` |
| `hay_dry_very_dirty_side_strings.png` | `3 сухе сіно/3 сухе дуже грязне сіно/1bi.png` |
| `hay_dry_very_dirty_side_plain.png` | `3 сухе сіно/3 сухе дуже грязне сіно/3bikbez.png` |
| `hay_dry_very_dirty_top.png` | `3 сухе сіно/3 сухе дуже грязне сіно/3verhtanyz.png` |
| `hay_dry_mold_dirt_side_strings.png` | `3 сухе сіно/4 сухе сіно з зеленою плесеню та грязю/1bik.png` |
| `hay_dry_mold_dirt_side_plain.png` | `3 сухе сіно/4 сухе сіно з зеленою плесеню та грязю/2bikbez.png` |
| `hay_dry_mold_dirt_top.png` | `3 сухе сіно/4 сухе сіно з зеленою плесеню та грязю/3verhtanyz.png` |
| `hay_dry_mold_side_strings.png` | `3 сухе сіно/5 сухе сіно з зеленою плесеню/1bik.png` |
| `hay_dry_mold_side_plain.png` | `3 сухе сіно/5 сухе сіно з зеленою плесеню/2bikbez.png` |
| `hay_dry_mold_top.png` | `3 сухе сіно/5 сухе сіно з зеленою плесеню/3verhtanyz.png` |
| `hay_dry_rotten_side_strings.png` | `3 сухе сіно/6 сухе гніле сіно/1bik.png` |
| `hay_dry_rotten_side_plain.png` | `3 сухе сіно/6 сухе гніле сіно/2bikbez.png` |
| `hay_dry_rotten_top.png` | `3 сухе сіно/6 сухе гніле сіно/3verhtanyz.png` |
| `hay_dry_wet_side_strings.png` | `3 сухе сіно/7 сухе мокре сіно/1bik.png` |
| `hay_dry_wet_side_plain.png` | `3 сухе сіно/7 сухе мокре сіно/2bikbez.png` |
| `hay_dry_fire_fungus_side_strings.png` | `3 сухе сіно/8 сухе гніле заражине вогне грибом сіно/1bik.png` |
| `hay_dry_fire_fungus_side_plain.png` | `3 сухе сіно/8 сухе гніле заражине вогне грибом сіно/2bikbez.png` |
| `hay_dry_fire_fungus_top.png` | `3 сухе сіно/8 сухе гніле заражине вогне грибом сіно/3verhtanyz.png` |
| `hay_overdried_clean_side_strings.png` | `4 пересохле сіно/1 пересохле сіно/1bik.png` |
| `hay_overdried_clean_side_plain.png` | `4 пересохле сіно/1 пересохле сіно/2bikbez.png` |
| `hay_overdried_clean_top.png` | `4 пересохле сіно/1 пересохле сіно/3verhtanyz.png` |
| `hay_overdried_slightly_dirty_side_strings.png` | `4 пересохле сіно/2 пересохле трохи грязне сіно/1bik.png` |
| `hay_overdried_slightly_dirty_side_plain.png` | `4 пересохле сіно/2 пересохле трохи грязне сіно/2bikbez.png` |
| `hay_overdried_slightly_dirty_top.png` | `4 пересохле сіно/2 пересохле трохи грязне сіно/3verhtanyz.png` |
| `hay_overdried_very_dirty_side_strings.png` | `4 пересохле сіно/3 пересохле дуже грязне сіно/1bik.png` |
| `hay_overdried_very_dirty_side_plain.png` | `4 пересохле сіно/3 пересохле дуже грязне сіно/2bikbez.png` |
| `hay_overdried_very_dirty_top.png` | `4 пересохле сіно/3 пересохле дуже грязне сіно/3verhtanyz.png` |
| `hay_overdried_mold_dirt_side_strings.png` | `4 пересохле сіно/4 пересохле сіно з зеленою плесеню та грязю/1bik.png` |
| `hay_overdried_mold_dirt_side_plain.png` | `4 пересохле сіно/4 пересохле сіно з зеленою плесеню та грязю/2bikbez.png` |
| `hay_overdried_mold_dirt_top.png` | `4 пересохле сіно/4 пересохле сіно з зеленою плесеню та грязю/3verhtanyz.png` |
| `hay_overdried_mold_side_strings.png` | `4 пересохле сіно/5 пересохле сіно з зеленою плесеню/1bik.png` |
| `hay_overdried_mold_side_plain.png` | `4 пересохле сіно/5 пересохле сіно з зеленою плесеню/2bikbez.png` |
| `hay_overdried_mold_top.png` | `4 пересохле сіно/5 пересохле сіно з зеленою плесеню/3verhtanyz.png` |
| `hay_overdried_rotten_side_strings.png` | `4 пересохле сіно/6 пересохле гніле сіно/1bik.png` |
| `hay_overdried_rotten_side_plain.png` | `4 пересохле сіно/6 пересохле гніле сіно/2bikbez.png` |
| `hay_overdried_rotten_top.png` | `4 пересохле сіно/6 пересохле гніле сіно/3verhtanyz.png` |
| `hay_overdried_wet_side_plain.png` | `4 пересохле сіно/7 пересохле мокре сіно/1bikbez.png` |
| `hay_overdried_wet_side_strings.png` | `4 пересохле сіно/7 пересохле мокре сіно/2bik.png` |
| `hay_fresh_apple_top.png` | `копія верху свіжого чистого сіна (у Fij той самий файл)` |
| `hay_fresh_carrot_top.png` | `копія верху свіжого чистого сіна (у Fij той самий файл)` |
| `hay_bundle_item.png` | `сіно/sino.png` |
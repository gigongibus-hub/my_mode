"""Моделі й стани блока сіна мода (hungrycows:hay) з текстур Fij (textures/block/hay/hay_<стадія>_<стан>_<сторона>.png).
Запуск з кореня репозиторію: python3 tools/make_hay_assets.py
Правила текстур (Fij): верх мокрого = верх чистого тієї ж стадії; верх яблука/моркви = верх свіжого чистого;
зірочник буває лише на свіжому, вогняний гриб — лише на сухому (на інших стадіях модель бере гниле тієї стадії)."""
import json, os
R = 'src/main/resources/assets/hungrycows'
TEX = R + '/textures/block/hay'
STAGES = ['fresh', 'wilted', 'dry', 'overdried']
CONDS = ['clean', 'slightly_dirty', 'very_dirty', 'mold_dirt', 'mold', 'rotten', 'wet', 'star_fungus', 'fire_fungus', 'apple', 'carrot']

def has(n):
    return os.path.exists(f'{TEX}/{n}.png')

def side(st, c, bound):
    k = 'strings' if bound else 'plain'
    for cc in (c, 'rotten' if 'fungus' in c else 'clean'):
        if has(f'hay_{st}_{cc}_side_{k}'):
            return f'hungrycows:block/hay/hay_{st}_{cc}_side_{k}'
    raise SystemExit(f'немає боку {st} {c}')

def top(st, c):
    if has(f'hay_{st}_{c}_top'):
        return f'hungrycows:block/hay/hay_{st}_{c}_top'
    if c in ('apple', 'carrot'):
        return 'hungrycows:block/hay/hay_fresh_clean_top'
    if 'fungus' in c:
        return f'hungrycows:block/hay/hay_{st}_rotten_top'
    return f'hungrycows:block/hay/hay_{st}_clean_top'   # мокре — верх чистого тієї ж стадії

os.makedirs(R + '/models/block/hay', exist_ok=True)
variants = {}
for st in STAGES:
    for c in CONDS:
        for bound in (False, True):
            name = f'{st}_{c}_{"bound" if bound else "loose"}'
            tex = {'end': top(st, c), 'side': side(st, c, bound)}
            for suffix, parent in (('', 'minecraft:block/cube_column'), ('_horizontal', 'minecraft:block/cube_column_horizontal')):
                with open(f'{R}/models/block/hay/{name}{suffix}.json', 'w') as f:
                    json.dump({'parent': parent, 'textures': tex}, f)
            for axis in ('x', 'y', 'z'):
                key = f'axis={axis},bound={str(bound).lower()},condition={c},stage={st}'
                if axis == 'y':
                    v = {'model': f'hungrycows:block/hay/{name}'}
                elif axis == 'x':
                    v = {'model': f'hungrycows:block/hay/{name}_horizontal', 'x': 90, 'y': 90}
                else:
                    v = {'model': f'hungrycows:block/hay/{name}_horizontal', 'x': 90}
                variants[key] = v
with open(R + '/blockstates/hay.json', 'w') as f:
    json.dump({'variants': variants}, f, indent=1)
# предмет: свій вигляд для кожного стану (властивість hungrycows:variant = індекс / 100, CowHayBlock.variant)
overrides = []
for si, st in enumerate(STAGES):
    for ci, c in enumerate(CONDS):
        for bi, bound in enumerate((False, True)):
            idx = (si * len(CONDS) + ci) * 2 + bi
            if idx == 0:
                continue
            overrides.append({'predicate': {'hungrycows:variant': round(idx * 0.01, 2)},
                              'model': f'hungrycows:block/hay/{st}_{c}_{"bound" if bound else "loose"}'})
with open(R + '/models/item/hay.json', 'w') as f:
    json.dump({'parent': 'hungrycows:block/hay/fresh_clean_loose', 'overrides': overrides}, f, indent=1)
with open(R + '/models/item/hay_bundle.json', 'w') as f:
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'hungrycows:item/hay_bundle'}}, f)
print('станів', len(variants), 'моделей', len(os.listdir(R + '/models/block/hay')))

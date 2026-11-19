"""Моделі Fij з Blockbench (Bedrock .geo.json, «box UV») → звичайні моделі блоків Майнкрафта (JSON з elements).
Бруски без поворотів переносяться точно. Запуск: python3 tools/geo_to_block_model.py
Оригінали — tools/models_src/*.geo.json. Результат — assets/hungrycows/models/block/<назва>.json."""
import json, os

SRC = 'tools/models_src'
PAN_FIRE_DY = 7   # сковорода на багатті (Fij): стоїть прямо на полінах (поліна багаття заввишки 7 пікс.), у тій самій клітинці
OUT = 'src/main/resources/assets/hungrycows/models/block'

def faces(u, v, w, h, d, s):
    # «box UV»: верхній ряд — верх (d..d+w) і низ (d+w..d+2w); нижній ряд — бік, перед, бік, зад
    r = lambda a, b, c, e: [round(a * s, 4), round(b * s, 4), round(c * s, 4), round(e * s, 4)]
    return {
        'up': {'uv': r(u + d, v, u + d + w, v + d)},
        'down': {'uv': r(u + d + w, v + d, u + d + 2 * w, v)},
        'east': {'uv': r(u, v + d, u + d, v + d + h)},
        'north': {'uv': r(u + d, v + d, u + d + w, v + d + h)},
        'west': {'uv': r(u + d + w, v + d, u + 2 * d + w, v + d + h)},
        'south': {'uv': r(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)},
    }

def convert(geo_file, texture, bones=None, name=None, dy=0):
    g = json.load(open(os.path.join(SRC, geo_file)))['minecraft:geometry'][0]
    s = 16.0 / g['description']['texture_width']
    elements = []
    for b in g['bones']:
        if bones is not None and b['name'] not in bones: continue
        for c in b.get('cubes', []):
            (ox, oy, oz), (w, h, d) = c['origin'], c['size']
            u, v = c['uv']
            # Bedrock: вісь X дзеркальна до Java
            frm = [8 - (ox + w), oy + dy, oz + 8]
            to = [8 - ox, oy + h + dy, oz + d + 8]
            f = faces(u, v, w, h, d, s)
            for k in f: f[k]['texture'] = '#tex'
            elements.append({'from': frm, 'to': to, 'faces': f})
    model = {'textures': {'tex': texture, 'particle': texture}, 'elements': elements}
    with open(os.path.join(OUT, name + '.json'), 'w') as o: json.dump(model, o, indent=1)
    print(name, len(elements), 'брусків')

# предмет у руці й інвентарі — як звичайний блок (Fij: «велечезні»): модель блока + display ванільного block.json
DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
    'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]}}
ITEM_OUT = 'src/main/resources/assets/hungrycows/models/item'


def item_from_block(name, dy=0):
    """Модель предмета з моделі блока, зсунута на dy пікселів (щоб предмет був посередині)"""
    d = json.load(open(os.path.join(OUT, f'{name}.json')))
    for e in d.get('elements', []):
        e['from'][1] += dy
        e['to'][1] += dy
    d['display'] = DISPLAY
    json.dump(d, open(os.path.join(ITEM_OUT, f'{name}.json'), 'w'), indent=1)

if __name__ == '__main__':
    convert('tub.geo.json', 'hungrycows:block/salt/tub', ['bone'], 'tub')
    convert('tub.geo.json', 'hungrycows:block/salt/tub', ['kry'], 'tub_lid')
    # рідина в діжці: 10 рівнів (1 відро — 1,2 бл. із 12 по висоті стінок), фарбується в коді (вода / розчин)
    for lvl in range(1, 11):
        top = 1 + 12 * lvl / 10.0
        el = {'from': [1, 1, 1], 'to': [15, round(top, 3), 15], 'faces': {
            'up': {'uv': [1, 1, 15, 15], 'texture': '#liquid', 'tintindex': 0}}}
        json.dump({'textures': {'liquid': 'minecraft:block/water_still', 'particle': 'minecraft:block/water_still'},
                   'elements': [el]}, open(os.path.join(OUT, f'tub_liquid_{lvl}.json'), 'w'), indent=1)
    print('рідина: 10 рівнів')
    # сковорода: на землі (dy 0) і прямо на полінах багаття (dy 7) — в одній клітинці з багаттям (Fij)
    convert('salt_pan.geo.json', 'hungrycows:block/salt/salt_pan', None, 'salt_pan', dy=0)
    convert('salt_pan.geo.json', 'hungrycows:block/salt/salt_pan', None, 'salt_pan_fire', dy=PAN_FIRE_DY)
    # порожні сковороди стосом (Fij): 2-га на 5 пікс. вище, 3-тя на 10
    convert('salt_pan.geo.json', 'hungrycows:block/salt/salt_pan', None, 'salt_pan_stack_2', dy=5)
    convert('salt_pan.geo.json', 'hungrycows:block/salt/salt_pan', None, 'salt_pan_stack_3', dy=10)
    # поліна під сковородою: згасле — ванільна модель; горить — ванільні поліна + низенький вогонь лише під дном
    camp = json.load(open(os.path.join(os.path.dirname(__file__), 'models_src', 'template_campfire.json')))
    els = []
    for e in camp['elements']:
        e = json.loads(json.dumps(e))
        if '#fire' in json.dumps(e):
            e['to'][1] = PAN_FIRE_DY          # полум'я 1–7 пікс. (до дна сковороди), нижня частина текстури
            for f in e['faces'].values():
                f['uv'] = [0, 16 - (PAN_FIRE_DY - 1), 16, 16]
        els.append(e)
    json.dump({'textures': {'particle': 'minecraft:block/campfire_log', 'log': 'minecraft:block/campfire_log',
                            'lit_log': 'minecraft:block/campfire_log_lit', 'fire': 'minecraft:block/campfire_fire'},
               'render_type': 'minecraft:cutout', 'elements': els},
              open(os.path.join(OUT, 'salt_pan_campfire_lit.json'), 'w'), indent=1)
    # сіль у сковороді — шар, що піднімається, як вода в діжці (Fij): 10 рівнів (по 2 штуки), сира чи прожарена
    for where, base in (('', 1), ('fire_', PAN_FIRE_DY + 1)):
        for kind in ('raw', 'roasted'):
            tex = f'hungrycows:block/salt/salt_pan_layer_{kind}'
            for lvl in range(1, 11):
                top = base + 3.6 * lvl / 10.0
                el = {'from': [1, base, 1], 'to': [15, round(top, 3), 15], 'faces': {
                    'up': {'uv': [1, 1, 15, 15], 'texture': '#salt'}}}
                json.dump({'textures': {'salt': tex, 'particle': tex}, 'elements': [el]},
                          open(os.path.join(OUT, f'salt_pan_{where}{kind}_{lvl}.json'), 'w'), indent=1)
    print('шар солі: 40 моделей')
    # поставлена кришка (Fij: «щоб можна було ставити») — та сама, опущена на землю
    lid = json.load(open(os.path.join(OUT, 'tub_lid.json')))
    for e in lid['elements']:
        e['from'][1] -= 13
        e['to'][1] -= 13
    json.dump(lid, open(os.path.join(OUT, 'tub_lid_placed.json'), 'w'), indent=1)
    convert('salt_basket.geo.json', 'hungrycows:block/salt/salt_basket', None, 'salt_basket')
    item_from_block('tub')
    item_from_block('tub_lid', dy=-6)          # кришка нагорі діжки — опущена до середини
    item_from_block('salt_pan', dy=2)
    item_from_block('salt_basket', dy=-1)
    print('предмети: 4 моделі')


# ---------- кружка Fij (16.11): розгортка «по гранях» (uv + uv_size для кожної грані), три кістки ----------
MUG_SHIFT = (0, 5.5, -1)    # підняти й посунути, щоб маленька кружка (4×5 пікс.) була посередині предмета
MUG_DISPLAY = {
    'gui': {'rotation': [30, 45, 0], 'translation': [0, 0, 0], 'scale': [2.2, 2.2, 2.2]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [1, 1, 1]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [2, 2, 2]},
    'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.5, 1.5, 1.5]},
    'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 1, 1], 'scale': [1, 1, 1]},
    'thirdperson_lefthand': {'rotation': [0, 90, 0], 'translation': [0, 1, 1], 'scale': [1, 1, 1]},
    'firstperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 3, 0], 'scale': [0.75, 0.75, 0.75]},
    'firstperson_lefthand': {'rotation': [0, 90, 0], 'translation': [0, 3, 0], 'scale': [0.75, 0.75, 0.75]}}

# Fij (19.11): «чашка дивно тримається — тримати за ручку»; потім (скріншоти Fij) «дуже погано виглядає» — завелика, ручки не видно.
# Третя особа: стоїть рівно (X 90 — у руці «вгору» це вісь z), ручкою до тіла (Y −90), зсунута назовні на 1,7 пікс. — ручка в кулаці,
# чашка збоку від руки; 0,75 розміру. Перша особа: 0,5 розміру, праворуч унизу, ручка праворуч. Деталі (заготовка, вушка, ручка) — як були.
MUG_HELD = dict(MUG_DISPLAY)
MUG_HELD.update({
    'thirdperson_righthand': {'rotation': [90, -90, 0], 'translation': [1.7, 0, 0], 'scale': [0.75, 0.75, 0.75]},
    'thirdperson_lefthand': {'rotation': [90, -90, 0], 'translation': [1.7, 0, 0], 'scale': [0.75, 0.75, 0.75]},
    'firstperson_righthand': {'rotation': [0, 75, 5], 'translation': [0.5, 1.5, -1], 'scale': [0.5, 0.5, 0.5]},
    'firstperson_lefthand': {'rotation': [0, 75, 5], 'translation': [0.5, 1.5, -1], 'scale': [0.5, 0.5, 0.5]},
})
FLIP = {'east': 'west', 'west': 'east'}   # Bedrock X дзеркальна до Java


def mug_elements(bones, shift=None):
    shift = shift or MUG_SHIFT
    g = json.load(open(os.path.join(SRC, 'mug.geo.json')))['minecraft:geometry'][0]
    s = 16.0 / g['description']['texture_width']
    out = []
    for b in g['bones']:
        if b['name'] not in bones: continue
        for c in b.get('cubes', []):
            (ox, oy, oz), (w, h, d) = c['origin'], c['size']
            frm = [8 - (ox + w) + shift[0], oy + shift[1], oz + 8 + shift[2]]
            to = [8 - ox + shift[0], oy + h + shift[1], oz + d + 8 + shift[2]]
            faces = {}
            for name, f in c['uv'].items():
                (u, v), (uw, uh) = f['uv'], f['uv_size']
                faces[FLIP.get(name, name)] = {'uv': [round(u * s, 4), round(v * s, 4), round((u + uw) * s, 4), round((v + uh) * s, 4)],
                                               'texture': '#tex'}
            out.append({'from': frm, 'to': to, 'faces': faces})
    return out


def mug_model(name, bones, liquid=None, held=False):
    els = mug_elements(bones)
    tex = {'tex': 'hungrycows:item/carve/mug_wood', 'particle': 'hungrycows:item/carve/mug_wood'}
    if liquid:   # рідина всередині, трохи нижче краю; колір — з коду (вода синя, молоко біле)
        tex['liquid'] = 'minecraft:block/white_wool'
        y = 4.5 + MUG_SHIFT[1]
        els.append({'from': [6.5, y, 6.5 + MUG_SHIFT[2]], 'to': [9.5, y, 9.5 + MUG_SHIFT[2]],
                    'faces': {'up': {'uv': [0, 0, 3, 3], 'texture': '#liquid', 'tintindex': 0}}})
    json.dump({'textures': tex, 'elements': els, 'display': MUG_HELD if held else MUG_DISPLAY}, open(os.path.join(ITEM_OUT, name + '.json'), 'w'), indent=1)
    print(name, len(els), 'брусків')


# СКЛЕЮВАННЯ (Fij, 19.11): кружка, що стоїть на землі, частинами (заготовка / вушка / ручка) і свіжа смола там, де частини
# торкаються (тонкий шар трохи ширший за стик — видно, поки сохне). Стоїть на підлозі (y 0), посередині блока.
MUG_BLOCK_SHIFT = (0, 0, -1)
RESIN_LAYER = 0.25       # товщина шару смоли (пікс.)
RESIN_WIDER = 0.15       # смола трохи ширша за стик


def resin_patches(base, part):
    """Стики між брусками base і part: тонкий шар на base з боку part"""
    out = []
    for a in base:
        for b in part:
            for ax in range(3):
                for sign in (1, -1):
                    plane = a['to'][ax] if sign > 0 else a['from'][ax]
                    other = b['from'][ax] if sign > 0 else b['to'][ax]
                    if abs(plane - other) > 1e-6: continue
                    lo, hi = [0, 0, 0], [0, 0, 0]
                    ok = True
                    for k in range(3):
                        if k == ax: continue
                        l, h = max(a['from'][k], b['from'][k]), min(a['to'][k], b['to'][k])
                        if h - l <= 1e-6: ok = False
                        lo[k], hi[k] = l - RESIN_WIDER, h + RESIN_WIDER
                    if not ok: continue
                    lo[ax], hi[ax] = (plane, plane + RESIN_LAYER) if sign > 0 else (plane - RESIN_LAYER, plane)
                    lo[1] = max(lo[1], 0)
                    faces = {f: {'uv': [0, 0, 2, 2], 'texture': '#resin'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
                    out.append({'from': [round(v, 4) for v in lo], 'to': [round(v, 4) for v in hi], 'faces': faces})
    return out


def glue_models():
    body = mug_elements(['kruzka'], MUG_BLOCK_SHIFT)
    ears = mug_elements(['vuha_dla_kruzky'], MUG_BLOCK_SHIFT)
    handle = mug_elements(['rucka'], MUG_BLOCK_SHIFT)
    tex = {'tex': 'hungrycows:item/carve/mug_wood', 'particle': 'hungrycows:item/carve/mug_wood'}
    rtex = {'resin': 'hungrycows:block/glue/resin_smear', 'particle': 'hungrycows:block/glue/resin_smear'}
    for name, els, t in (('body', body, tex), ('ears', ears, tex), ('handle', handle, tex),
                         ('resin_body', resin_patches(body, ears), rtex), ('resin_ears', resin_patches(ears, handle), rtex)):
        json.dump({'textures': t, 'elements': els}, open(os.path.join(OUT, 'mug_assembly_' + name + '.json'), 'w'), indent=1)
        print('mug_assembly_' + name, len(els), 'брусків')
    # стани: stage 0 заготовка · 1 намазана · 2 + вушка · 3 вушка намазані · 4 + ручка (сохне) · 5 готова
    parts = [('body', [0, 1, 2, 3, 4, 5]), ('resin_body', [1, 2, 3, 4]), ('ears', [2, 3, 4, 5]), ('resin_ears', [3, 4]), ('handle', [4, 5])]
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    mp = []
    for face, y in rot.items():
        for part, stages in parts:
            m = {'model': 'hungrycows:block/mug_assembly_' + part}
            if y: m['y'] = y
            mp.append({'when': {'facing': face, 'stage': '|'.join(map(str, stages))}, 'apply': m})
    json.dump({'multipart': mp}, open('src/main/resources/assets/hungrycows/blockstates/mug_assembly.json', 'w'), indent=1)
    # котел зі смолою: ванільні шаблони котла з рідиною (3 висоти), смола розплавлена / застигла
    for lvl, parent in ((1, 'template_cauldron_level1'), (2, 'template_cauldron_level2'), (3, 'template_cauldron_full')):
        for kind in ('molten', 'solid'):
            json.dump({'parent': 'minecraft:block/' + parent, 'textures': {'content': 'hungrycows:block/glue/resin_' + kind}},
                      open(os.path.join(OUT, 'resin_cauldron_%d_%s.json' % (lvl, kind)), 'w'), indent=1)
    variants = {}
    for lvl in (1, 2, 3):
        for kind, molten in (('molten', 'true'), ('solid', 'false')):
            variants['level=%d,molten=%s' % (lvl, molten)] = {'model': 'hungrycows:block/resin_cauldron_%d_%s' % (lvl, kind)}
    json.dump({'variants': variants}, open('src/main/resources/assets/hungrycows/blockstates/resin_cauldron.json', 'w'), indent=1)
    print('котел зі смолою: 6 моделей')


if __name__ == '__main__':
    ALL = ['kruzka', 'vuha_dla_kruzky', 'rucka']
    glue_models()
    mug_model('mug_body', ['kruzka'])
    mug_model('mug_ears', ['vuha_dla_kruzky'])
    mug_model('mug_handle', ['rucka'])
    mug_model('mug_water', ALL, liquid=True, held=True)
    mug_model('mug_milk', ALL, liquid=True, held=True)
    mug_model('mug_empty', ALL, held=True)
    # головна модель кружки: порожня + заміна за рідиною (властивість hungrycows:drink: 0,5 вода, 1 молоко)
    m = json.load(open(os.path.join(ITEM_OUT, 'mug_empty.json')))
    m['overrides'] = [{'predicate': {'hungrycows:drink': 0.5}, 'model': 'hungrycows:item/mug_water'},
                      {'predicate': {'hungrycows:drink': 1.0}, 'model': 'hungrycows:item/mug_milk'}]
    json.dump(m, open(os.path.join(ITEM_OUT, 'mug.json'), 'w'), indent=1)
    print('mug + заміни за рідиною')

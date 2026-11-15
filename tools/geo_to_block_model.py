"""Моделі Fij з Blockbench (Bedrock .geo.json, «box UV») → звичайні моделі блоків Майнкрафта (JSON з elements).
Бруски без поворотів переносяться точно. Запуск: python3 tools/geo_to_block_model.py
Оригінали — tools/models_src/*.geo.json. Результат — assets/hungrycows/models/block/<назва>.json."""
import json, os

SRC = 'tools/models_src'
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

def convert(geo_file, texture, bones=None, name=None):
    g = json.load(open(os.path.join(SRC, geo_file)))['minecraft:geometry'][0]
    s = 16.0 / g['description']['texture_width']
    elements = []
    for b in g['bones']:
        if bones is not None and b['name'] not in bones: continue
        for c in b.get('cubes', []):
            (ox, oy, oz), (w, h, d) = c['origin'], c['size']
            u, v = c['uv']
            # Bedrock: вісь X дзеркальна до Java
            frm = [8 - (ox + w), oy, oz + 8]
            to = [8 - ox, oy + h, oz + d + 8]
            f = faces(u, v, w, h, d, s)
            for k in f: f[k]['texture'] = '#tex'
            elements.append({'from': frm, 'to': to, 'faces': f})
    model = {'textures': {'tex': texture, 'particle': texture}, 'elements': elements}
    with open(os.path.join(OUT, name + '.json'), 'w') as o: json.dump(model, o, indent=1)
    print(name, len(elements), 'брусків')

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

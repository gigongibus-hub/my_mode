"""Текстури склеювання (Fij, 19.11), поки Fij не намалює свої: свіжа смола на стиках, розплавлена смола в котлі, застигла.
Кольори — з текстури смоли Fij (`textures/item/glue/resin.png`)."""
import os, random
from PIL import Image

OUT = 'src/main/resources/assets/hungrycows/textures/block/glue'
os.makedirs(OUT, exist_ok=True)
random.seed(7)


def tex(name, base, spread, shine, shine_n):
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = random.randint(-spread, spread)
            px[x, y] = tuple(max(0, min(255, c + d)) for c in base) + (255,)
    for _ in range(shine_n):        # відблиски
        x, y = random.randrange(16), random.randrange(16)
        px[x, y] = shine + (255,)
    im.save(os.path.join(OUT, name + '.png'))


import json, math


def blend(a, b, t):
    return tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3))


SOLID, MOLTEN = (150, 82, 20), (230, 122, 22)


def melting(name, part, frames=1):
    """Плавлення (Fij: «хоча б три стадії»): застигла грудка, по якій розходяться розплавлені плями. part — частка розплавленого
    (0…1); плями ростуть від кількох «гарячих» точок. frames > 1 — анімація (повільне переливання поверхні)."""
    rnd = random.Random(11)
    hot = [(rnd.uniform(0, 16), rnd.uniform(0, 16)) for _ in range(5)]
    noise = [[rnd.uniform(-1, 1) for _ in range(16)] for _ in range(16)]
    im = Image.new('RGBA', (16, 16 * frames))
    px = im.load()
    for f in range(frames):
        ph = 2 * math.pi * f / frames
        for y in range(16):
            for x in range(16):
                d = min(math.hypot(x + 0.5 - hx, y + 0.5 - hy) for hx, hy in hot) / 11.3   # 0 біля гарячої точки … ~1
                m = 1.0 if part >= 1 else max(0.0, min(1.0, (part * 1.25 - d + noise[y][x] * 0.12) * 4))
                c = blend(SOLID, MOLTEN, m)
                if m > 0.5:      # розплавлене: переливається й блищить
                    wave = 0.5 + 0.5 * math.sin(x * 0.9 + y * 0.6 + ph) * math.sin(y * 0.7 - x * 0.3 + ph * 2)
                    c = blend(c, (255, 190, 80), wave * 0.35)
                c = tuple(max(0, min(255, v + int(noise[y][x] * 10))) for v in c)
                px[x, y + 16 * f] = c + (255,)
    im.save(os.path.join(OUT, name + '.png'))
    if frames > 1:
        json.dump({'animation': {'frametime': 8, 'interpolate': True}}, open(os.path.join(OUT, name + '.png.mcmeta'), 'w'))


tex('resin_smear', (214, 128, 30), 14, (255, 214, 120), 10)   # свіжа, блискуча
tex('resin_solid', (150, 82, 20), 10, (190, 120, 50), 6)      # застигла (Fij намалює свою)
melting('resin_melt_1', 0.25)                                  # плавиться: перші плями
melting('resin_melt_2', 0.5)                                   # майже розплавилась: лишились грудки
melting('resin_molten', 1.0, frames=8)                         # розплавлена, переливається (анімація)
print('текстури смоли: мазок, застигла, 2 стадії плавлення, розплавлена (8 кадрів)')

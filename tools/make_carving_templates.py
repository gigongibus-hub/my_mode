"""Шаблони ремесла (124×124) у форматі Fij: ЧОРНЕ — дерево, що має лишитись; ЗЕЛЕНЕ — що треба вибрати.
Гра читає лише «чорне» (темні пікселі); Fij може замінити будь-який файл своїм малюнком того ж розміру.
Запуск: python3 tools/make_carving_templates.py → data/hungrycows/carving_templates/*.png
Також малює текстуру креслення (папір з написами) → textures/item/carve/blueprint.png"""
import os, random
from PIL import Image, ImageDraw

OUT = 'src/main/resources/data/hungrycows/carving_templates'
TEX = 'src/main/resources/assets/hungrycows/textures/item/carve'
S = 124
BLACK, GREEN = (0, 0, 0, 255), (40, 200, 60, 255)

def template(name, rects):
    im = Image.new('RGBA', (S, S), GREEN)
    d = ImageDraw.Draw(im)
    for (x0, y0, x1, y1) in rects:
        d.rectangle([x0, y0, x1, y1], fill=BLACK)
    im.save(os.path.join(OUT, name + '.png'))
    print(name, rects)

os.makedirs(OUT, exist_ok=True)
# заготовка кружки (збоку): стінки й дно, порожнина відкрита згори (модель Fij 4×5 → 80×104)
template('mug_body', [(22, 14, 33, 117), (90, 14, 101, 117), (22, 106, 101, 117)])
# вушка для ручки: дві планки (модель 2×0,5 → 72×18)
template('mug_ears', [(26, 30, 97, 47), (26, 76, 97, 93)])
# ручка: брусок (модель 1×3,5 → 28×104)
template('mug_handle', [(48, 10, 75, 113)])

# креслення: папір з рядками «написів» (16×16)
rnd = random.Random(7)
im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
px = im.load()
for y in range(1, 15):
    for x in range(2, 14):
        base = 228 - (x + y) % 3 * 6
        px[x, y] = (base, base - 10, base - 34, 255)
for x in range(2, 14):            # край аркуша трохи темніший
    px[x, 1] = (196, 184, 150, 255); px[x, 14] = (190, 176, 140, 255)
for y in range(1, 15):
    px[2, y] = (200, 188, 152, 255); px[13, y] = (186, 172, 136, 255)
for y in (4, 6, 8, 10, 12):       # «написи»
    for x in range(4, 4 + rnd.randint(5, 8)):
        if rnd.random() < 0.8: px[x, y] = (70, 56, 40, 255)
im.save(os.path.join(TEX, 'blueprint.png'))
print('blueprint.png')

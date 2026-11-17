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
# Fij (16.11): «ідеальну практично неможливо» — під деталлю лишалось дерево до самого столу, а туди зубило не дістає
# (збоку — стіл, згори — сама деталь). Тепер кожна деталь СТОЇТЬ НА СТОЛІ (до низу, y 123): усе зайве — згори або збоку.
# заготовка кружки (збоку): стінки й дно, порожнина відкрита згори
template('mug_body', [(18, 12, 33, 123), (90, 12, 105, 123), (18, 104, 105, 123)])
# вушка для ручки: два виступи поруч
template('mug_ears', [(22, 80, 49, 123), (74, 80, 101, 123)])
# ручка: брусок стовпчиком
template('mug_handle', [(46, 8, 77, 123)])

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

# ---- ОБ'ЄМНІ шаблони (справжня різьба, Fij 17.11): 12×12×12, файл 12 × 144 — 12 шарів знизу вгору,
# рядок = шар*12 + z, стовпчик = x (уздовж волокон). Чорне — лишається. Кожна деталь стоїть на кресленні (шар 0).
N = 12
OUT3 = 'src/main/resources/data/hungrycows/carving_templates_3d'
os.makedirs(OUT3, exist_ok=True)

def template3d(name, keep):
    im = Image.new('RGBA', (N, N * N), GREEN)
    px = im.load()
    cnt = 0
    for y in range(N):
        for z in range(N):
            for x in range(N):
                if keep(x, y, z):
                    px[x, y * N + z] = BLACK
                    cnt += 1
    im.save(os.path.join(OUT3, name + '.png'))
    print('3d', name, cnt)

# заготовка кружки: коробка 10×12×10, стінки 2, дно 2, відкрита згори
template3d('mug_body', lambda x, y, z: 1 <= x <= 10 and 1 <= z <= 10 and not (3 <= x <= 8 and 3 <= z <= 8 and y >= 2))
# вушка: два виступи 3×5×6 по краях
template3d('mug_ears', lambda x, y, z: y <= 4 and 3 <= z <= 8 and (1 <= x <= 3 or 8 <= x <= 10))
# ручка: брусок 4×12×4 стовпчиком
template3d('mug_handle', lambda x, y, z: 4 <= x <= 7 and 4 <= z <= 7)

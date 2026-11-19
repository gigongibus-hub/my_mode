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


tex('resin_smear', (214, 128, 30), 14, (255, 214, 120), 10)   # свіжа, блискуча
tex('resin_molten', (226, 120, 22), 18, (255, 200, 90), 18)   # розплавлена в котлі
tex('resin_solid', (150, 82, 20), 10, (190, 120, 50), 6)      # застигла (Fij намалює свою)
print('3 текстури смоли')

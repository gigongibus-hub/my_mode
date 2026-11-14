"""Генератор текстур корів Hungry Cows: 6 мастей × 4 візерунки = 24 текстури 64×32 (розкладка ванільної моделі корови).
Запуск: python3 tools/make_cow_textures.py  → src/main/resources/assets/hungrycows/textures/entity/cow/c{масть}_p{візерунок}.png
Масті (за темністю, щоб теля від білої й чорної виходило «посередині»): 0 біла, 1 палева, 2 руда, 3 бура, 4 сіра, 5 чорна.
Візерунки: 0 суцільна, 1 білоголова, 2 з поясом, 3 ряба (плями)."""
import random
from PIL import Image

OUT = "src/main/resources/assets/hungrycows/textures/entity/cow"
COLORS = [(232, 226, 214), (196, 156, 102), (152, 66, 36), (102, 66, 40), (128, 126, 122), (38, 35, 34)]
WHITE = (238, 234, 226)
DARK_MARK = (40, 37, 35)       # плями й пояс на білій корові
MUZZLE = (214, 160, 150)       # рожевий ніс
MUZZLE_DARK = (70, 60, 58)     # темний ніс у темних мастей
HORN = (226, 218, 196)
HOOF = (58, 52, 48)
UDDER = (226, 162, 156)
EYE = (20, 20, 22)

# прямокутники розгортки (x0, y0, x1, y1), як у ванільній моделі корови 64×32
HEAD = [(6, 0, 14, 6), (14, 0, 22, 6), (0, 6, 28, 14)]
HEAD_FRONT = (6, 6, 14, 14)
HEAD_TOP = (6, 0, 14, 6)
HORNS = [(22, 0, 26, 4)]
BODY = [(28, 4, 52, 14), (18, 14, 62, 32)]
UDDER_R = [(52, 0, 62, 7)]
LEGS = [(0, 16, 16, 32)]


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def fill(img, rects, color, rnd, noise=7):
    for x0, y0, x1, y1 in rects:
        for x in range(x0, x1):
            for y in range(y0, y1):
                img.putpixel((x, y), shade(color, rnd.randint(-noise, noise)))


def inside(rects, x, y):
    return any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in rects)


def blobs(img, rects, color, rnd, count, rmin, rmax):
    for _ in range(count):
        x0, y0, x1, y1 = rnd.choice(rects)
        cx, cy = rnd.randint(x0, x1 - 1), rnd.randint(y0, y1 - 1)
        r = rnd.uniform(rmin, rmax)
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            for y in range(int(cy - r) - 1, int(cy + r) + 2):
                if inside(rects, x, y) and (x - cx) ** 2 + ((y - cy) * 1.2) ** 2 <= r * r + rnd.uniform(-1.5, 1.5):
                    img.putpixel((x, y), shade(color, rnd.randint(-5, 5)))


def make(ci, pi):
    rnd = random.Random(ci * 31 + pi * 7 + 1)
    base = COLORS[ci]
    mark = DARK_MARK if ci == 0 else WHITE          # чим малювати візерунок
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    fill(img, HEAD + BODY + LEGS, base, rnd)
    fill(img, HORNS, HORN, rnd, 4)
    fill(img, UDDER_R, UDDER, rnd, 4)
    if pi == 1:   # білоголова (на білій — темноголова)
        fill(img, [HEAD_FRONT, HEAD_TOP], mark, rnd, 5)
    elif pi == 2:   # з поясом посередині тулуба
        fill(img, [(18, 20, 62, 26)], mark, rnd, 5)
    elif pi == 3:   # ряба: плями на тулубі, голові й ногах
        blobs(img, BODY, mark, rnd, 9, 2.0, 4.2)
        blobs(img, HEAD, mark, rnd, 3, 1.2, 2.4)
        blobs(img, LEGS, mark, rnd, 3, 1.0, 2.0)
    # морда, очі, копита
    x0, y0, x1, y1 = HEAD_FRONT
    light_face = ci <= 1 or pi == 1
    fill(img, [(x0 + 1, y1 - 3, x1 - 1, y1)], MUZZLE if light_face else MUZZLE_DARK, rnd, 4)
    for ex in (x0 + 1, x1 - 2):
        img.putpixel((ex, y0 + 3), EYE)
        img.putpixel((ex, y0 + 2), shade(WHITE, -20))
    fill(img, [(0, 30, 16, 32)], HOOF, rnd, 3)
    img.save(f"{OUT}/c{ci}_p{pi}.png")


for c in range(len(COLORS)):
    for p in range(4):
        make(c, p)
print("готово:", len(COLORS) * 4, "текстур у", OUT)

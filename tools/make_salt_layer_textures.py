"""Текстури шару солі в сковороді (16×16): зернистий шар з кольорів предметів Fij (недоочищена / прожарена)."""
import os, random
from PIL import Image
A = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/hungrycows/textures')
for src, dst in (('ground_salt_half_purified', 'salt_pan_layer_raw'), ('ground_salt_roasted', 'salt_pan_layer_roasted')):
    im = Image.open(f'{A}/item/salt/{src}.png').convert('RGBA')
    cols = [p for p in im.getdata() if p[3] > 200]
    rnd = random.Random(dst)
    out = Image.new('RGBA', (16, 16))
    out.putdata([rnd.choice(cols)[:3] + (255,) for _ in range(256)])
    out.save(f'{A}/block/salt/{dst}.png')
    print(dst, len(cols), 'кольорів')

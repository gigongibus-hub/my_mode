"""Тестові шаблони (structures/*.nbt): місця, яких немає в списку блоків, гра при розстановці НЕ чіпає —
там лишались блоки від попередніх тестів (корова ставала на них і перестрибувала скляну стіну). Скрипт
дописує в кожен шаблон повітря на всі такі місця. Запуск: python3 tools/fill_structure_air.py"""
import gzip, struct, io, glob, sys

def parse(f):
    def rd(n): return f.read(n)
    def tag(t):
        if t == 1: return ('b', struct.unpack('>b', rd(1))[0])
        if t == 2: return ('h', struct.unpack('>h', rd(2))[0])
        if t == 3: return ('i', struct.unpack('>i', rd(4))[0])
        if t == 4: return ('q', struct.unpack('>q', rd(8))[0])
        if t == 5: return ('f', struct.unpack('>f', rd(4))[0])
        if t == 6: return ('d', struct.unpack('>d', rd(8))[0])
        if t == 7: n = struct.unpack('>i', rd(4))[0]; return ('ba', rd(n))
        if t == 8: n = struct.unpack('>H', rd(2))[0]; return ('s', rd(n).decode())
        if t == 9:
            et = rd(1)[0]; n = struct.unpack('>i', rd(4))[0]
            return ('l', et, [tag(et) for _ in range(n)])
        if t == 10:
            d = {}
            while True:
                tt = rd(1)[0]
                if tt == 0: return ('c', d)
                nl = struct.unpack('>H', rd(2))[0]; name = rd(nl).decode(); d[name] = (tt, tag(tt))
        if t == 11: n = struct.unpack('>i', rd(4))[0]; return ('ia', [struct.unpack('>i', rd(4))[0] for _ in range(n)])
        if t == 12: n = struct.unpack('>i', rd(4))[0]; return ('la', [struct.unpack('>q', rd(8))[0] for _ in range(n)])
        raise ValueError(t)
    t = rd(1)[0]; nl = struct.unpack('>H', rd(2))[0]; name = rd(nl).decode()
    return name, (t, tag(t))

def write(out, t, v):
    k = v[0]
    if k == 'b': out.write(struct.pack('>b', v[1]))
    elif k == 'h': out.write(struct.pack('>h', v[1]))
    elif k == 'i': out.write(struct.pack('>i', v[1]))
    elif k == 'q': out.write(struct.pack('>q', v[1]))
    elif k == 'f': out.write(struct.pack('>f', v[1]))
    elif k == 'd': out.write(struct.pack('>d', v[1]))
    elif k == 'ba': out.write(struct.pack('>i', len(v[1]))); out.write(v[1])
    elif k == 's': b = v[1].encode(); out.write(struct.pack('>H', len(b))); out.write(b)
    elif k == 'l':
        out.write(bytes([v[1]])); out.write(struct.pack('>i', len(v[2])))
        for e in v[2]: write(out, v[1], e)
    elif k == 'c':
        for name, (tt, tv) in v[1].items():
            out.write(bytes([tt])); b = name.encode(); out.write(struct.pack('>H', len(b))); out.write(b); write(out, tt, tv)
        out.write(b'\x00')
    elif k == 'ia': out.write(struct.pack('>i', len(v[1]))); [out.write(struct.pack('>i', x)) for x in v[1]]
    elif k == 'la': out.write(struct.pack('>i', len(v[1]))); [out.write(struct.pack('>q', x)) for x in v[1]]

for path in sorted(glob.glob('src/main/resources/data/hungrycows/structures/*.nbt')):
    name, (t, root) = parse(io.BytesIO(gzip.open(path).read()))
    d = root[1]
    size = [e[1] for e in d['size'][1][2]]
    palette = d['palette'][1][2]
    names = [p[1]['Name'][1][1] for p in palette]
    if 'minecraft:air' in names:
        air = names.index('minecraft:air')
    else:
        palette.append(('c', {'Name': (8, ('s', 'minecraft:air'))}))
        air = len(palette) - 1
    blocks = d['blocks'][1][2]
    have = set(tuple(e[1] for e in b[1]['pos'][1][2]) for b in blocks)
    added = 0
    for x in range(size[0]):
        for y in range(size[1]):
            for z in range(size[2]):
                if (x, y, z) in have: continue
                blocks.append(('c', {'pos': (9, ('l', 3, [('i', x), ('i', y), ('i', z)])), 'state': (3, ('i', air))}))
                added += 1
    out = io.BytesIO()
    out.write(bytes([t])); b = name.encode(); out.write(struct.pack('>H', len(b))); out.write(b); write(out, t, root)
    with gzip.open(path, 'wb') as g: g.write(out.getvalue())
    print(path, size, 'додано повітря:', added)

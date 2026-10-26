"""Build review sheets from actual GL captures; never modify the game artwork."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parent
captures = root / 'final-captures'
files = list(captures.glob('*.png'))
assert len(files) == 80, f'Expected 80 rendered screens, found {len(files)}'
for prefix in ['entry', 'exit', 'link']:
    selected = sorted(p for p in files if p.stem.startswith(prefix + '-'))
    assert len(selected) == 23
    sheet = Image.new('RGB', (1600, 246 * 6), '#10141e')
    draw = ImageDraw.Draw(sheet)
    for i, path in enumerate(selected):
        im = Image.open(path)
        im.thumbnail((400, 225))
        x, y = i % 4 * 400, i // 4 * 246
        sheet.paste(im, (x, y + 21))
        draw.text((x + 6, y + 4), path.stem, fill='white')
    sheet.save(root / f'review-{prefix}.jpg', quality=90)

for name in ['link-R1-R2', 'entry-R1-R2', 'exit-R1-R2', 'link-L3-L4',
             'entry-L3-L4', 'link-T3-T5', 'entry-T3-T5', 'link-T3-T4', 'link-CAMP-V']:
    Image.open(captures / f'{name}.png').convert('RGB').save(root / f'{name}.jpg', quality=92)

art = sorted((root.parents[1] / 'assets/scenes/links-v2').glob('*.png'))
sheet = Image.new('RGB', (1200, 1000), '#10141e')
draw = ImageDraw.Draw(sheet)
for i, path in enumerate(art):
    im = Image.open(path)
    im.thumbnail((390, 300))
    x, y = i % 3 * 400, i // 3 * 333
    sheet.paste(im, (x, y + 24))
    draw.text((x + 8, y + 5), path.stem, fill='white')
sheet.save(root / 'contact.jpg', quality=90)

prior = root.parent / 'map-continuity/after/link-R1-R2.png'
if prior.exists():
    sheet = Image.new('RGB', (1280, 400), '#10141e')
    draw = ImageDraw.Draw(sheet)
    for i, (path, label) in enumerate([(prior, 'BEFORE - rectangular routing'),
            (captures / 'link-R1-R2.png', 'AFTER - an intact painted location')]):
        im = Image.open(path)
        im.thumbnail((640, 360))
        sheet.paste(im, (i * 640, 40))
        draw.text((i * 640 + 15, 14), label, fill='white')
    sheet.save(root / 'comparison.jpg', quality=92)
print('Updated 3 review sheets, 9 detailed frames, source art sheet and comparison.')

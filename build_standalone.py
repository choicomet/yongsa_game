"""Build one self-contained reviewer: no server, sibling files, or network requests.

Source PNGs stay untouched. Embedded WebP previews keep original dimensions and
alpha; feedback continues to identify the original PNG hashes and coordinates.
"""
import base64
import io
import json
import re
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / '이동-가림-검토.html'

def read_manifest(name):
    return json.loads((ROOT / name).read_text(encoding='utf8').split('=', 1)[1].strip().rstrip(';'))

def build():
    manifest = read_manifest('manifest.js')
    layers = read_manifest('layers-manifest.js')
    paths = {item['image'] for item in manifest['items']}
    paths.add(layers['hero']['sheet'])
    paths.update(row['raster']['image'] for row in layers['items'].values() if row.get('raster'))
    assets = {}
    for name in sorted(paths):
        with Image.open(ROOT / 'assets' / name) as image:
            encoded = io.BytesIO()
            image.save(encoded, 'WEBP', quality=92, method=4, exact=True)
            assets['assets/' + name] = 'data:image/webp;base64,' + base64.b64encode(encoded.getvalue()).decode('ascii')
    html = (ROOT / 'review.template.html').read_text(encoding='utf8')
    def inline(match):
        source = (ROOT / match[1]).read_text(encoding='utf8')
        return '<script>\n' + source.replace('</script', '<\\/script') + '\n</script>'
    html = re.sub(r'<script src="([^"]+)"></script>', inline, html)
    # Resolve every image reference before assigning a DOM src or starting a load.
    html = html.replace("image.src=src;", "image.src=reviewAsset(src);")
    html = html.replace("img.src='assets/'+item.image;", "img.src=reviewAsset('assets/'+item.image);")
    html = html.replace("$('original').href='assets/'+item.image;", "$('original').href=reviewAsset('assets/'+item.image);")
    embed = '<script>window.REVIEW_EMBEDDED_ASSETS=' + json.dumps(assets, separators=(',', ':')) + ';\nfunction reviewAsset(path){return window.REVIEW_EMBEDDED_ASSETS[path]||path;}\n</script>\n'
    # All code is inline; image/mask data URLs are the only permitted resources.
    html = html.replace('<script>', embed + '<script>', 1)
    # A modal keeps enlarged data images usable without blocked data-URL navigation.
    html = html.replace('</html>', '''<script>
const originalDialog=document.createElement('dialog');
originalDialog.style.cssText='max-width:96vw;max-height:94vh;background:#101416;color:white;border:1px solid #65737b;padding:16px';
const closeOriginal=document.createElement('button');closeOriginal.textContent='닫기';closeOriginal.onclick=()=>originalDialog.close();
const originalImage=document.createElement('img');originalImage.style.cssText='display:block;max-width:none;margin-top:12px';originalImage.alt='확대된 검토 그림';
originalDialog.append(closeOriginal,originalImage);document.body.append(originalDialog);
$('original').textContent='그림 크게 보기';$('original').onclick=e=>{e.preventDefault();if(!current)return;originalImage.src=reviewAsset('assets/'+current.image);originalDialog.showModal();};
</script></html>''')
    OUTPUT.write_text(html, encoding='utf8')
    (ROOT / 'index.html').write_text(html, encoding='utf8')
    print(f'{OUTPUT.name}: {len(assets)} embedded images, {OUTPUT.stat().st_size / 1024**2:.1f} MiB')

if __name__ == '__main__':
    build()

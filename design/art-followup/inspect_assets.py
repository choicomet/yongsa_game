from pathlib import Path
import json, hashlib
from PIL import Image
root = Path(__file__).resolve().parents[2]
folder = root / "design/art-followup"
sources = json.loads((folder / "generated-assets.json").read_text(encoding="utf-8"))
for item in sources:
    item["source"] = Path(item["source"]).name
(folder / "generated-assets.json").write_text(json.dumps(sources, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
names = ["mine-antechamber", "citadel-corner", "tower-antechamber", "citadel-ore-north"]
names += [x+"-front" for x in ["mine-boss-a","citadel-boss-a","tower-boss-a","seal-hall","mine","citadel","tower","mine-mob-b","mine-mob-c","mine-ore-a","mine-ore-b"]]
paths = [root / "assets/scenes/natural-v1" / (x+".png") for x in names]
paths += [folder / "archive" / (x+".png") for x in ["mine-junction","central-camp-open"]]
inventory=[]
for path in paths:
    with Image.open(path) as img:
        img.load()
        size_matches = img.size == (1672, 941)
        alpha = img.getchannel("A") if "A" in img.getbands() else None
        extrema = alpha.getextrema() if alpha is not None else None
        if path.stem.endswith("-front") or path.stem=="central-camp-open":
            assert extrema and extrema[0]==0 and extrema[1]>0, (path.name, extrema)
        inventory.append(dict(path=path.relative_to(root).as_posix(), width=img.width, height=img.height, sizeMatchesContract=size_matches, mode=img.mode, alphaExtrema=extrema, bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        if not size_matches: print(f"SIZE_MISMATCH {path.name}: {img.size}")
(folder / "asset-inventory.json").write_text(json.dumps(inventory, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print(f"{len(inventory)} PNGs inventoried; {sum(not x['sizeMatchesContract'] for x in inventory)} size mismatches; not gameplay QA")

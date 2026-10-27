"""
팀 저장소 main 의 보스·몬스터·검 그림을 이 프로토타입의 그림 형식으로 바꿔 넣는다 (2026-10-27 김선우 결정).

가져오는 것 (choicomet/yongsa_game main, 같은 팀 작업물)
  assets/team/jiyong/boss_*.png   지용 버전 보스 동작 시트 (256px 칸 8열 x 5행, 왼쪽을 보는 그림, 발은 칸의 (128, 224))
                                  1행 대기4+피격4 · 2행 이동8 · 3행 기본 공격8 · 4행 특수 공격8 · 5행 쓰러짐8
  assets/world/enemies.png        잡몹·보스 한 장짜리 그림 (자리는 assets/data/hd-art.json 의 world_* 항목)
  assets/world/sword_*.png        검 그림

어디에 쓰나 (main 과 같은 자리)
  왼쪽 던전   잡몹 갱도 박쥐     · 보스 갱도를 삼킨 골렘 (boss_golem 시트)
  오른쪽 던전 잡몹 저주받은 해골병 · 보스 혈월의 기사 (boss_knight 시트)
  위쪽 던전   잡몹 탑의 마족병   · 보스 탑의 수문장 (한 장짜리 그림에서 자세를 만든다)
  마왕        1페이즈 boss_lich 시트, 2페이즈(본모습) boss_lich2 시트
  해골 기사   해골병 그림을 크게, 어둡게

이 프로토타입 형식: 프레임을 가로로 이어 붙인 PNG, 오른쪽을 본다, 프레임 아래 끝이 발.
  보스 13프레임: 0·1 대기, 2 특수 동작, 3 위 포효, 4 꼬리, 5 공격 준비, 6 공격, 7 휘청, 8~10 쓰러짐, 11 뒷모습, 12 옆모습
  잡몹 4프레임: 0·1 걷기, 2 공격 준비, 3 공격 (해골 기사는 5번째가 막기)

실행 (저장소 맨 위에서): python3 tools/team_art.py [main 저장소 폴더]
  폴더를 주지 않으면 git show origin/main:... 으로 읽는다. gen_assets.py 를 다시 돌린 뒤에도 이것을 한 번 더 돌린다.
"""
import io
import json
import os
import subprocess
import sys

from PIL import Image, ImageEnhance

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUT = os.path.join(ROOT, "assets", "sprites")
SRC = sys.argv[1] if len(sys.argv) > 1 else None

CELL, FOOT_X, FOOT_Y = 256, 128, 224


def load(path):
    """main 의 assets/ 아래 파일."""
    if SRC:
        return Image.open(os.path.join(SRC, "assets", path)).convert("RGBA")
    data = subprocess.run(["git", "-C", ROOT, "show", "origin/main:assets/" + path],
                          check=True, capture_output=True).stdout
    return Image.open(io.BytesIO(data)).convert("RGBA")


def load_json(path):
    if SRC:
        with open(os.path.join(SRC, "assets", path), encoding="utf-8") as f:
            return json.load(f)
    return json.loads(subprocess.run(["git", "-C", ROOT, "show", "origin/main:assets/" + path],
                                     check=True, capture_output=True).stdout.decode("utf-8"))


def resize(im, w, h):
    """알파 가장자리가 검게 번지지 않게 premultiplied 로 줄인다."""
    return im.convert("RGBa").resize((max(1, w), max(1, h)), Image.LANCZOS).convert("RGBA")


def clean_alpha(im, cut=40):
    """많이 줄이면 생기는 반투명 테두리를 정리해 픽셀 그림처럼 또렷하게."""
    r, g, b, a = im.split()
    a = a.point(lambda v: 0 if v < cut else (255 if v > 200 else v))
    return Image.merge("RGBA", (r, g, b, a))


def strip(frames):
    w, h = frames[0].size
    out = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.alpha_composite(f, (i * w, 0))
    return out


# ───────────────────────── 지용 버전 동작 시트 → 13프레임

# (행, 열) — 행 0 대기/피격, 1 이동, 2 기본 공격, 3 특수 공격, 4 쓰러짐
BOSS_PICK = [
    (0, 0), (0, 2),   # 0·1 대기
    (3, 3),           # 2 특수 동작 (돌진·마법)
    (3, 2),           # 3 위 포효 (힘을 모음)
    (3, 6),           # 4 꼬리 (특수 공격 끝)
    (2, 3),           # 5 공격 준비
    (2, 5),           # 6 공격
    (0, 5),           # 7 휘청 (피격)
    (4, 2), (4, 4), (4, 7),  # 8~10 쓰러짐
    (1, 0),           # 11 뒷모습 대신 이동 자세
    (1, 4),           # 12 옆모습 대신 이동 자세
]


def sheet_cells(sheet):
    """오른쪽을 보도록 뒤집은 칸들 [행][열]."""
    cells = []
    for r in range(5):
        row = []
        for c in range(8):
            cell = sheet.crop((c * CELL, r * CELL, (c + 1) * CELL, (r + 1) * CELL))
            row.append(cell.transpose(Image.FLIP_LEFT_RIGHT))
        cells.append(row)
    return cells


def boss_from_sheet(path, body_height, pick=BOSS_PICK):
    """
    대기 그림의 몸 높이가 body_height 가 되게 줄인다. 모든 프레임이 같은 크기, 발은 프레임 아래 가운데.
    """
    cells = sheet_cells(load(path))
    chosen = [cells[r][c] for r, c in pick]
    # 고른 프레임 전체를 감싸는 상자 (가로는 발을 가운데로 좌우 대칭)
    top, half = CELL, 0
    for im in chosen:
        bb = im.getbbox()
        if not bb:
            continue
        top = min(top, bb[1])
        half = max(half, FOOT_X - bb[0], bb[2] - FOOT_X)
    idle = chosen[0].getbbox()
    k = body_height / (FOOT_Y - idle[1])
    bottom = FOOT_Y + 3 / k             # 프로토타입은 발을 프레임 아래에서 3px 위로 그린다
    box = (int(FOOT_X - half), int(top), int(FOOT_X + half) + 1, int(round(bottom)))
    w = round((box[2] - box[0]) * k)
    w += w % 2
    h = round((box[3] - box[1]) * k)
    frames = [clean_alpha(resize(im.crop(box), w, h)) for im in chosen]
    return frames


# ───────────────────────── 한 장짜리 그림 → 자세 만들기

def pose(base, canvas, angle=0.0, sx=1.0, sy=1.0, dx=0, dy=0, dark=1.0, flip=False):
    """발(아래 가운데)을 축으로 기울이고 늘린 한 프레임."""
    im = base
    if flip:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    if sx != 1.0 or sy != 1.0:
        im = resize(im, round(im.width * sx), round(im.height * sy))
    if angle:
        # 발(아래 가운데)이 회전 중심이 되도록 아래쪽에 같은 높이의 빈칸을 덧대고 돌린 뒤 위 절반만 쓴다
        pad = Image.new("RGBA", (im.width, im.height * 2), (0, 0, 0, 0))
        pad.alpha_composite(im, (0, 0))
        pad = pad.rotate(angle, resample=Image.BICUBIC, expand=True)
        im = pad.crop((0, 0, pad.width, pad.height // 2))
    if dark != 1.0:
        r, g, b, a = im.split()
        im = Image.merge("RGBA", (*ImageEnhance.Brightness(Image.merge("RGB", (r, g, b))).enhance(dark).split(), a))
    cw, ch = canvas
    # 크게 덧댄 판 가운데에 놓고 잘라 프레임 밖으로 나간 부분은 버린다
    x = cw // 2 - im.width // 2 + dx
    y = ch - 3 - im.height - dy
    tmp = Image.new("RGBA", (cw + 2 * im.width, ch + 2 * im.height), (0, 0, 0, 0))
    tmp.alpha_composite(im, (x + im.width, y + im.height))
    return clean_alpha(tmp.crop((im.width, im.height, im.width + cw, im.height + ch)))


def single(sheet, region, body_height):
    """hd-art.json 자리에서 잘라 몸 높이 body_height 로 줄인다 (여백은 잘라낸다)."""
    x, y, w, h = region["x"], region["y"], region["width"], region["height"]
    im = sheet.crop((x, y, x + w, y + h))
    im = im.crop(im.getbbox())
    k = body_height / im.height
    return resize(im, round(im.width * k), body_height)


def mob_frames(base, extra_block=False, dark=1.0):
    cw = int(base.width * 1.35) + 4
    cw += cw % 2
    ch = int(base.height * 1.12) + 4
    c = (cw, ch)
    f = [
        pose(base, c, dark=dark),                                 # 0 걷기
        pose(base, c, sy=0.96, sx=1.03, dark=dark),               # 1 걷기 (눌림)
        pose(base, c, angle=10, dx=-2, dark=dark),                # 2 공격 준비 (뒤로 젖힘)
        pose(base, c, angle=-14, dx=4, dark=dark),                # 3 공격 (앞으로 휘두름)
    ]
    if extra_block:
        f.append(pose(base, c, sx=0.94, sy=0.94, dark=dark * 0.8))  # 4 막기 (웅크림)
    return f


def boss_from_single(base):
    cw = int(base.width * 1.5) + 4
    cw += cw % 2
    ch = int(base.height * 1.2) + 4
    c = (cw, ch)
    return [
        pose(base, c),                                   # 0 대기
        pose(base, c, sy=0.97, sx=1.02),                 # 1 대기 (숨)
        pose(base, c, sx=1.06, sy=1.06),                 # 2 특수 동작
        pose(base, c, sy=1.08, sx=0.97),                 # 3 위 포효
        pose(base, c, angle=-10, dx=4),                  # 4 꼬리
        pose(base, c, angle=9, dx=-3),                   # 5 공격 준비
        pose(base, c, angle=-15, dx=6),                  # 6 공격
        pose(base, c, angle=14, dx=-4, dark=0.85),       # 7 휘청
        pose(base, c, angle=22, sy=0.85, dark=0.8),      # 8 쓰러짐
        pose(base, c, angle=55, sy=0.6, dark=0.65),      # 9
        pose(base, c, angle=80, sy=0.45, dark=0.5),      # 10
        pose(base, c, dark=0.55, flip=True),             # 11 뒷모습
        pose(base, c, sx=0.85, flip=True),               # 12 옆모습
    ]


def sword(path):
    im = load(path)
    im = im.crop(im.getbbox())
    side = max(im.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
    return clean_alpha(resize(sq, 32, 32), 60)


def main():
    with open(os.path.join(OUT, "manifest.json"), encoding="utf-8") as f:
        manifest = json.load(f)

    def save(name, frames):
        strip(frames).save(os.path.join(OUT, name + ".png"))
        manifest[name] = len(frames)
        print(f"{name:16s} {len(frames):2d}프레임  {frames[0].width}x{frames[0].height}")

    # 보스 (지용 버전 동작 시트)
    save("boss_left", boss_from_sheet("team/jiyong/boss_golem.png", 104))
    save("boss_right", boss_from_sheet("team/jiyong/boss_knight.png", 100))
    save("demon_king", boss_from_sheet("team/jiyong/boss_lich.png", 118))
    save("demon_eldritch", boss_from_sheet("team/jiyong/boss_lich2.png", 150))

    # 한 장짜리 그림 (main 이 게임 안에서 쓰는 world_* 자리)
    regions = load_json("data/hd-art.json")["sprites"]
    enemies = load("world/enemies.png")
    save("boss_top", boss_from_single(single(enemies, regions["world_warden"], 116)))
    save("mob_left", mob_frames(single(enemies, regions["world_bat"], 30)))
    save("mob_right", mob_frames(single(enemies, regions["world_skeleton"], 40)))
    save("mob_top", mob_frames(single(enemies, regions["world_imp"], 38)))
    save("mob_knight", mob_frames(single(enemies, regions["world_skeleton"], 50), extra_block=True, dark=0.72))

    # 검 (봉인된 검 = 성검, 깨어난 성검 = 성검 각성)
    for mine, theirs in [("weapon_sealed", "sword_holy"), ("weapon_holy", "sword_holy_awakened"),
                         ("weapon_blackiron", "sword_blackiron"), ("weapon_blackiron_aw", "sword_blackiron_awakened"),
                         ("weapon_bloodmoon", "sword_bloodmoon"), ("weapon_bloodmoon_aw", "sword_bloodmoon_awakened"),
                         ("weapon_sky", "sword_skyfall"), ("weapon_sky_aw", "sword_skyfall_awakened")]:
        save(mine, [sword("world/" + theirs + ".png")])

    with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")


if __name__ == "__main__":
    main()

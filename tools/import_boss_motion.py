"""
세분화된 보스 동작 시트를 게임 에셋으로 가져온다.

원본: art_source/bosses/motion/{golem,knight,lich}.png
      (2048x1280, 256px 칸, 8열 x 5행. 왼쪽을 보는 그림. 발 기준점은 칸의 (128, 224))
        1행: 대기 4 + 피격 4    2행: 이동 8    3행: 기본 공격 8    4행: 특수 공격 8    5행: 사망 8
결과: assets/sprites/boss/boss_*.png, assets/data/sprites_bosses.json

공격 줄은 앞 4칸이 준비 동작, 5번째 칸부터가 타격이다. 게임은 보스 공격마다 정해진 자세 이름
(balance.json 의 bossAttacks[].anim)으로 "<자세>_tele"(예고 동안)와 "<자세>_hit"(타격부터)를 찾으므로,
여기서 자세 이름을 기본 공격 줄 또는 특수 공격 줄에 이어 준다.

실행: python tools/import_boss_motion.py
"""
import json
import os
import shutil

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "art_source", "bosses", "motion")
OUT = os.path.join(ROOT, "assets", "sprites", "boss")
MANIFEST = os.path.join(ROOT, "assets", "data", "sprites_bosses.json")

CELL = 256
ATTACK_ROW, SKILL_ROW = 2, 3
WINDUP = 4  # 공격 줄에서 준비 동작인 칸 수

# 보스 id: (원본 이름, 확대 배율, 이동 동작의 초당 프레임, {자세 이름: 공격 줄})
# 이동 동작 속도는 실제 이동 속도(balance.json 의 bosses[].speed)에 맞춘다:
# 걷는 보스는 8프레임이 두 걸음이므로, 한 걸음에 몸 너비의 절반쯤 나아가도록 느리게 돌린다.
BOSSES = {
    "boss_mine": ("golem", 1.3, 5.5, {"swing": ATTACK_ROW, "slam": SKILL_ROW}),
    "boss_fortress": ("knight", 1.3, 6, {"smash": ATTACK_ROW, "bash": SKILL_ROW}),
    "boss_temple": ("lich", 1.3, 8, {"cast": ATTACK_ROW, "ritual": SKILL_ROW, "summon": SKILL_ROW}),
}


def anim(file, row, col, frames, fps, loop=True):
    a = {"file": file, "frameWidth": CELL, "frameHeight": CELL, "row": row, "col": col, "frames": frames, "fps": fps}
    if not loop:
        a["loop"] = False
    return a


def main():
    os.makedirs(OUT, exist_ok=True)
    manifest = {"_설명": "세분화된 보스 동작 시트. tools/import_boss_motion.py 가 art_source/bosses/motion 에서 만든다."}
    for boss, (name, scale, move_fps, poses) in BOSSES.items():
        shutil.copyfile(os.path.join(SRC, name + ".png"), os.path.join(OUT, boss + ".png"))
        f = "sprites/boss/" + boss + ".png"
        anims = {
            "idle": anim(f, 0, 0, 4, 5.5),
            "hurt": anim(f, 0, 4, 4, 11, loop=False),
            "move": anim(f, 1, 0, 8, move_fps),
            "death": anim(f, 4, 0, 8, 7, loop=False),
        }
        for pose, row in poses.items():
            anims[pose + "_tele"] = anim(f, row, 0, WINDUP, 8, loop=False)
            anims[pose + "_hit"] = anim(f, row, WINDUP, 8 - WINDUP, 10, loop=False)
        manifest[boss] = {"originX": 128, "originY": CELL - 224, "scale": scale, "facesLeft": True, "anims": anims}
    with open(MANIFEST, "w", encoding="utf-8") as fp:
        json.dump(manifest, fp, ensure_ascii=False, indent=1)
    print("ok", list(BOSSES))


if __name__ == "__main__":
    main()

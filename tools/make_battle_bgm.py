"""던전에서 잡몹과 싸울 때(방이 잠기는 싸움) 나오는 전투 배경음악 세 곡을 코드로 작곡·합성한다.

각 던전 탐험 곡과 같은 조·같은 주제를 빠르고 세게 바꿨다. 탐험 곡에서 전투곡으로 넘어가도 같은 노래가
긴장한 것처럼 들리고, 보스곡과도 같은 조라서 보스방으로 이어질 때 덜 튄다.

- battle_mine.ogg    무너진 갱도 : D단조 132BPM. 큰북·곡괭이 쇳소리 리듬, 낮은 현 8분 박동, 바순 주제.
- battle_citadel.ogg 저주받은 성채: C단조 140BPM. 행진 스네어, 하프시코드 16분 음형, 호른 주제, 합창 화음.
- battle_tower.ogg   마왕의 탑    : E단조 150BPM. 팀파니·스네어, 트레몰로 현, 하프 16분 아르페지오, 클라리넷 주제.

16마디가 끊김 없이 반복된다 (세 바퀴를 이어 만든 뒤 가운데만 쓴다).
실행: python tools/make_battle_bgm.py [미리듣기 wav 폴더] [곡 이름...]   (numpy, ffmpeg 필요)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_camp_bgm as C  # noqa: E402
import make_village_bgm as V  # noqa: E402
import make_dungeon_bgm as D  # noqa: E402

rng = C.rng
n, hz, place = C.n, C.midi_hz, C.place

MINE_CHORDS = [("D2", ["D3", "F3", "A3"]), ("A#1", ["A#2", "D3", "F3"]), ("C2", ["C3", "E3", "G3"]), ("A1", ["A2", "C#3", "E3"])]
CITADEL_CHORDS = [("C2", ["C3", "D#3", "G3"]), ("G#1", ["G#2", "C3", "D#3"]), ("F2", ["F2", "G#2", "C3"]), ("G1", ["G2", "B2", "D3"])]
TOWER_CHORDS = [("E2", ["E3", "G3", "B3"]), ("C2", ["C3", "E3", "G3"]), ("A1", ["A2", "C3", "E3"]), ("B1", ["B2", "D#3", "F#3"])]

# 탐험 곡 주제를 짧게 줄여 리듬을 세운 것 (마디, 박, 음, 길이)
MINE_THEME = [(0, 0, "D3", 1.5), (0, 1.5, "F3", 0.5), (0, 2, "E3", 1), (0, 3, "D3", 1), (1, 0, "A#2", 1.5), (1, 1.5, "C3", 0.5), (1, 2, "D3", 2),
              (2, 0, "C3", 1), (2, 1, "E3", 1), (2, 2, "G3", 1), (2, 3, "F3", 1), (3, 0, "E3", 1.5), (3, 1.5, "C#3", 0.5), (3, 2, "A2", 2)]
CITADEL_THEME = [(0, 0, "C4", 1), (0, 1, "D#4", 1), (0, 2, "G4", 2), (1, 0, "G#4", 1.5), (1, 1.5, "G4", 0.5), (1, 2, "D#4", 2),
                 (2, 0, "F4", 1), (2, 1, "G#4", 1), (2, 2, "C5", 1), (2, 3, "A#4", 1), (3, 0, "B4", 2), (3, 2, "D4", 2)]
TOWER_THEME = [(0, 0, "E4", 0.5), (0, 0.5, "F#4", 0.5), (0, 1, "G4", 1), (0, 2, "B4", 2), (1, 0, "C5", 1), (1, 1, "B4", 1), (1, 2, "G4", 2),
               (2, 0, "A4", 0.5), (2, 0.5, "B4", 0.5), (2, 1, "C5", 1), (2, 2, "E5", 2), (3, 0, "D#5", 2), (3, 2, "B4", 2)]


def hat():
    import numpy as np
    nsamp = int(0.05 * C.SR)
    t = np.arange(nsamp) / C.SR
    x = rng.normal(0, 1, nsamp)
    x = x - C.lowpass_fft(x, 7000)
    return x * np.exp(-t * 90)


def drums(buf, at, beat, style, fill):
    """한 마디 북. style: 0 갱도(큰북 위주), 1 성채(행진 스네어), 2 탑(팀파니+스네어)."""
    if style == 0:
        for k in (0, 1.5, 2, 3.5):
            place(buf, D.low_drum(52, 0.7, 6), at + k * beat, 0.3 if k in (0, 2) else 0.2)
        for k in (1, 3):
            place(buf, D.snare(0.2, 1400), at + k * beat, 0.12, pan=-0.2)
            place(buf, D.pick_tap(2500 + 300 * rng.random()), at + k * beat, 0.06, pan=0.45)
    elif style == 1:
        place(buf, D.low_drum(48, 0.8, 5), at, 0.28)
        place(buf, D.low_drum(48, 0.8, 5), at + 2 * beat, 0.22)
        for k in range(8):
            v = 0.12 if k % 2 == 0 else 0.06
            if k in (2, 6):
                v = 0.16
            place(buf, D.snare(0.15, 1800), at + k * beat / 2 + rng.normal(0, 0.003), v, pan=-0.25)
    else:
        for k in (0, 0.75, 2, 2.5):
            place(buf, D.low_drum(65, 0.5, 9), at + k * beat, 0.26 if k in (0, 2) else 0.16)
        for k in (1, 3):
            place(buf, D.snare(0.18, 2000), at + k * beat, 0.14, pan=-0.2)
    for k in range(8):
        place(buf, hat(), at + k * beat / 2, 0.035 if k % 2 else 0.05, pan=0.35)
    if fill:
        # 마지막 마디 끝에 16분음표 스네어 채우기
        for k in range(8):
            place(buf, D.snare(0.12, 1900), at + 2 * beat + k * beat / 4, 0.04 + 0.1 * k / 8, pan=-0.2)


def battle(buf, off, beat, chords, theme, style):
    bar = 4 * beat
    for b in range(16):
        root, notes = chords[b % 4]
        at = off + b * bar
        sec = b // 4  # 0: 북+박동, 1: 주제, 2: 주제+화음 크게, 3: 주제 한 옥타브 위
        # 낮은 현 8분 박동 (근음-근음-옥타브)
        for k in range(8):
            m = n(root) + 12 + (12 if k % 4 == 3 else 0)
            place(buf, V.bowed(hz(m), beat * 0.35, 900, 0.0), at + k * beat / 2, 0.13 if k % 2 == 0 else 0.09)
        place(buf, C.bass(hz(n(root)), beat * 0.9), at, 0.3)
        place(buf, C.bass(hz(n(root)), beat * 0.9), at + 2 * beat, 0.24)
        drums(buf, at, beat, style, fill=(b % 4 == 3))
        # 던전마다 다른 16분음표 색깔
        for k in range(16):
            idx = [0, 1, 2, 1][k % 4]
            f = hz(n(notes[idx]) + 24 + (12 if style == 2 and k % 8 >= 4 else 0))
            t = at + k * beat / 4
            if style == 0 and k % 2 == 0:
                place(buf, D.pizz(f / 2, 0.4), t, 0.09, pan=0.25)
            elif style == 1:
                place(buf, D.harpsichord(f / 2, 0.6), t, 0.05 if k % 2 else 0.07, pan=0.3)
            elif style == 2:
                place(buf, D.pluck(f, 0.6, 0.6), t, 0.05, pan=0.35)
        if style == 2:
            place(buf, D.tremolo_strings([m[:-1] + str(int(m[-1]) + 1) for m in notes], bar, 14), at, 0.1, pan=-0.35)
        if sec >= 2:
            if style == 1:
                place(buf, D.choir(notes, bar), at - 0.1, 0.13, pan=-0.15)
            else:
                place(buf, C.pad(notes, bar), at - 0.2, 0.12)
        # 현이 2마디마다 짧게 찌른다 (금관은 거슬린다는 의견이 있어서 현으로)
        if b % 2 == 1 and sec >= 1:
            stab = [m[:-1] + str(int(m[-1]) + 1) for m in notes]
            for k in (2.5, 3):
                for m, g in ((stab[0], 0.07), (stab[1], 0.05), (stab[2], 0.05)):
                    place(buf, V.bowed(hz(n(m)), beat * 0.3, 2400, 0.0), at + k * beat, g, pan=-0.1)
        if b == 0 and style == 2:
            place(buf, D.thunder(3.0), at, 0.1)
    for rep in range(1, 4):
        for b, bt, name, length in theme:
            at = off + (rep * 4 + b) * bar + bt * beat
            m = n(name) + (12 if rep == 3 else 0)
            dur = length * beat * 0.9
            if style == 0:
                place(buf, D.reed(hz(m), dur, 1000, 0.32), at, 0.13)
                if rep == 3:
                    place(buf, C.flute(hz(m + 12), dur), at, 0.06, pan=0.15)
            elif style == 1:
                place(buf, D.horn(hz(m), dur), at, 0.2)
            else:
                place(buf, D.reed(hz(m), dur, 1800, 0.5, 0.005), at, 0.08)
                if rep >= 2:
                    place(buf, V.bowed(hz(m + 12), dur, 3000, 0.006), at, 0.05, pan=0.2)


def mine(buf, off, beat):
    battle(buf, off, beat, MINE_CHORDS, MINE_THEME, 0)


def citadel(buf, off, beat):
    battle(buf, off, beat, CITADEL_CHORDS, CITADEL_THEME, 1)


def tower(buf, off, beat):
    battle(buf, off, beat, TOWER_CHORDS, TOWER_THEME, 2)


# (곡, BPM, 마디, 평균 크기 dBFS, 잔향 길이, 잔향 양)
D.SONGS.update({
    "battle_mine": (mine, 132, 16, -16, 1.6, 0.2),
    "battle_citadel": (citadel, 140, 16, -16, 2.0, 0.22),
    "battle_tower": (tower, 150, 16, -16, 1.8, 0.2),
})


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    names = sys.argv[2:] or ["battle_mine", "battle_citadel", "battle_tower"]
    for name in names:
        D.render(name, preview)


if __name__ == "__main__":
    main()

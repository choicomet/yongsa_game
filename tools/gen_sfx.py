#!/usr/bin/env python3
"""
임시 효과음(assets/sfx/*.wav)과 배경음(assets/music/*.wav)을 합성한다.
실제 효과음으로 바꿀 때는 같은 이름의 .ogg/.wav 파일을 넣으면 된다(.ogg 가 우선).
사용법: python3 tools/gen_sfx.py
"""
import math
import os
import random
import struct
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SFX = os.path.join(ROOT, "assets", "sfx")
MUSIC = os.path.join(ROOT, "assets", "music")
RATE = 22050
rnd = random.Random(3)


def write(path, samples):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 30000)) for s in samples))


def env(i, n, attack=0.01, release=0.6):
    t = i / RATE
    dur = n / RATE
    a = min(1, t / attack) if attack > 0 else 1
    r = max(0, 1 - max(0, t - (dur - dur * release)) / (dur * release)) if release > 0 else 1
    return a * r


def tone(freq, dur, vol=0.5, wave_="sine", slide=0.0, noise=0.0, attack=0.005, release=0.7):
    n = int(dur * RATE)
    out = []
    ph = 0
    for i in range(n):
        f = freq + slide * (i / n)
        ph += 2 * math.pi * f / RATE
        if wave_ == "sine":
            s = math.sin(ph)
        elif wave_ == "square":
            s = 1 if math.sin(ph) > 0 else -1
        elif wave_ == "saw":
            s = ((ph / (2 * math.pi)) % 1) * 2 - 1
        else:
            s = 0
        s = s * (1 - noise) + (rnd.random() * 2 - 1) * noise
        out.append(s * vol * env(i, n, attack, release))
    return out


def mix(*parts):
    n = max(len(p) for p in parts)
    return [sum(p[i] for p in parts if i < len(p)) for i in range(n)]


def seq(*parts):
    out = []
    for p in parts:
        out += p
    return out


def lowpass(s, k=0.15):
    out, y = [], 0
    for v in s:
        y += k * (v - y)
        out.append(y)
    return out


SOUNDS = {
    "warn": mix(tone(880, 0.12, 0.25, "square"), tone(660, 0.12, 0.15, "square")),
    "enemy_attack": lowpass(tone(200, 0.15, 0.6, "saw", slide=-120, noise=0.5), 0.3),
    "boss_attack": lowpass(tone(90, 0.35, 0.9, "saw", slide=-50, noise=0.6), 0.2),
    "dash": lowpass(tone(300, 0.3, 0.5, "saw", slide=-200, noise=0.7), 0.25),
    "swing_sword": lowpass(tone(1200, 0.1, 0.35, "sine", slide=-800, noise=0.85), 0.5),
    "swing_hammer": lowpass(tone(500, 0.16, 0.45, "sine", slide=-350, noise=0.85), 0.3),
    "shockwave_charge": tone(300, 0.25, 0.3, "sine", slide=500),
    "shockwave": lowpass(mix(tone(160, 0.45, 0.7, "saw", slide=-100, noise=0.4), tone(900, 0.2, 0.2, "sine", slide=-600)), 0.3),
    "hit": lowpass(tone(240, 0.08, 0.6, "square", slide=-140, noise=0.5), 0.4),
    "hit_boss": lowpass(tone(140, 0.1, 0.7, "square", slide=-60, noise=0.5), 0.35),
    "enemy_die": lowpass(tone(400, 0.3, 0.5, "square", slide=-350, noise=0.4), 0.35),
    "hurt": lowpass(tone(180, 0.18, 0.7, "saw", slide=-100, noise=0.3), 0.35),
    "death": lowpass(tone(300, 0.9, 0.6, "saw", slide=-250, noise=0.2, release=0.9), 0.2),
    "clear": seq(tone(523, 0.1, 0.35, "square"), tone(659, 0.1, 0.35, "square"), tone(784, 0.22, 0.35, "square")),
    "victory": seq(tone(392, 0.14, 0.35, "square"), tone(523, 0.14, 0.35, "square"), tone(659, 0.14, 0.35, "square"),
                   tone(784, 0.5, 0.4, "square", release=0.8)),
    "door_lock": lowpass(mix(tone(90, 0.25, 0.8, "square", noise=0.4), tone(60, 0.3, 0.5, "sine")), 0.25),
    "mine_done": seq(lowpass(tone(700, 0.05, 0.6, "square", noise=0.7), 0.6), tone(1320, 0.18, 0.3, "sine")),
    "rest": seq(tone(392, 0.25, 0.25, "sine"), tone(523, 0.25, 0.25, "sine"), tone(659, 0.5, 0.25, "sine")),
    "teleport": tone(400, 0.4, 0.3, "sine", slide=900),
    "ui_click": tone(1000, 0.04, 0.2, "square"),
    "ui_open": tone(600, 0.08, 0.2, "sine", slide=300),
    "enhance_success": seq(lowpass(tone(900, 0.05, 0.6, "square", noise=0.6), 0.6), tone(784, 0.1, 0.35, "square"),
                           tone(1047, 0.3, 0.35, "square")),
    "enhance_fail": seq(lowpass(tone(900, 0.05, 0.6, "square", noise=0.6), 0.6), tone(330, 0.3, 0.35, "square", slide=-80)),
    "craft": seq(lowpass(tone(800, 0.05, 0.6, "square", noise=0.6), 0.6), lowpass(tone(800, 0.05, 0.6, "square", noise=0.6), 0.6),
                 tone(988, 0.25, 0.3, "square")),
    "dismantle": lowpass(tone(500, 0.25, 0.5, "square", slide=-300, noise=0.5), 0.4),
}


def ambient(seconds, base, minor=True, pulse=False):
    n = int(seconds * RATE)
    notes = [base, base * (1.189 if minor else 1.26), base * 1.498]
    out = []
    for i in range(n):
        t = i / RATE
        s = 0
        for k, f in enumerate(notes):
            lfo = 0.5 + 0.5 * math.sin(2 * math.pi * t / seconds * (k + 1))
            s += math.sin(2 * math.pi * f * t) * 0.12 * lfo
            s += math.sin(2 * math.pi * f * 2.003 * t) * 0.03 * lfo
        if pulse:
            beat = (t * 2) % 1
            s += math.sin(2 * math.pi * 55 * t) * 0.35 * math.exp(-beat * 8)
        out.append(s)
    # 이음매가 끊기지 않도록 앞뒤를 교차
    fade = int(0.5 * RATE)
    for i in range(fade):
        k = i / fade
        out[i] = out[i] * k + out[n - fade + i] * (1 - k)
    return lowpass(out[: n - fade], 0.2)


if __name__ == "__main__":
    for name, s in SOUNDS.items():
        write(os.path.join(SFX, name + ".wav"), s)
    write(os.path.join(MUSIC, "bgm_dungeon.wav"), ambient(16, 110))
    write(os.path.join(MUSIC, "bgm_boss.wav"), ambient(8, 82.4, pulse=True))
    write(os.path.join(MUSIC, "bgm_title.wav"), ambient(16, 130.8, minor=False))
    print("ok", len(SOUNDS), "sfx")

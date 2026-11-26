"""대장간·보스 패턴 효과음 만들기 (2026-11-26 밤, 해성 요청).

- 대장간 망치 (fg_hammer): 모루를 치는 '깡'. 강화 결과음은 원래 녹음 그대로.
- 보스 패턴 준비 (bs_*): 패턴을 시작할 때 (예고 동안) 나는 소리. 보스마다 재질이 다르다:
  골렘 = 바위·땅, 혈월의 기사 = 쇠·피, 탑의 수문장 = 번개·불·마법, 마왕 = 어둠.
- 보스 패턴 터짐 (hz_*): 장판이 터지는 순간의 소리.

재료와 도구는 make_skill_sfx.py 와 같다 (Kenney CC0 녹음 + 코드로 만든 층).
결과: assets/audio/sfx/{fg,bs,hz}_*.wav, 가장 큰 50ms 구간 RMS -7dB.
실행: python tools/make_world_sfx.py [이름 ...]
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_skill_sfx import (SR, load, pitch, t, env, noise, onepole_lp, hp, bandsweep,  # noqa: E402
                            sine_sweep, chord, crackle, place, mixlen, reverb, sat, finish, quake_roll)

rng = np.random.default_rng(1126)


def ramp(dur, power=1.5):
    """천천히 커지는 모양 (모으기·예고용)."""
    return np.clip(t(dur) / dur, 0, 1) ** power


def lp(x, hz):
    return onepole_lp(onepole_lp(x, hz), hz)


def reverse(x):
    return x[::-1].copy()


# ── 대장간: 모루 위 달군 쇠를 두드리는 둔탁한 소리 ────────────

# 해성이 녹음해 준 '대장간 모루질소리' 영상(2026-11-26)을 재서 맞춘 모루 배음 (Hz, 처음 크기).
# 1636/1745 두 배음이 붙어 있어 '깡'이 살짝 거칠게 떨리고, 0.15초쯤 울리다 망치가 눌러 빨리 사라진다.
ANVIL_PARTIALS = ((1636, 1.0), (1745, 0.55), (2880, 0.3), (5640, 0.45), (6160, 0.35), (7760, 0.4))


def anvil(ring=0.17, weight=1.0, tune=1.0):
    """망치 한 번: 모루를 치는 '깡'. 넓게 터지는 '딱' + 1.6kHz 중심의 모루 배음이 ring 초 울리다 눌려 꺼짐 + 약한 몸통."""
    metal = load("block")
    tick = bandsweep(noise(0.035), 7000, 3500) * env(0.035, 0.0003, 5) * 1.3
    hold = int(ring * SR)
    tail = int(0.25 * SR)
    shape = np.concatenate([np.exp(-np.arange(hold) / (0.22 * SR)), np.exp(-np.arange(tail) / (0.035 * SR)) * np.exp(-hold / (0.22 * SR))])
    shape[:int(0.0008 * SR)] *= np.linspace(0, 1, int(0.0008 * SR))
    tt = np.arange(len(shape)) / SR
    rings = np.zeros(len(shape))
    for i, (f, a) in enumerate(ANVIL_PARTIALS):
        speed = 1 + 0.25 * i  # 높은 배음은 조금 더 빨리 사라짐
        rings += a * np.sin(2 * np.pi * f * tune * tt + rng.uniform(0, 6)) * shape ** speed
    body = sine_sweep(320, 260, 0.1) * env(0.1, 0.001, 5)
    out = np.zeros(len(shape) + int(0.02 * SR))
    place(out, tick, 0, 1.0)
    place(out, rings, 0.0005, 0.9)
    place(out, hp(pitch(metal, 1.1), 600), 0, 0.6)
    place(out, body, 0, 0.6 * weight)
    place(out, hp(noise(0.2), 3000) * env(0.2, 0.001, 4) * 0.25, 0, 1.0)  # 쇳가루 같은 높은 잔결
    return sat(out, 1.3)


def fg_hammer():
    return reverb(anvil(), size=0.25, wet=0.12, damp=2500)


# 강화 결과음(success·keep·down·destroy·craft)은 원래 Kenney 녹음을 그대로 쓴다 (해성, 2026-11-26 밤). 여기서는 망치만 만든다.


# ── 보스 패턴 준비 (bs_) ──────────────────────────────

def growl(dur=0.7, base=70):
    """낮게 끓는 짐승 소리 (톱니파 + 거칠게 떨림)."""
    tt = t(dur)
    f = base * (1 + 0.15 * np.sin(2 * np.pi * 3 * tt)) * (1 + 0.04 * rng.standard_normal(len(tt)))
    saw = 2 * ((np.cumsum(f) / SR) % 1) - 1
    rough = 0.6 + 0.4 * np.abs(onepole_lp(noise(dur), 30) * 10).clip(0, 1)
    return lp(saw * rough, 900) * env(dur, 0.12, 2)


def grind(dur):
    """바위가 서로 갈리는 소리."""
    return bandsweep(crackle(dur, 2500, 2500) + noise(dur) * 0.15, 300, 700) * ramp(dur, 0.7)


def bs_rush():
    """골렘 돌진: 낮은 으르렁 + 바위 몸이 갈리며 땅을 박차는 소리."""
    out = np.zeros(int(0.8 * SR))
    place(out, growl(0.7, 60), 0, 1.0)
    place(out, grind(0.5), 0.1, 0.8)
    place(out, pitch(load("slam"), 0.7), 0.45, 0.6)
    return sat(out, 1.5)


def bs_slam():
    """내려찍기 준비: 무거운 몸을 들어 올리는 낮은 '우우웅' + 바위 갈림."""
    rise = sine_sweep(45, 90, 0.6) * ramp(0.6)
    out = np.zeros(int(0.65 * SR))
    place(out, rise, 0, 0.9)
    place(out, grind(0.6), 0, 0.7)
    return sat(out, 1.6)


def bs_boulder():
    """바위 뽑기: 땅에서 바위를 뜯어내는 '우지끈' + 흙 쏟아짐."""
    rock = load("destroy")
    out = np.zeros(int(0.9 * SR))
    place(out, grind(0.4), 0, 0.8)
    place(out, pitch(rock, 0.5), 0.3, 0.9)
    place(out, lp(crackle(0.5, 1500, 2500), 2500) * env(0.5, 0.02, 2), 0.35, 0.6)
    return reverb(sat(out, 1.4), size=0.3, wet=0.12, damp=2000)


def bs_pound():
    """연속 내려찍기 준비: 두 팔을 들어 올리며 숨을 모으는 낮은 소리."""
    out = np.zeros(int(0.7 * SR))
    place(out, growl(0.6, 55), 0, 0.8)
    place(out, sine_sweep(40, 75, 0.6) * ramp(0.6), 0, 0.8)
    place(out, grind(0.6), 0, 0.8)  # 노트북 스피커에서도 들리게 바위 갈리는 중음
    return sat(out, 1.5)


def bs_shield():
    """기사 방패: 방패를 세우는 '철컥' + 쇠가 웅 울림."""
    metal = load("block")
    out = np.zeros(int(0.7 * SR))
    place(out, pitch(metal, 0.8), 0, 1.0)
    place(out, pitch(metal, 0.6), 0.06, 0.7)
    place(out, chord([220, 330, 495], 0.6, 0.003) * env(0.6, 0.005, 3), 0.02, 0.35)
    return reverb(out, size=0.35, wet=0.2, damp=3500)


def bs_combo():
    """기사 연속 베기 준비: 칼을 뽑는 '스르릉'."""
    draw = bandsweep(noise(0.35), 2500, 6000) * env(0.35, 0.05, 2) * 0.8
    out = np.zeros(int(0.5 * SR))
    place(out, draw, 0, 1.0)
    place(out, pitch(load("block"), 1.4), 0.3, 0.4)
    return reverb(out, size=0.25, wet=0.15, damp=6000)


def bs_spear():
    """기사 창: 창을 돌려 쥐는 바람 + 쇠 덜그럭."""
    swirl = bandsweep(noise(0.45), 500, 1600) * (0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 6 * t(0.45)))) * env(0.45, 0.05, 2)
    out = np.zeros(int(0.55 * SR))
    place(out, swirl, 0, 1.0)
    place(out, pitch(load("block"), 1.1), 0.38, 0.4)
    return out


def bs_crescent():
    """기사 혈월 검기 모으기: 피가 끓는 듯 빨려 드는 소리 (거꾸로) + 낮은 단조."""
    suck = reverse(bandsweep(noise(0.6), 2500, 400) * env(0.6, 0.01, 3))
    drone = chord([98, 116.5, 146.8], 0.6, 0.006) * ramp(0.6)
    out = np.zeros(int(0.65 * SR))
    place(out, suck, 0, 1.0)
    place(out, drone, 0, 0.6)
    return reverb(out, size=0.4, wet=0.2, damp=3000)


def bs_spin():
    """기사 회전 베기: 점점 빨라지는 '휭 휭 휭'."""
    dur = 0.8
    rate = 4 + 10 * t(dur) / dur
    am = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(rate) / SR)
    out = bandsweep(noise(dur), 600, 2200) * am * ramp(dur, 0.8)
    return reverb(out, size=0.3, wet=0.15, damp=5000)


def bs_lightning():
    """수문장 번개: 하늘에 전기가 모이는 지직거림 (천공 낙뢰보다 낮고 어둡게)."""
    dur = 0.6
    zap = hp(crackle(dur, 1800, 8000), 1200) * ramp(dur)
    hum = np.sign(np.sin(2 * np.pi * 60 * t(dur))) * 0.25 * ramp(dur)
    out = zap * 0.8 + lp(hum, 1500) + sine_sweep(150, 600, dur, 0.04, 25) * 0.3 * ramp(dur)
    return reverb(out, size=0.35, wet=0.2, damp=5000)


def bs_firespin():
    """수문장 불 회오리: '화르륵' 불이 붙어 소용돌이치는 소리."""
    dur = 0.9
    roar = lp(noise(dur), 1800) * (0.6 + 0.4 * np.sin(2 * np.pi * 5 * t(dur))) * ramp(dur, 0.6)
    pops = lp(crackle(dur, 120, 6000), 6000) * ramp(dur, 0.5)
    out = roar * 1.2 + pops * 1.5
    return reverb(sat(out, 1.3), size=0.3, wet=0.15, damp=4000)


def bs_volley():
    """수문장 마탄 연사 준비: 마력이 '웅-웅' 맥박 치며 모임."""
    dur = 0.6
    pulse = 0.5 + 0.5 * np.sin(2 * np.pi * 8 * t(dur))
    tone = sine_sweep(220, 440, dur) * pulse * ramp(dur)
    return reverb(tone + hp(crackle(dur, 300, 7000), 2000) * 0.3, size=0.4, wet=0.25, damp=6000)


def bs_cage():
    """번개 감옥: 높은 전기 '지이잉' 이 둘러치는 소리."""
    dur = 0.7
    buzz = np.sign(np.sin(2 * np.pi * 120 * t(dur)))
    out = lp(buzz, 3000) * 0.4 * ramp(dur, 0.5) + hp(crackle(dur, 900, 9000), 2000) * 0.6
    return reverb(out, size=0.3, wet=0.2, damp=6000)


def bs_homing():
    """유도 마탄: 기분 나쁘게 흔들리는 '우우우~'."""
    dur = 0.7
    w = sine_sweep(300, 500, dur, vib=0.08, vib_hz=7) * env(dur, 0.1, 1.5)
    return reverb(w, size=0.5, wet=0.3, damp=5000)


def bs_summon():
    """부하 소환: 바닥이 갈라지며 어둠이 열리는 낮은 울림 + 종 한 번."""
    dur = 1.0
    rumble = lp(noise(dur), 160) * 5 * quake_roll(dur, 4) * ramp(dur, 0.5)
    toll = chord([110, 164.8, 220], 0.9, 0.003) * env(0.9, 0.005, 2.5)
    out = np.zeros(int(1.1 * SR))
    place(out, rumble, 0, 0.8)
    place(out, toll, 0.15, 0.6)
    return reverb(out, size=0.6, wet=0.25, damp=2500)


def bs_ring():
    """마왕 원형 탄막: 어둠이 한 점으로 빨려 들었다가 퍼질 준비 (거꾸로 '후웅')."""
    swell = reverse(lp(noise(0.6), 900) * env(0.6, 0.005, 3)) * 1.5
    sub = sine_sweep(40, 80, 0.6) * ramp(0.6)
    return reverb(swell + sub * 0.7, size=0.5, wet=0.25, damp=2500)


def bs_darkorb():
    """마왕 어둠 구체: 깊게 빨아들이는 소리 + 낮게 떨리는 화음."""
    suck = reverse(bandsweep(noise(0.7), 1500, 150) * env(0.7, 0.01, 3))
    drone = chord([73.4, 87.3, 110], 0.7, 0.008) * ramp(0.7)
    return reverb(suck + drone * 0.6, size=0.6, wet=0.3, damp=2000)


def bs_brand():
    """마왕의 낙인: 불에 지지는 '치익' + 낮은 종 한 번 (불길한)."""
    hiss = hp(noise(0.6), 3000) * env(0.6, 0.02, 2) * 0.5
    bell = chord([82.4, 123.5, 174.6], 1.0, 0.004) * env(1.0, 0.004, 2.5)
    out = np.zeros(int(1.0 * SR))
    place(out, bell, 0, 0.8)
    place(out, hiss, 0.05, 1.0)
    return reverb(out, size=0.6, wet=0.25, damp=3000)


def bs_tentacle():
    """마왕 촉수: 땅 밑에서 질척하게 꿈틀거리는 소리."""
    dur = 0.8
    squish = lp(noise(dur), 600) * (0.3 + 0.7 * np.abs(np.sin(2 * np.pi * 3.5 * t(dur)))) * 2
    gurgle = sine_sweep(90, 60, dur, vib=0.2, vib_hz=11) * 0.5
    return sat((squish + gurgle) * env(dur, 0.1, 1.5), 1.5)


def bs_eyes():
    """마왕 눈알: 유리를 긁는 듯한 높은 떨림 + 속삭임 같은 바람."""
    dur = 0.8
    glass = chord([1760, 1864.7, 2349.3], dur, 0.01) * env(dur, 0.15, 1.5)
    whisper = bandsweep(noise(dur), 2500, 4000) * env(dur, 0.2, 1.5) * 0.4
    return reverb(glass * 0.7 + whisper, size=0.6, wet=0.35, damp=8000)


def bs_charge():
    return bs_rush()


# ── 보스 패턴 터짐 (hz_) ─────────────────────────────

def boom(low=60, rock=0.6, tail=0.8):
    """둔탁한 '쿵' + 부서짐 + 짧은 땅울림."""
    sub = sine_sweep(low * 1.5, low * 0.5, 0.35) * env(0.35, 0.002, 4)
    thud = lp(noise(0.12), 900) * env(0.12, 0.001, 5) * 3
    rumble = lp(noise(tail), 200) * 4 * env(tail, 0.02, 3)
    out = np.zeros(int((tail + 0.1) * SR))
    place(out, thud, 0, 1.0)
    place(out, sub, 0, 0.7)
    place(out, pitch(load("destroy"), rock), 0.005, 0.6)
    place(out, rumble, 0.02, 0.5)
    return sat(hp(out, 70), 1.5)


def hz_boulder():
    return reverb(boom(55, 0.5, 1.0), size=0.35, wet=0.15, damp=2000)


def hz_rock():
    return boom(80, 0.7, 0.4)


def hz_pound():
    return reverb(boom(50, 0.55, 0.9), size=0.3, wet=0.12, damp=2000)


def hz_quake():
    out = lp(noise(0.9), 250) * 5 * quake_roll(0.9, 8) * env(0.9, 0.02, 2.5)
    return sat(hp(out, 60), 1.4)


def hz_leap():
    return boom(65, 0.8, 0.5)


def hz_spear():
    """창 찌르기: 쇠가 '챙' + 바람 가르는 소리."""
    stab = bandsweep(noise(0.15), 3000, 1200) * env(0.15, 0.002, 4)
    out = np.zeros(int(0.4 * SR))
    place(out, stab, 0, 0.8)
    place(out, pitch(load("block"), 1.15), 0.01, 0.8)
    return reverb(out, size=0.2, wet=0.12, damp=5000)


def hz_spin():
    slash = bandsweep(noise(0.3), 1500, 4500) * env(0.3, 0.01, 3)
    out = np.zeros(int(0.4 * SR))
    place(out, slash, 0, 1.0)
    place(out, pitch(load("swing"), 0.75), 0, 0.6)
    return out


def hz_flame():
    """불 터짐: '화악' 불길 + 탁탁 튀는 불똥."""
    whoom = lp(noise(0.6), 1500) * env(0.6, 0.01, 3) * 1.5
    pops = lp(crackle(0.5, 200, 6000), 6000) * env(0.5, 0.01, 2.5) * 1.5
    out = np.zeros(len(whoom))
    place(out, whoom, 0, 1.0)
    place(out, pops, 0, 1.0)
    return reverb(sat(out, 1.4), size=0.3, wet=0.15, damp=4000)


def hz_bolt():
    """수문장 번개 떨어짐: 짧고 날카로운 '빠직' + 짧은 천둥 (천공 낙뢰보다 짧게)."""
    crack = hp(noise(0.025), 2500) * env(0.025, 0.0005, 6) * 1.4
    snap = hp(crackle(0.18, 3000, 12000), 2000) * env(0.18, 0.001, 6)
    thunder = lp(noise(0.6), 160) * env(0.6, 0.04, 3) * 4
    out = np.zeros(int(0.7 * SR))
    place(out, crack, 0, 1.0)
    place(out, snap, 0, 0.6)
    place(out, thunder, 0.02, 0.5)
    return reverb(sat(out, 1.5), size=0.5, wet=0.2, damp=4000)


def hz_cage():
    zz = hp(crackle(0.3, 2500, 10000), 1500) * env(0.3, 0.002, 4)
    buzz = lp(np.sign(np.sin(2 * np.pi * 120 * t(0.3))), 3000) * env(0.3, 0.002, 4) * 0.4
    return reverb(zz + buzz, size=0.3, wet=0.15, damp=6000)


def hz_bone():
    """뼈 가시: 뼈가 '우두둑' 솟는 소리."""
    chop = load("kill")
    out = np.zeros(int(0.35 * SR))
    for at, p in ((0, 1.3), (0.04, 1.6), (0.08, 1.15)):
        place(out, pitch(chop, p), at, 0.6)
    return out


def hz_tentacle():
    """촉수: 땅을 뚫고 '푸욱' 솟는 질척한 소리."""
    squelch = lp(noise(0.3), 700) * env(0.3, 0.005, 3) * 2.5
    out = np.zeros(int(0.45 * SR))
    place(out, squelch, 0, 1.0)
    place(out, sine_sweep(120, 50, 0.3) * env(0.3, 0.003, 4), 0, 0.6)
    place(out, lp(pitch(load("kill"), 0.6), 1500), 0.02, 0.5)
    return sat(out, 1.4)


def hz_brand():
    """낙인 폭발: 어두운 '콰앙' + 낮은 울림."""
    out = np.zeros(int(1.2 * SR))
    place(out, boom(55, 0.6, 0.8), 0, 1.0)
    place(out, chord([55, 82.4, 116.5], 1.0, 0.006) * env(1.0, 0.01, 2.5), 0, 0.5)
    return reverb(out, size=0.6, wet=0.25, damp=2500)


def hz_whip():
    """마왕 채찍: '휘익-짝'."""
    swish = bandsweep(noise(0.18), 800, 5000) * env(0.18, 0.08, 1.5)
    snap = hp(noise(0.02), 2000) * env(0.02, 0.0005, 5) * 1.5
    out = np.zeros(int(0.3 * SR))
    place(out, swish, 0, 0.8)
    place(out, snap, 0.16, 1.0)
    return reverb(out, size=0.25, wet=0.12, damp=6000)


ALL = [fg_hammer,
       bs_rush, bs_charge, bs_slam, bs_boulder, bs_pound, bs_shield, bs_combo, bs_spear, bs_crescent, bs_spin,
       bs_lightning, bs_firespin, bs_volley, bs_cage, bs_homing, bs_summon,
       bs_ring, bs_darkorb, bs_brand, bs_tentacle, bs_eyes,
       hz_boulder, hz_rock, hz_pound, hz_quake, hz_leap, hz_spear, hz_spin, hz_flame,
       hz_bolt, hz_cage, hz_bone, hz_tentacle, hz_brand, hz_whip]

def finish_heard(x, name, level_db=-9.0):
    """노트북 스피커 기준으로 크기를 맞춘다: 150Hz 아래(잘 안 들리는 저음)를 빼고 잰 크기가 level_db 가 되게.
    저음이 많은 소리(쿵, 망치)가 재는 값만 크고 실제로는 작게 들리던 문제를 막는다.
    꼭대기는 부드럽게 눌러(-1dB) 크기를 지킨다."""
    import make_skill_sfx as base
    x = hp(hp(np.asarray(x, dtype=np.float64), 70), 70)
    nz = np.nonzero(np.abs(x) > 1e-3 * np.max(np.abs(x)))[0]
    x = x[nz[0]:nz[-1] + 1]
    f = int(0.08 * SR)
    x[-f:] *= np.linspace(1, 0, f)
    heard = hp(hp(x, 150), 150)
    w = SR // 20
    best = max(np.sqrt(np.mean(heard[i:i + w] ** 2)) for i in range(0, max(1, len(heard) - w), w // 4))
    x *= 10 ** (level_db / 20) / best
    ceil = 10 ** (-1 / 20)
    x = np.tanh(x / ceil) * ceil
    base.write_wav(os.path.join(base.SFX, name + ".wav"), x)
    print(f"{name}.wav  {len(x) / SR:.2f}s")


if __name__ == "__main__":
    for fn in ALL:
        if len(sys.argv) > 1 and fn.__name__ not in sys.argv[1:]:
            continue
        finish_heard(fn(), fn.__name__, -8.0 if fn.__name__.startswith("fg_") else -9.0)

"""Generate the twelve boss introduction cues using Kenney CC0 samples and synthesis."""
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


def bs_slam():
    """내려찍기 준비: 무거운 몸을 들어 올리는 낮은 '우우웅' + 바위 갈림."""
    rise = sine_sweep(45, 90, 0.6) * ramp(0.6)
    out = np.zeros(int(0.65 * SR))
    place(out, rise, 0, 0.9)
    place(out, grind(0.6), 0, 0.7)
    return sat(out, 1.6)


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


def bs_darkorb():
    """마왕 어둠 구체: 깊게 빨아들이는 소리 + 낮게 떨리는 화음."""
    suck = reverse(bandsweep(noise(0.7), 1500, 150) * env(0.7, 0.01, 3))
    drone = chord([73.4, 87.3, 110], 0.7, 0.008) * ramp(0.7)
    return reverb(suck + drone * 0.6, size=0.6, wet=0.3, damp=2000)


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


def hz_pound():
    return reverb(boom(50, 0.55, 0.9), size=0.3, wet=0.12, damp=2000)


def hz_spin():
    slash = bandsweep(noise(0.3), 1500, 4500) * env(0.3, 0.01, 3)
    out = np.zeros(int(0.4 * SR))
    place(out, slash, 0, 1.0)
    place(out, pitch(load("swing"), 0.75), 0, 0.6)
    return out


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


def hz_whip():
    """마왕 채찍: '휘익-짝'."""
    swish = bandsweep(noise(0.18), 800, 5000) * env(0.18, 0.08, 1.5)
    snap = hp(noise(0.02), 2000) * env(0.02, 0.0005, 5) * 1.5
    out = np.zeros(int(0.3 * SR))
    place(out, swish, 0, 0.8)
    place(out, snap, 0.16, 1.0)
    return reverb(out, size=0.25, wet=0.12, damp=6000)


def bs_intro_golem():
    """서로 밀리는 암반, 묵직한 두 발, 뒤따라 떨어지는 잔돌."""
    out = np.zeros_like(t(1.14))
    rub = grind(0.48) * np.sin(np.pi * t(0.48) / 0.48)
    place(out, rub, 0, 0.58)
    foot = lp(pitch(load("slam"), 0.67), 1450)
    for at, weight in ((0.17, 0.65), (0.49, 1.0)):
        place(out, foot, at, weight)
        place(out, sine_sweep(175, 63, 0.22) * env(0.22, 0.006, 3.3), at, 0.49 * weight)
    chips = hp(load("mine"), 950)
    for at, speed, level in ((0.57, 1.31, 0.33), (0.69, 1.65, 0.21), (0.84, 1.48, 0.12)):
        place(out, pitch(chips, speed), at, level)
    return reverb(hp(out, 54), size=0.29, wet=0.16, damp=1850)


def bs_intro_knight():
    """갑옷 고리가 부딪친 뒤 검을 뽑을 때 남는 가는 쇳소리."""
    out = np.zeros_like(t(1.12))
    armour = hp(load("block"), 520)
    place(out, pitch(armour, 0.84), 0, 0.52)
    place(out, pitch(armour, 1.14), 0.11, 0.31)
    draw = bandsweep(noise(0.40), 710, 2850) * np.sin(np.pi * t(0.40) / 0.40) ** 1.8
    place(out, draw, 0.16, 0.39)
    place(out, pitch(load("swing"), 1.12), 0.37, 0.56)
    for hz, gain, decay in ((463, 0.25, 3.7), (1218, 0.14, 5.8), (1991, 0.07, 9)):
        place(out, sine_sweep(hz * 1.012, hz, 0.56) * env(0.56, 0.004, decay), 0.42, gain)
    return reverb(hp(out, 85), size=0.26, wet=0.14, damp=3200)


def bs_intro_warden():
    """수정이 차례로 깨어나고 두 음이 겹칠 때 짧은 전류가 튄다."""
    out = np.zeros_like(t(1.22))
    for at, hz, gain in ((0.0, 329.6, 0.33), (0.18, 493.9, 0.32), (0.42, 659.2, 0.28)):
        tt = t(0.65)
        crystal = np.sin(2*np.pi*hz*tt) + 0.21*np.sin(2*np.pi*hz*2.73*tt)*np.exp(-8*tt)
        place(out, crystal * env(0.65, 0.010, 4.5), at, gain)
    arc = hp(crackle(0.23, 850, 5200), 750) * env(0.23, 0.008, 3.6)
    place(out, arc, 0.46, 1.15)
    place(out, sine_sweep(220, 331, 0.37) * env(0.37, 0.06, 3), 0.43, 0.19)
    return reverb(out, size=0.38, wet=0.19, damp=4100)


def bs_intro_demonlord():
    """숨을 안으로 들이키듯 좁아지는 공기와 서로 어긋나게 가라앉는 저음."""
    out = np.zeros_like(t(1.28))
    inhale = reverse(bandsweep(noise(0.46), 610, 1580) * env(0.46, 0.012, 3.3))
    place(out, inhale, 0, 0.34)
    for start, end, level in ((159, 130, 0.27), (167, 138, 0.26), (239, 195, 0.16)):
        place(out, sine_sweep(start, end, 0.89) * env(0.89, 0.12, 2.9), 0.16, level)
    place(out, growl(0.63, 86), 0.4, 0.31)
    place(out, lp(pitch(load("kill"), 0.81), 1500), 0.45, 0.42)
    return reverb(hp(out, 58), size=0.41, wet=0.18, damp=1900)


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


CUES = (bs_intro_golem, bs_intro_knight, bs_intro_warden, bs_intro_demonlord, bs_slam, bs_spin, bs_lightning, bs_darkorb, hz_pound, hz_spin, hz_bolt, hz_whip)

if __name__ == "__main__":
    for cue in CUES:
        if len(sys.argv) == 1 or cue.__name__ in sys.argv[1:]:
            finish_heard(cue(), cue.__name__)

"""용사 검 스킬 효과음 만들기 (2026-11-26).

스킬마다 쓰는 소리(sk_<kind>.wav)와 맞을 때 소리(sk_<kind>Hit.wav)를 만든다.
재료: assets/audio/sfx 의 Kenney CC0 녹음 + 코드로 만든 층(저음, 바람, 번개, 화음, 잔향).
결과는 assets/audio/sfx/sk_*.wav (44.1kHz 모노, 가장 큰 50ms 구간 RMS -7dB, 꼭대기 -1dB 아래).

실행: python tools/make_skill_sfx.py   (numpy, ffmpeg 필요)
"""
import os
import subprocess
import sys

import numpy as np

SR = 44100
# 스킬은 크게: 파일 기준 크기 (SfxMix.FILE_LEVELS 의 "sk_" 와 같아야 한다)
LEVEL_DB = -7
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SFX = os.path.join(ROOT, "assets", "audio", "sfx")
rng = np.random.default_rng(2026)


# ── 재료 ─────────────────────────────────────────

def load(name):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", os.path.join(SFX, name + ".wav"),
                          "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def pitch(x, ratio):
    """재생 속도를 바꿔 높낮이를 바꾼다 (ratio < 1 이면 낮고 길게)."""
    n = int(len(x) / ratio)
    return np.interp(np.arange(n) * ratio, np.arange(len(x)), x)


def t(dur):
    return np.arange(int(dur * SR)) / SR


def env(dur, attack, curve=3.0):
    """빠르게 올라갔다가 지수로 줄어드는 모양."""
    tt = t(dur)
    a = np.clip(tt / max(attack, 1e-4), 0, 1)
    d = np.exp(-curve * tt / dur)
    return a * d


def noise(dur):
    return rng.standard_normal(int(dur * SR))


def onepole_lp(x, hz):
    a = np.exp(-2 * np.pi * hz / SR)
    y = np.empty_like(x)
    s = 0.0
    for i, v in enumerate(x):
        s = (1 - a) * v + a * s
        y[i] = s
    return y


def hp(x, hz):
    return x - onepole_lp(x, hz)


def bandsweep(x, f0, f1):
    """가운데 주파수가 f0 → f1 로 움직이는 대역 필터 (상태 변수 필터)."""
    y = np.empty_like(x)
    low = band = 0.0
    q = 0.35
    for i, v in enumerate(x):
        f = f0 + (f1 - f0) * i / len(x)
        g = 2 * np.sin(np.pi * min(f, SR / 6) / SR)
        low += g * band
        high = v - low - q * band
        band += g * high
        y[i] = band
    return y


def sine_sweep(f0, f1, dur, vib=0.0, vib_hz=0.0):
    tt = t(dur)
    f = f0 * (f1 / f0) ** (tt / dur)
    if vib:
        f = f * (1 + vib * np.sin(2 * np.pi * vib_hz * tt))
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def chord(freqs, dur, detune=0.004):
    tt = t(dur)
    out = np.zeros_like(tt)
    for f in freqs:
        for d in (-detune, 0, detune):
            out += np.sin(2 * np.pi * f * (1 + d) * tt + rng.uniform(0, 6.28))
    return out / (3 * len(freqs))


def crackle(dur, density, hz_lp=4000):
    x = np.zeros(int(dur * SR))
    k = int(density * dur)
    pos = rng.integers(0, len(x), k)
    x[pos] = rng.uniform(-1, 1, k)
    return onepole_lp(x, hz_lp) * 6


def place(dst, src, at, gain=1.0):
    i = int(at * SR)
    end = min(len(dst), i + len(src))
    dst[i:end] += src[:end - i] * gain
    return dst


def mixlen(*parts):
    return np.zeros(max(len(p) for p in parts))


def reverb(x, size=0.5, wet=0.3, damp=3000):
    """작은 슈뢰더 잔향: 콤 4개 + 올패스 2개."""
    tail = int(SR * (0.4 + 1.6 * size))
    y = np.concatenate([x, np.zeros(tail)])
    acc = np.zeros_like(y)
    for ms, g in ((29.7, 0.80), (37.1, 0.78), (41.1, 0.76), (43.7, 0.74)):
        d = int(ms * (0.6 + size) * SR / 1000)
        c = y.copy()
        for i in range(d, len(c)):
            c[i] += g * (0.4 + 0.5 * size) / 0.8 * c[i - d]
        acc += onepole_lp(c, damp)
    for ms in (5.0, 1.7):
        d = int(ms * SR / 1000)
        a = acc.copy()
        for i in range(d, len(a)):
            a[i] = -0.7 * acc[i] + acc[i - d] + 0.7 * a[i - d]
        acc = a
    acc *= np.max(np.abs(x)) / (np.max(np.abs(acc)) + 1e-9)
    return y * (1 - wet) + acc * wet


def sat(x, drive=1.5):
    return np.tanh(x * drive) / np.tanh(drive)


def finish(x, name, fade_out=0.08):
    """앞 무음 자르기, 끝 페이드, LEVEL_DB(가장 큰 50ms RMS)로 맞추고 꼭대기 -1dB 아래로."""
    x = np.asarray(x, dtype=np.float64)
    nz = np.nonzero(np.abs(x) > 1e-3 * np.max(np.abs(x)))[0]
    x = x[nz[0]:nz[-1] + 1]
    f = int(fade_out * SR)
    x[-f:] *= np.linspace(1, 0, f)
    w = SR // 20
    best = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 4))
    x *= 10 ** (LEVEL_DB / 20) / best
    peak = np.max(np.abs(x))
    if peak > 10 ** (-1 / 20):
        # 꼭대기만 부드럽게 눌러서 크기는 유지
        x = np.tanh(x / peak * 1.2) / np.tanh(1.2) * 10 ** (-1 / 20)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    os.path.join(SFX, name + ".wav")], input=pcm, check=True)
    print(f"{name}.wav  {len(x) / SR:.2f}s")


# ── 스킬 소리 ─────────────────────────────────────

def cleave():
    """흑철 · 대지 가르기: 땅을 내려찍어 갈라지는 충격파. 바위 + 자갈 + 저음 (노트북 스피커에서도 들리게 저음은 적게)."""
    slam, rock = load("slam"), load("destroy")
    sub = sine_sweep(95, 32, 0.7) * env(0.7, 0.004, 4)
    rumble = onepole_lp(noise(1.0), 180) * env(1.0, 0.02, 3) * 4
    gravel = crackle(0.8, 900, 3500) * env(0.8, 0.05, 2.5)
    out = mixlen(sub, rumble, gravel, pitch(slam, 0.72))
    place(out, sub, 0, 0.4)
    place(out, pitch(slam, 0.72), 0, 0.8)
    place(out, pitch(rock, 0.55), 0.03, 1.0)
    place(out, rumble, 0.01, 0.25)
    place(out, gravel, 0.06, 0.7)
    return reverb(sat(hp(out, 90), 1.8), size=0.35, wet=0.18, damp=1800)


def cleave_hit():
    rock = load("destroy")
    thump = sine_sweep(80, 40, 0.18) * env(0.18, 0.002, 5)
    out = mixlen(pitch(rock, 0.62), thump)
    place(out, pitch(rock, 0.62), 0, 1.0)
    place(out, thump, 0, 0.3)
    # 노트북 스피커는 150Hz 아래가 거의 안 나와서, 크기를 잴 때 저음이 다 먹지 않게 깎는다
    return sat(hp(out, 140), 1.5)


def crescent():
    """혈월 · 혈월참: 붉은 초승달 검기. 휘어지며 날아가는 바람 + 낮은 쇳소리 + 어두운 단조 울림."""
    swing, metal = load("swing"), load("block")
    whoosh = bandsweep(noise(0.55), 350, 2600) * env(0.55, 0.12, 2.5) * 0.9
    whoosh2 = bandsweep(noise(0.45), 1800, 600) * env(0.45, 0.05, 3) * 0.6
    # 라 단조 (A2 C3 E3) + 반음 위 긴장음, 천천히 떨림
    drone = chord([110, 130.8, 164.8, 174.6], 0.9) * env(0.9, 0.08, 2.5)
    drone *= 1 + 0.25 * np.sin(2 * np.pi * 7 * t(0.9))
    out = mixlen(whoosh, drone)
    place(out, whoosh, 0, 1.0)
    place(out, whoosh2, 0.12, 1.0)
    place(out, pitch(swing, 0.8), 0.02, 0.6)
    place(out, pitch(metal, 0.62), 0.05, 0.25)
    place(out, drone, 0.0, 0.5)
    return reverb(out, size=0.55, wet=0.3, damp=2500)


def crescent_hit():
    chop, metal = load("kill"), load("block")
    out = mixlen(pitch(chop, 0.85), pitch(metal, 0.7))
    place(out, pitch(chop, 0.85), 0, 0.9)
    place(out, pitch(metal, 0.7), 0.01, 0.3)
    return reverb(out, size=0.3, wet=0.2, damp=2500)


def strike():
    """천공 · 천공 낙뢰 (모으기): 하늘로 전기가 모이는 소리. 올라가는 윙 소리 + 지직거림."""
    whine = sine_sweep(260, 1400, 0.4, vib=0.03, vib_hz=33) * np.clip(t(0.4) / 0.4, 0, 1) ** 1.5
    buzz = np.sign(sine_sweep(130, 700, 0.4)) * 0.3 * np.clip(t(0.4) / 0.4, 0, 1) ** 2
    zap = hp(crackle(0.4, 1400, 9000), 1500) * np.clip(t(0.4) / 0.4, 0, 1)
    out = (whine * 0.6 + onepole_lp(buzz, 2500) * 0.5 + zap * 0.5)
    return reverb(out, size=0.4, wet=0.2, damp=6000)


def strike_hit():
    """번개가 떨어짐: 날카로운 '딱' + 유리처럼 갈라지는 소리 + 길게 구르는 천둥."""
    glass = load("destroy")
    crack = hp(noise(0.03), 2500) * env(0.03, 0.0005, 6) * 1.4
    snap = hp(crackle(0.25, 3000, 12000), 2000) * env(0.25, 0.001, 6)
    thunder = onepole_lp(noise(1.3), 140) * env(1.3, 0.06, 2.2) * 5
    thunder *= 0.6 + 0.4 * np.abs(onepole_lp(noise(1.3), 9) * 8).clip(0, 1)
    out = mixlen(thunder, pitch(glass, 1.2))
    place(out, crack, 0, 1.0)
    place(out, snap, 0, 0.6)
    place(out, pitch(glass, 1.2), 0.005, 0.55)
    place(out, thunder, 0.04, 0.45)
    return reverb(sat(out, 1.6), size=0.8, wet=0.28, damp=3500)


def beam():
    """깨어난 성검 · 성광 검기: 맑은 종 + 밝은 장조 화음 반짝임 + 높은 바람."""
    bell = load("craft")
    # 다 장조 (C5 E5 G5 C6 E6)
    shine = chord([523.3, 659.3, 784.0, 1046.5, 1318.5], 1.1, detune=0.006) * env(1.1, 0.03, 2.2)
    air = bandsweep(noise(0.6), 2500, 6500) * env(0.6, 0.08, 3) * 0.5
    sparkle = np.zeros(int(0.9 * SR))
    for k in range(9):
        f = rng.uniform(2500, 5000)
        ping = np.sin(2 * np.pi * f * t(0.12)) * env(0.12, 0.002, 6)
        place(sparkle, ping, rng.uniform(0.05, 0.75), 0.25)
    out = mixlen(shine, sparkle)
    place(out, pitch(bell, 1.0), 0, 0.6)
    place(out, shine, 0.01, 0.8)
    place(out, air, 0.0, 0.7)
    place(out, sparkle, 0.0, 1.0)
    return reverb(out, size=0.9, wet=0.35, damp=9000)


def beam_hit():
    bell = load("craft")
    ping = chord([1046.5, 1568.0, 2093.0], 0.35, detune=0.003) * env(0.35, 0.002, 4)
    out = mixlen(pitch(bell, 1.5), ping)
    place(out, pitch(bell, 1.5), 0, 0.6)
    place(out, ping, 0, 0.7)
    return reverb(out, size=0.5, wet=0.3, damp=9000)


if __name__ == "__main__":
    for fn, name in ((cleave, "sk_cleave"), (cleave_hit, "sk_cleaveHit"),
                     (crescent, "sk_crescent"), (crescent_hit, "sk_crescentHit"),
                     (strike, "sk_strike"), (strike_hit, "sk_strikeHit"),
                     (beam, "sk_beam"), (beam_hit, "sk_beamHit")):
        finish(fn(), name)
    sys.exit(0)

"""용사 검 스킬 효과음 만들기 (2026-11-26).

스킬마다 쓰는 소리(sk_<kind>.wav)와 맞을 때 소리(sk_<kind>Hit.wav)를 만든다.
재료: assets/audio/sfx 의 Kenney CC0 녹음 + 코드로 만든 층(저음, 바람, 번개, 화음, 잔향).
결과는 assets/audio/sfx/sk_*.wav (44.1kHz 모노, 가장 큰 50ms 구간 RMS -7dB, 꼭대기 -1dB 아래).

실행: python tools/make_skill_sfx.py   (numpy 필요)
2026-11-26 저녁: 해성이 천공 낙뢰(날카로운 시작 + 갈라지는 결 + 길게 구르는 꼬리)를 가장 좋아해서
대지 가르기·혈월참·성광 검기도 같은 짜임으로 다시 만들었다. 대지 가르기는 이름처럼 지진 소리.
2026-11-26 밤: 혈월참 = 피의 맥박(심장 박동 → 붉은 검기, 맞으면 흡혈 '스읍'),
성광 검기 = 검의 각성(칼날 공명 '우웅' → '콰앙').
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
    """16비트 WAV 는 파이썬 기본 wave 로 읽는다 (ffmpeg 가 없어도 된다)."""
    import wave
    with wave.open(os.path.join(SFX, name + ".wav"), "rb") as w:
        ch, rate, width = w.getnchannels(), w.getframerate(), w.getsampwidth()
        raw = w.readframes(w.getnframes())
    if width != 2:
        raise ValueError(name + ": 16비트 WAV 만 읽는다")
    x = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768
    if ch > 1:
        x = x.reshape(-1, ch).mean(axis=1)
    if rate != SR:
        x = pitch(x, rate / SR)
    return x


def write_wav(path, x):
    import wave
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm)


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
    write_wav(os.path.join(SFX, name + ".wav"), x)
    print(f"{name}.wav  {len(x) / SR:.2f}s")


# ── 스킬 소리 ─────────────────────────────────────

def quake_roll(dur, rate=6.0):
    """땅이 구르듯 커졌다 작아지는 느린 흔들림 (0.35~1)."""
    slow = onepole_lp(noise(dur), rate)
    slow = slow / (np.max(np.abs(slow)) + 1e-9)
    return 0.35 + 0.65 * np.abs(slow)


def cleave():
    """흑철 · 대지 가르기: 지진. 땅을 내려찍는 '쾅' → 바위가 연달아 쩍쩍 갈라짐 → 길게 구르는 땅울림과 자갈.
    천공 낙뢰처럼 날카로운 시작 + 갈라지는 결 + 길게 구르는 꼬리. 노트북 스피커에서도 들리게 150~400Hz 대역을 살린다."""
    slam, rock = load("slam"), load("destroy")
    dur = 1.7
    crack = bandsweep(noise(0.05), 3000, 900) * env(0.05, 0.0005, 5) * 2.2
    sub = sine_sweep(70, 28, 0.9) * env(0.9, 0.003, 3.5)
    # 땅울림: 낮은 소음에 느린 흔들림 + 빠른 떨림(11Hz)
    body = onepole_lp(noise(dur), 220) * 6
    body *= quake_roll(dur, 5) * (0.75 + 0.25 * np.sin(2 * np.pi * 11 * t(dur)))
    body *= env(dur, 0.06, 2.4)
    growl = bandsweep(noise(dur), 420, 140) * env(dur, 0.05, 2.6) * quake_roll(dur, 7) * 1.4
    gravel = crackle(dur, 700, 3000) * env(dur, 0.08, 2.2)
    out = np.zeros(int((dur + 0.1) * SR))
    place(out, crack, 0, 1.0)
    place(out, pitch(slam, 0.6), 0, 0.9)
    place(out, sub, 0, 0.45)
    place(out, body, 0.02, 0.55)
    place(out, growl, 0.03, 0.8)
    place(out, gravel, 0.08, 0.6)
    # 바위가 연달아 갈라지는 소리: 점점 멀어지고 작아진다
    for i, (at, p, g) in enumerate(((0.04, 0.55, 1.0), (0.22, 0.68, 0.7), (0.41, 0.5, 0.55),
                                    (0.63, 0.75, 0.4), (0.9, 0.6, 0.28))):
        place(out, onepole_lp(pitch(rock, p), 4000 - 500 * i), at, g)
    return reverb(sat(hp(out, 70), 1.8), size=0.6, wet=0.22, damp=2200)


def cleave_hit():
    """대지 가르기에 맞음: 발밑에서 바위가 터지는 소리 + 짧은 땅울림."""
    rock = load("destroy")
    crack = bandsweep(noise(0.04), 2800, 900) * env(0.04, 0.0005, 5) * 1.8
    tremor = onepole_lp(noise(0.45), 260) * 6 * env(0.45, 0.01, 3) * quake_roll(0.45, 12)
    out = np.zeros(int(0.6 * SR))
    place(out, crack, 0, 1.0)
    place(out, pitch(rock, 0.6), 0.005, 0.9)
    place(out, tremor, 0.01, 0.6)
    # 노트북 스피커는 150Hz 아래가 거의 안 나와서, 크기를 잴 때 저음이 다 먹지 않게 깎는다
    return reverb(sat(hp(out, 120), 1.6), size=0.3, wet=0.15, damp=2500)


def heartbeat(gain_dub=0.75):
    """심장 박동 '둥-둥' 한 번 (lub-dub). 노트북 스피커에서도 들리게 120~300Hz 층을 섞는다."""
    def thump(dur, f0, f1):
        body = sine_sweep(f0, f1, dur) * env(dur, 0.004, 4)
        over = sine_sweep(f0 * 2.3, f1 * 2.3, dur) * env(dur, 0.003, 5)
        knock = onepole_lp(noise(dur), 380) * 5 * env(dur, 0.002, 6)
        return sat(body + 0.6 * over + knock, 2.2)
    out = np.zeros(int(0.5 * SR))
    place(out, thump(0.22, 120, 55), 0, 1.0)
    place(out, thump(0.2, 140, 60), 0.17, gain_dub)
    return out


def crescent():
    """혈월 · 혈월참: 피의 맥박. 심장이 '둥-둥' 두 번 뛰고 → 붉은 검기가 '촤악' 휘어 나가며
    → 핏빛 지직거림과 어두운 울림이 남는다."""
    swing, metal = load("swing"), load("block")
    beat = heartbeat()
    shing = hp(noise(0.025), 3500) * env(0.025, 0.0005, 5) * 1.4
    arc = bandsweep(noise(0.55), 600, 3400) * env(0.55, 0.03, 2.6)
    sizzle = hp(crackle(0.6, 900, 7000), 1800) * env(0.6, 0.02, 3.0)
    drone = chord([110, 130.8, 164.8], 1.0) * env(1.0, 0.05, 2.4)
    drone *= 1 + 0.35 * np.sin(2 * np.pi * 5 * t(1.0))
    out = np.zeros(int(1.45 * SR))
    place(out, beat, 0, 1.0)
    place(out, shing, 0.3, 0.9)
    place(out, pitch(metal, 1.25), 0.3, 0.4)
    place(out, pitch(swing, 0.8), 0.3, 0.8)
    place(out, arc, 0.3, 0.9)
    place(out, sizzle, 0.34, 0.4)
    place(out, drone, 0.32, 0.5)
    # 꼬리에서 약한 심장 박동이 한 번 더 (멀어짐)
    place(out, onepole_lp(heartbeat(0.6), 900), 0.95, 0.35)
    return reverb(sat(hp(out, 60), 1.4), size=0.6, wet=0.25, damp=3000)


def crescent_hit():
    """혈월참에 맞음: 베이는 '촤' 직후 피를 빨아들이는 '스읍' (흡혈 회복)."""
    chop = load("kill")
    slice_ = bandsweep(noise(0.1), 4000, 1000) * env(0.1, 0.001, 4) * 1.1
    # 스읍: 커지면서 위로 올라가는 숨 들이쉬는 바람, 끝에서 뚝 끊김
    n = 0.38
    rise = np.clip(t(n) / n, 0, 1)
    suck = bandsweep(noise(n), 500, 2600) * rise ** 2.2
    cut = int(0.015 * SR)
    suck[-cut:] *= np.linspace(1, 0, cut)
    gulp = sine_sweep(180, 320, n) * rise ** 3 * 0.35
    out = np.zeros(int(0.65 * SR))
    place(out, slice_, 0, 1.0)
    place(out, pitch(chop, 0.8), 0.005, 0.8)
    place(out, suck, 0.12, 0.9)
    place(out, gulp, 0.12, 1.0)
    return reverb(sat(out, 1.3), size=0.3, wet=0.18, damp=3500)


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
    """깨어난 성검 · 성광 검기: 검의 각성. 칼날이 '우웅' 공명하며 빠르게 커지다가 → '콰앙' 빛이 뻗어 나가고
    → 밝은 화음 여운."""
    slam, bell = load("slam"), load("craft")
    w = 0.32
    ramp = np.clip(t(w) / w, 0, 1) ** 1.6
    hum = (sine_sweep(196, 262, w, vib=0.02, vib_hz=9) + 0.5 * sine_sweep(392, 523, w, vib=0.02, vib_hz=9)
           + 0.3 * sine_sweep(588, 785, w)) * ramp
    shimmer = bandsweep(noise(w), 1200, 6000) * ramp ** 2 * 0.5
    boom = sine_sweep(110, 45, 0.6) * env(0.6, 0.002, 3.5)
    crack = hp(noise(0.03), 3000) * env(0.03, 0.0005, 5) * 1.4
    blast = bandsweep(noise(0.7), 6000, 1200) * env(0.7, 0.005, 2.6)
    shine = chord([523.3, 659.3, 784.0, 1046.5], 1.2, detune=0.005) * env(1.2, 0.01, 2.2)
    out = np.zeros(int(1.7 * SR))
    place(out, hum, 0, 0.8)
    place(out, shimmer, 0, 1.0)
    place(out, crack, w, 1.0)
    place(out, pitch(slam, 0.85), w, 0.9)
    place(out, boom, w, 0.5)
    place(out, pitch(bell, 1.0), w, 0.7)
    place(out, blast, w, 0.8)
    place(out, shine, w + 0.02, 0.6)
    return reverb(sat(hp(out, 60), 1.6), size=0.8, wet=0.3, damp=7000)


def beam_hit():
    """성광 검기에 맞음: 묵직한 빛의 '쾅' + 짧은 공명."""
    slam, bell = load("slam"), load("craft")
    crack = hp(noise(0.025), 3500) * env(0.025, 0.0004, 5) * 1.2
    ring = chord([784.0, 1046.5, 1568.0], 0.4, detune=0.003) * env(0.4, 0.002, 4)
    out = np.zeros(int(0.6 * SR))
    place(out, crack, 0, 1.0)
    place(out, pitch(slam, 1.1), 0, 0.8)
    place(out, pitch(bell, 1.3), 0, 0.5)
    place(out, ring, 0.005, 0.5)
    return reverb(sat(hp(out, 120), 1.5), size=0.4, wet=0.22, damp=7000)


if __name__ == "__main__":
    for fn, name in ((cleave, "sk_cleave"), (cleave_hit, "sk_cleaveHit"),
                     (crescent, "sk_crescent"), (crescent_hit, "sk_crescentHit"),
                     (strike, "sk_strike"), (strike_hit, "sk_strikeHit"),
                     (beam, "sk_beam"), (beam_hit, "sk_beamHit")):
        # 이름을 주면 그 소리만 다시 만든다: python tools/make_skill_sfx.py sk_cleave sk_beam
        if len(sys.argv) > 1 and name not in sys.argv[1:]:
            continue
        finish(fn(), name)
    sys.exit(0)

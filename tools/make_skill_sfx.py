"""용사 검 스킬 효과음 만들기 (2026-11-26).

스킬마다 쓰는 소리(sk_<kind>.wav)와 맞을 때 소리(sk_<kind>Hit.wav)를 만든다.
재료: assets/audio/sfx 의 Kenney CC0 녹음 + 코드로 만든 층(저음, 바람, 번개, 화음, 잔향).
결과는 assets/audio/sfx/sk_*.wav (44.1kHz 모노, 가장 큰 50ms 구간 RMS -7dB, 꼭대기 -1dB 아래).

실행: python tools/make_skill_sfx.py   (numpy 필요)
2026-11-26 저녁: 해성이 천공 낙뢰(날카로운 시작 + 갈라지는 결 + 길게 구르는 꼬리)를 가장 좋아해서
대지 가르기·혈월참·성광 검기도 같은 짜임으로 다시 만들었다. 대지 가르기는 이름처럼 지진 소리.
2026-11-26 밤: 혈월참 = 저주받은 칼날(녹슨 쇠 긁힘 '끼이익' → 낮은 웅얼거림),
성광 검기 = 검의 각성(칼날 공명 '우웅' → '콰앙'), 종소리 없음.
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


def saw(f0, f1, dur, vib=0.0, vib_hz=0.0):
    """톱니파 (목소리·쇳소리 재료)."""
    tt = t(dur)
    f = f0 * (f1 / f0) ** (tt / dur)
    if vib:
        f = f * (1 + vib * np.sin(2 * np.pi * vib_hz * tt + rng.uniform(0, 6)))
    ph = np.cumsum(f) / SR
    return 2 * (ph - np.floor(ph + 0.5))


def scrape(dur):
    """녹슨 쇠를 긁는 '끼이익': 걸렸다 미끄러지는 떨림(스틱슬립) + 높은 쇳소리 공명."""
    tt = t(dur)
    jerk = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * (38 + 10 * np.sin(2 * np.pi * 3 * tt)) * tt))
    squeal = saw(1250, 1600, dur, vib=0.025, vib_hz=17)
    rasp = bandsweep(noise(dur), 2400, 1700) * 1.5
    x = (bandsweep(squeal, 1700, 2300) * 0.8 + rasp) * onepole_lp(jerk, 300)
    return x * env(dur, 0.03, 1.6)


def murmur(dur, voices=((92, 520, 880), (110, 450, 760), (131, 600, 1000), (78, 400, 700))):
    """여러 목소리가 낮게 웅얼거림: 낮은 톱니파를 모음 모양(포먼트)으로 거르고, 목소리마다 크기가 따로 출렁인다."""
    out = np.zeros(int(dur * SR))
    for f0, f1, f2 in voices:
        v = saw(f0, f0 * 0.92, dur, vib=0.012, vib_hz=rng.uniform(4, 6))
        vowel = bandsweep(v, f1, f1 * 0.85) + 0.6 * bandsweep(v, f2, f2 * 1.1)
        swell = onepole_lp(np.abs(noise(dur)), 3) * 25
        swell = np.clip(swell / (np.max(swell) + 1e-9), 0.15, 1)
        out += vowel * swell
    return out / len(voices) * env(dur, 0.15, 1.8)


def crescent():
    """혈월 · 혈월참: 저주받은 칼날. 녹슨 쇠를 긁는 '끼이익' → 칼바람 → 여러 목소리가 낮게 웅얼거리는 울림.
    (2026-11-26 밤 해성 선택: 아이디어 2번)"""
    swing, metal = load("swing"), load("block")
    out = np.zeros(int(1.55 * SR))
    place(out, scrape(0.42), 0, 1.0)
    place(out, lp_metal(metal), 0, 0.5)
    place(out, pitch(swing, 0.75), 0.3, 0.9)
    place(out, bandsweep(noise(0.45), 2800, 500) * env(0.45, 0.02, 3), 0.32, 0.8)
    place(out, murmur(1.15), 0.32, 1.1)
    place(out, onepole_lp(noise(1.0), 200) * 4 * env(1.0, 0.1, 2.5), 0.35, 0.4)
    return reverb(sat(hp(out, 60), 1.4), size=0.7, wet=0.3, damp=2800)


def lp_metal(metal):
    return onepole_lp(pitch(metal, 0.7), 3000)


def crescent_hit():
    """혈월참에 맞음: 녹슨 칼날이 '츠윽' 긁고 베임 + 짧은 웅얼거림."""
    chop = load("kill")
    out = np.zeros(int(0.6 * SR))
    place(out, scrape(0.16), 0, 0.9)
    place(out, pitch(chop, 0.8), 0.01, 0.9)
    place(out, murmur(0.45, voices=((98, 500, 850), (123, 560, 950))), 0.05, 0.7)
    return reverb(sat(out, 1.4), size=0.35, wet=0.2, damp=3000)


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
    → 묵직한 '우웅' 여운. 종소리·화음은 넣지 않는다 (해성: 종소리 싫음)."""
    slam = load("slam")
    w = 0.38
    ramp = np.clip(t(w) / w, 0, 1) ** 1.6
    hum = (saw(98, 147, w, vib=0.03, vib_hz=11) * 0.5 + sine_sweep(196, 294, w, vib=0.03, vib_hz=11)) * ramp
    hum = onepole_lp(hum, 1400)
    rush = bandsweep(noise(w), 300, 2500) * ramp ** 2 * 0.7
    boom = sine_sweep(110, 40, 0.7) * env(0.7, 0.002, 3.2)
    crack = hp(noise(0.03), 2500) * env(0.03, 0.0005, 5) * 1.4
    blast = bandsweep(noise(0.8), 3500, 400) * env(0.8, 0.005, 2.4)
    drone = onepole_lp(saw(147, 140, 1.1, vib=0.02, vib_hz=7) + 0.6 * saw(98, 94, 1.1), 900) * env(1.1, 0.01, 2.6)
    out = np.zeros(int(1.8 * SR))
    place(out, hum, 0, 0.9)
    place(out, rush, 0, 1.0)
    place(out, crack, w, 1.0)
    place(out, pitch(slam, 0.8), w, 1.0)
    place(out, boom, w, 0.6)
    place(out, blast, w, 0.9)
    place(out, drone, w + 0.02, 0.6)
    return reverb(sat(hp(out, 60), 1.7), size=0.7, wet=0.25, damp=4000)


def beam_hit():
    """성광 검기에 맞음: 묵직한 '쾅' + 짧은 '우웅'. 종소리 없음."""
    slam = load("slam")
    crack = hp(noise(0.025), 2500) * env(0.025, 0.0004, 5) * 1.2
    thump = onepole_lp(saw(147, 110, 0.3), 900) * env(0.3, 0.003, 3.5)
    out = np.zeros(int(0.6 * SR))
    place(out, crack, 0, 1.0)
    place(out, pitch(slam, 1.0), 0, 1.0)
    place(out, thump, 0.005, 0.6)
    place(out, bandsweep(noise(0.3), 2500, 500) * env(0.3, 0.003, 3), 0, 0.5)
    return reverb(sat(hp(out, 120), 1.6), size=0.35, wet=0.18, damp=4000)


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

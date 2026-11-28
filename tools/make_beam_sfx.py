"""성광 검기(깨어난 성검 스킬) 효과음 시안 - 층 쌓기 방식 (2026-11-27, 지용 요청).

지금 소리(sk_beam, 2.3초, 합성 화음 위주)가 너무 크고 길다는 의견 → 실제 녹음(CC0) 위주로 짧게.
검기가 날아가는 시간은 0.6초(rangeTiles 12 / speedTiles 20)라서 소리 몸통도 0.6초 안에 끝내고 꼬리만 조금 남긴다.

층 (사운드 디자이너가 스킬 소리를 짜는 순서):
  타격(transient)  : 칼 뽑는 '스링' 녹음 - 첫 20ms 가 '눌렀다'를 알려 준다
  저음(sub)        : 짧은 주먹 '퉁' 녹음을 낮춰서 무게만 준다 (70Hz 아래는 잘라 노트북 스피커에서도 들리게)
  몸통(body)       : 치유 마법 녹음(맑은 빛) - 성검다운 성격
  바람(whoosh)     : 휘두르는 바람 녹음을 낮추고 높낮이를 내려 '앞으로 날아가는' 느낌 (도플러)
  반짝임(sparkle)  : 종·보석 녹음을 높여 아주 작게 위에 얹는다
재료: tools/sfx_src/beam (모두 CC0, CREDITS.txt).
실행: python tools/make_beam_sfx.py            → 게임에 쓰는 B 를 assets/audio/sfx/sk_beam.wav 로
      python tools/make_beam_sfx.py <재료> <출력> A B C  → 시안 셋을 sk_beam_A~C.wav 로 (비교용)
2026-11-27 지용이 B(빛의 종)를 골랐다. 게임 음량은 SfxMix 의 sk_beam 목표 -11dB (예전 -7dB 는 너무 컸다).
"""
import os
import sys
import wave

import numpy as np

SR = 44100
LEVEL_DB = -7          # 파일 크기 (SfxMix.SKILL_FILE_DB 와 같게). 게임 음량은 SfxMix 의 cue 목표로 정한다
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "sfx_src", "beam")
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, "..", "assets", "audio", "sfx")
rng = np.random.default_rng(20261127)


def load(name, start=0.0, dur=None):
    with wave.open(os.path.join(SRC, name + ".wav"), "rb") as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64) / 32768
    x = x - x.mean()
    nz = np.nonzero(np.abs(x) > 0.01 * np.max(np.abs(x)))[0]
    x = x[nz[0]:nz[-1] + 1]
    x = x[int(start * SR):]
    if dur:
        x = x[:int(dur * SR)]
    return x / (np.max(np.abs(x)) + 1e-9)


def write_wav(path, x):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())


def pitch(x, ratio):
    n = int(len(x) / ratio)
    return np.interp(np.arange(n) * ratio, np.arange(len(x)), x)


def glide(x, r0, r1):
    """높낮이가 r0 → r1 로 미끄러진다 (날아가며 멀어지는 도플러)."""
    n = len(x)
    rate = np.linspace(r0, r1, n)
    pos = np.cumsum(rate)
    pos = pos[pos < n - 1]
    return np.interp(pos, np.arange(n), x)


def biquad(x, kind, f, q=0.707, gain_db=0.0):
    """RBJ 바이쿼드 (lp, hp, bp, peak, hshelf)."""
    A = 10 ** (gain_db / 40)
    w0 = 2 * np.pi * f / SR
    c, s = np.cos(w0), np.sin(w0)
    al = s / (2 * q)
    if kind == "lp":
        b = [(1 - c) / 2, 1 - c, (1 - c) / 2]; a = [1 + al, -2 * c, 1 - al]
    elif kind == "hp":
        b = [(1 + c) / 2, -(1 + c), (1 + c) / 2]; a = [1 + al, -2 * c, 1 - al]
    elif kind == "bp":
        b = [al, 0, -al]; a = [1 + al, -2 * c, 1 - al]
    elif kind == "peak":
        b = [1 + al * A, -2 * c, 1 - al * A]; a = [1 + al / A, -2 * c, 1 - al / A]
    elif kind == "hshelf":
        sq = 2 * np.sqrt(A) * al
        b = [A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq)]
        a = [(A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq]
    b = np.array(b) / a[0]; a = np.array(a) / a[0]
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        o = b[0] * v + b[1] * x1 + b[2] * x2 - a[1] * y1 - a[2] * y2
        x2, x1, y2, y1 = x1, v, y1, o
        y[i] = o
    return y


def fade(x, a=0.002, r=0.05):
    x = x.copy()
    na, nr = int(a * SR), min(int(r * SR), len(x))
    if na:
        x[:na] *= np.linspace(0, 1, na)
    x[-nr:] *= np.linspace(1, 0, nr) ** 2
    return x


def place(dst, src, at, gain=1.0):
    i = int(at * SR)
    end = min(len(dst), i + len(src))
    dst[i:end] += src[:end - i] * gain


def reverb(x, size=0.4, wet=0.2, damp=6000):
    """짧은 방 울림 (콤 4개 + 올패스 2개). 꼬리를 길게 끌지 않는다."""
    tail = int(SR * (0.25 + 0.8 * size))
    y = np.concatenate([x, np.zeros(tail)])
    acc = np.zeros_like(y)
    for ms, g in ((29.7, 0.77), (37.1, 0.75), (41.1, 0.73), (43.7, 0.71)):
        d = int(ms * (0.6 + size) * SR / 1000)
        cc = y.copy()
        for i in range(d, len(cc)):
            cc[i] += g * (0.4 + 0.5 * size) / 0.8 * cc[i - d]
        acc += cc
    acc = biquad(acc, "lp", damp)
    acc = biquad(acc, "hp", 300)
    for ms in (5.0, 1.7):
        d = int(ms * SR / 1000)
        aa = acc.copy()
        for i in range(d, len(aa)):
            aa[i] = -0.7 * acc[i] + acc[i - d] + 0.7 * aa[i - d]
        acc = aa
    acc *= np.max(np.abs(x)) / (np.max(np.abs(acc)) + 1e-9)
    return y * (1 - wet) + acc * wet


def sat(x, drive=1.4):
    return np.tanh(x * drive) / np.tanh(drive)


def finish(x, level_db=LEVEL_DB, fade_out=0.12):
    """앞 무음 자르기, 70Hz 아래 자르기, 끝 페이드, 가장 큰 50ms RMS 를 level_db 로, 꼭대기 -1dB 아래."""
    x = biquad(np.asarray(x, dtype=np.float64), "hp", 70)
    nz = np.nonzero(np.abs(x) > 1e-3 * np.max(np.abs(x)))[0]
    x = x[nz[0]:nz[-1] + 1]
    f = int(fade_out * SR)
    x[-f:] *= np.linspace(1, 0, f) ** 2
    w = SR // 20
    best = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 4))
    x = x * 10 ** (level_db / 20) / best
    peak = np.max(np.abs(x))
    lim = 10 ** (-1 / 20)
    if peak > lim:
        x = np.tanh(x / peak * 1.3) / np.tanh(1.3) * lim
    return x


# ── 층 재료 ─────────────────────────────────────────

def layers_a():
    """A 빛의 칼날: 칼 '스링' + 앞으로 뻗는 바람 + 맑은 빛 (검 느낌이 가장 큼). 약 0.9초."""
    L = {}
    shing = load("draw_knife2", 0.04)
    L["타격-칼스링"] = (biquad(fade(pitch(shing, 1.12)), "peak", 5200, 1.2, 4), 0.0, 0.85)
    L["타격-칼날"] = (fade(pitch(load("blade_03"), 1.25)), 0.0, 0.45)
    sub = biquad(biquad(pitch(load("impact_punch_medium_002"), 0.72), "lp", 450), "hp", 70)
    L["저음-퉁"] = (sat(fade(sub, 0.001, 0.12), 1.8), 0.0, 0.55)
    whoosh = glide(load("swing2"), 0.95, 0.62)
    whoosh = biquad(biquad(whoosh, "hp", 350), "lp", 6000)
    L["바람-앞으로"] = (fade(whoosh, 0.02, 0.15), 0.01, 0.7)
    holy = load("Healing_Spell_Impact_9", 0.20, 0.6)
    holy = biquad(biquad(holy, "hp", 500), "hshelf", 6000, 0.7, 2)
    L["몸통-빛"] = (fade(holy, 0.01, 0.25), 0.03, 0.65)
    L["반짝임-종"] = (biquad(fade(pitch(load("bell_02"), 1.6)), "hp", 2500), 0.04, 0.22)
    tail = biquad(load("Healing_Spell_Impact_11", 0.55, 0.75), "hp", 1500)
    L["꼬리-빛여운"] = (fade(tail, 0.12, 0.35), 0.12, 0.28)
    return L, 1.0, dict(size=0.35, wet=0.18)


def layers_b():
    """B 빛의 종: 맑은 종소리와 치유 빛이 중심, 칼은 살짝. 성스럽고 부드럽게. 약 1.1초."""
    L = {}
    L["타격-주문"] = (biquad(fade(pitch(load("spell_02"), 1.15)), "hp", 900), 0.0, 0.55)
    L["타격-칼스링"] = (fade(pitch(load("draw_knife2", 0.04), 1.3)), 0.0, 0.4)
    sub = biquad(biquad(pitch(load("impact_punch_medium_002"), 0.8), "lp", 380), "hp", 70)
    L["저음-퉁"] = (sat(fade(sub, 0.001, 0.1), 1.6), 0.0, 0.4)
    holy = load("Healing_Spell_Impact_4", 0.26, 0.55)
    L["몸통-빛"] = (biquad(fade(holy, 0.005, 0.25), "hp", 400), 0.0, 0.9)
    bell = biquad(pitch(load("bell_03", 0.12), 1.19), "hp", 800)
    L["몸통-종"] = (fade(bell, 0.004, 0.35)[:int(0.8 * SR)], 0.02, 0.4)
    wind = load("Wind_Spell_Impact_5", 0.16, 0.55)
    wind = glide(biquad(wind, "hp", 600), 1.05, 0.8)
    L["바람-빛바람"] = (fade(wind, 0.02, 0.2), 0.02, 0.45)
    L["반짝임-보석"] = (biquad(fade(pitch(load("item_gem_04", 0.1), 1.5)), "hp", 3000), 0.08, 0.2)
    tail = biquad(load("Healing_Spell_Impact_11", 0.5, 0.9), "hp", 1200)
    L["꼬리-빛여운"] = (fade(tail, 0.1, 0.4), 0.15, 0.35)
    return L, 1.15, dict(size=0.5, wet=0.24)


def layers_c():
    """C 묵직한 성광: 무게 있는 '쿵' + 크리스털 깨지듯 퍼지는 빛 + 쇳소리 울림. 타격감이 가장 큼. 약 0.9초."""
    L = {}
    L["타격-베기"] = (biquad(fade(pitch(load("knife_slice", 0.12), 1.1)), "hp", 1200), 0.0, 0.7)
    sub = biquad(biquad(pitch(load("impact_punch_heavy_002"), 0.82), "lp", 300), "hp", 70)
    L["저음-쿵"] = (sat(fade(sub, 0.001, 0.15), 2.2), 0.0, 0.75)
    mid = biquad(pitch(load("impact_punch_medium_002"), 1.0), "bp", 900, 0.9)
    L["저음-몸통"] = (fade(mid, 0.001, 0.1), 0.0, 0.35)
    ice = load("Ice_Spell_Impact_5", 0.17, 0.55)
    L["몸통-빛결정"] = (biquad(fade(ice, 0.004, 0.22), "hp", 600), 0.0, 0.75)
    L["몸통-쇳울림"] = (biquad(fade(pitch(load("metal_02"), 1.35), 0.002, 0.2), "hp", 1500), 0.01, 0.3)
    whoosh = glide(load("swing2"), 0.85, 0.5)
    L["바람-무겁게"] = (fade(biquad(whoosh, "lp", 3500), 0.02, 0.15), 0.02, 0.55)
    holy = load("Healing_Spell_Impact_15", 0.25, 0.4)
    L["반짝임-빛"] = (biquad(fade(holy, 0.01, 0.2), "hp", 3000), 0.06, 0.3)
    return L, 0.95, dict(size=0.35, wet=0.16)


def mix(spec, only=None):
    L, length, rv = spec
    out = np.zeros(int(length * SR))
    for name, (x, at, g) in L.items():
        if only is None or name in only:
            place(out, x, at, g)
    return reverb(sat(out, 1.2), **rv)


VARIANTS = {"A": layers_a, "B": layers_b, "C": layers_c}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    pick = sys.argv[3:] or ["B"]
    for k in pick:
        spec = VARIANTS[k]()
        x = finish(mix(spec))
        name = "sk_beam" if len(pick) == 1 else "sk_beam_" + k
        write_wav(os.path.join(OUT, name + ".wav"), x)
        print(f"{name}.wav  {len(x) / SR:.2f}s")
        if k == "A" and len(pick) > 1:
            for nm in spec[0]:
                write_wav(os.path.join(OUT, f"layer_A_{nm}.wav"), finish(mix(spec, {nm}), -14))

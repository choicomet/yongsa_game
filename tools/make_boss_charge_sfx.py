"""보스 힘 모으기(차지) 소리 시안 - 게임 사운드 디자이너 방식으로 만들기 (2026-11-27, 지용 요청).

방법 (보통 사운드 디자이너가 차지/와인드업 소리를 만드는 순서):
  1) 보스마다 '재료 단어'를 정한다 (골렘=돌·땅, 기사=쇠·칼·피, 수문장=번개·종, 마왕=공허·합창)
  2) 역할별 층(layer)으로 나눈다: 서브(몸으로 느끼는 저음) / 몸통(보스 성격을 주는 실제 녹음) /
     질감(쌓여 가는 알갱이) / 공기(위로 열리는 바람) / 스위트너(마지막 반짝·딱)
  3) 긴장을 올리는 장치: 음높이 상승, 필터가 점점 열림, 떨림(트레몰로)이 점점 빨라짐,
     알갱이가 점점 촘촘해짐, 역재생(소리를 거꾸로 틀어 '빨려 들어오는' 느낌), 역잔향
  4) 터지기 직전 40ms 를 비워(석션) 귀가 '이제 온다'를 알게 하고, 마지막에 짧은 신호음(딱/칭)
  5) 녹음 재료는 그대로 쓰지 않고 거꾸로·느리게·필터로 바꿔 원래 소리를 알아듣기 어렵게 한다
  6) 층마다 EQ 로 자리를 나누고, 포화(saturation)로 노트북 스피커에서도 저음이 들리게, 압축으로 묶는다

재료: OpenGameArt CC0 팩 (80 CC0 RPG SFX, 100 CC0 SFX, 100 CC0 SFX #2 - rubberduck,
      150 Game Effect Sounds 모음 - Kenney/CoMiGo 등 CC0), RPG Sound Pack (artisticdude, CC0) + 코드 합성.
실행: python tools/make_boss_charge_sfx.py tools/sfx_src/boss_charge <출력 폴더>
      게임에는 A~D 를 assets/audio/sfx/charge_<golem·knight·warden·demonlord>.wav 로 넣었다.
"""
import os
import sys
import wave

import numpy as np

SR = 44100
PEAK = 0.80      # 모으기가 가장 커지는 순간 (석션 시작)
REL = 0.84       # 신호음(딱/칭) - 여기서 공격이 나간다고 보면 된다
LEN = 1.25
rng = np.random.default_rng(20261127)
SRC = sys.argv[1] if len(sys.argv) > 1 else "src"
OUT = sys.argv[2] if len(sys.argv) > 2 else "out"


# ── 기본 도구 ─────────────────────────────────────────────
def load(name):
    with wave.open(os.path.join(SRC, name + ".wav"), "rb") as w:
        x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64) / 32768
    nz = np.nonzero(np.abs(x) > 0.01 * np.max(np.abs(x)))[0]
    x = x[nz[0]:nz[-1] + 1]
    return x / (np.max(np.abs(x)) + 1e-9)


def tt(n=None):
    return np.arange(n if n is not None else int(LEN * SR)) / SR


def buf():
    return np.zeros(int(LEN * SR))


def place(dst, src, at, gain=1.0):
    i = int(at * SR)
    if i < 0:
        src, i = src[-i:], 0
    n = min(len(src), len(dst) - i)
    if n > 0:
        dst[i:i + n] += src[:n] * gain
    return dst


def place_end(dst, src, end, gain=1.0):
    """src 가 정확히 end 초에 끝나게 놓는다 (역재생 소리를 정점에 맞출 때)."""
    return place(dst, src, end - len(src) / SR, gain)


def speed(x, ratio):
    """재생 속도 바꾸기 (ratio<1: 낮고 길게)."""
    n = int(len(x) / ratio)
    return np.interp(np.arange(n) * ratio, np.arange(len(x)), x)


def speed_ramp(x, r0, r1, out_dur):
    """재생 속도가 r0→r1 로 바뀌며 out_dur 초 동안 읽는다 (음높이가 계속 올라가는 '라이저')."""
    n = int(out_dur * SR)
    r = np.linspace(r0, r1, n)
    pos = np.cumsum(r)
    pos = pos % (len(x) - 1)
    return np.interp(pos, np.arange(len(x)), x)


def svf(x, f, q=0.7, mode="lp"):
    """TPT 상태변수 필터. f 는 숫자나 샘플마다 바뀌는 배열 (필터가 점점 열리는 효과)."""
    f = np.broadcast_to(np.asarray(f, dtype=np.float64), x.shape)
    g = np.tan(np.pi * np.clip(f, 20, SR * 0.45) / SR)
    k = 1.0 / q
    a1 = 1 / (1 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    y = np.empty_like(x)
    ic1 = ic2 = 0.0
    for i in range(len(x)):
        v3 = x[i] - ic2
        v1 = a1[i] * ic1 + a2[i] * v3
        v2 = ic2 + a2[i] * ic1 + a3[i] * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        y[i] = v2 if mode == "lp" else v1 if mode == "bp" else x[i] - k * v1 - v2
    return y


def lp(x, f, q=0.7):
    return svf(x, f, q, "lp")


def hp(x, f, q=0.7):
    return svf(x, f, q, "hp")


def bp(x, f, q=1.0):
    return svf(x, f, q, "bp")


def expo(a, b, n, curve=1.0):
    u = np.linspace(0, 1, n) ** curve
    return a * (b / a) ** u


def swell(n, peak_at=PEAK, curve=2.5):
    """0 → 1 로 점점 커지다가 peak_at 에서 뚝 끊긴다."""
    e = np.zeros(n)
    m = int(peak_at * SR)
    e[:m] = np.linspace(0, 1, m) ** curve
    return e


def suck(x, start=PEAK, end=REL):
    """터지기 직전 짧게 비우기 (프리 임팩트 석션)."""
    y = x.copy()
    a, b = int(start * SR), int(end * SR)
    y[a:b] *= np.linspace(1, 0.04, b - a) ** 0.5
    y[b:] *= 0.0
    return y


def accel_trem(n, r0, r1, depth=0.8, end=PEAK):
    """점점 빨라지는 떨림 (두-두-두-두-두르르르)."""
    t = tt(n)
    rate = r0 + (r1 - r0) * np.clip(t / end, 0, 1) ** 1.6
    ph = 2 * np.pi * np.cumsum(rate) / SR
    return 1 - depth * (0.5 - 0.5 * np.cos(ph))


def grains(src_list, n, rate0, rate1, glen=(0.03, 0.09), pitch=(0.8, 1.25), end=PEAK, gain_curve=1.5):
    """알갱이(그레인) 구름: 재료 녹음 조각을 점점 촘촘하게 뿌린다 (돌 부스러기·전기 튀는 소리·쇠사슬)."""
    y = np.zeros(n)
    t = 0.02
    while t < end:
        u = t / end
        rate = rate0 + (rate1 - rate0) * u ** 1.4
        s = src_list[rng.integers(len(src_list))]
        s = speed(s, rng.uniform(*pitch))
        L = int(rng.uniform(*glen) * SR)
        if len(s) > L + 1:
            st = rng.integers(0, len(s) - L)
            g = s[st:st + L] * np.hanning(L)
        else:
            g = s * np.hanning(len(s))
        place(y, g, t, (0.25 + 0.75 * u ** gain_curve) * rng.uniform(0.6, 1.0))
        t += rng.exponential(1 / rate)
    return y


def ir(rt=1.2, damp=3500, pre=0.012):
    n = int((rt + pre) * SR)
    t = tt(n)
    h = rng.standard_normal(n) * np.exp(-6.9 * t / rt)
    h[:int(pre * SR)] = 0
    h = lp(h, damp)
    return h / np.sqrt(np.sum(h ** 2))


def conv(x, h):
    m = len(x) + len(h) - 1
    nfft = 1 << (m - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(h, nfft), nfft)[:m]


def reverb(x, rt=1.0, wet=0.3, damp=3500):
    w = conv(x, ir(rt, damp))[:len(x)]
    w *= np.max(np.abs(x)) / (np.max(np.abs(w)) + 1e-9)
    return x * (1 - wet) + w * wet


def reverse_reverb(x, rt=1.2, damp=4000):
    """역잔향: 소리에 잔향만 걸어 거꾸로 뒤집는다 → '스으으읍' 하고 빨려 들어오는 앞부분."""
    w = conv(x, ir(rt, damp))
    return norm(w[::-1])


def sat(x, drive=2.0):
    return np.tanh(x * drive) / np.tanh(drive)


def head(x, dur, tau):
    """앞부분 dur 초만 잘라 tau 초로 빨리 사라지게."""
    x = x[:int(dur * SR)]
    return x * np.exp(-np.arange(len(x)) / (tau * SR))


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def comp(x, thr_db=-18, ratio=3.0, att=0.004, rel=0.08):
    """버스 압축기: 층을 하나로 묶는다."""
    a_a, a_r = np.exp(-1 / (att * SR)), np.exp(-1 / (rel * SR))
    env = np.empty_like(x)
    e = 0.0
    ax = np.abs(x)
    for i in range(len(x)):
        c = a_a if ax[i] > e else a_r
        e = c * e + (1 - c) * ax[i]
        env[i] = e
    lvl = 20 * np.log10(env + 1e-9)
    over = np.maximum(lvl - thr_db, 0)
    return x * 10 ** (-over * (1 - 1 / ratio) / 20)


def sub(f0, f1, n, peak_at=PEAK, drive=2.5):
    """서브 저음: 음이 올라가며 커지는 사인파. 포화로 2·3배음을 만들어 작은 스피커에서도 들리게."""
    f = expo(f0, f1, n)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR)
    return sat(s * swell(n, peak_at, 2.0), drive)


def air(n, f0, f1, q=1.4, peak_at=PEAK, curve=2.8):
    """공기층: 흰 소음을 대역필터로 위로 쓸어 올리는 라이저."""
    x = rng.standard_normal(n)
    f = expo(f0, f1, n, 1.3)
    return bp(x, f, q) * swell(n, peak_at, curve)


def saw(f, n):
    ph = np.cumsum(np.broadcast_to(f, (n,))) / SR
    return 2 * (ph % 1) - 1


def finish(x, name, target_db=-7.0):
    """크기 맞추기: 가장 큰 50ms 구간 RMS 를 -7dBFS 로 (스킬 소리 sk_ 와 같은 기준, SfxMix.SKILL_FILE_DB), 피크는 -1dB 로 제한."""
    # 노트북·모니터 스피커는 70Hz 아래를 거의 못 낸다. 첫 버전은 에너지의 70~85% 가 그 아래라 게임에서 거의 안 들렸다
    # (지용 11/27). 70Hz 아래를 깎고 1~4kHz(귀가 제일 잘 듣는 대역)를 올린 뒤 크기를 맞춘다.
    x = hp(hp(x, 70), 70)
    x = x + 0.8 * bp(x, 2200, 0.7)
    w = int(0.05 * SR)
    rms = np.sqrt(np.convolve(x ** 2, np.ones(w) / w, mode="same"))
    x = x * 10 ** (target_db / 20) / (rms.max() + 1e-9)
    x = np.tanh(x / 0.89) * 0.89
    f = int(0.06 * SR)
    x[-f:] *= np.linspace(1, 0, f) ** 2
    os.makedirs(OUT, exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as wv:
        wv.setnchannels(1)
        wv.setsampwidth(2)
        wv.setframerate(SR)
        wv.writeframes(data.tobytes())
    return x


def bus(layers, gains, rt=1.0, wet=0.2, damp=3500, sweet_db=0.0):
    """믹스 버스. 마지막 층은 정점 신호음(스위트너), 나머지는 모으기 층.
    모으기 층: 합침 → 전체 크레센도(-20dB→0dB) → 잔향 → 압축 → 석션(정점 직후 40ms 비움).
    신호음: 짧은 잔향만 걸어 석션 뒤에 또렷하게 얹는다."""
    n = int(LEN * SR)
    y = np.zeros(n)
    for L, g in zip(layers[:-1], gains[:-1]):
        y[:len(L)] += L[:n] * g
    y *= 0.1 + 0.9 * np.clip(tt(n) / PEAK, 0, 1) ** 1.6
    y = reverb(y, rt, wet, damp)[:n]
    y = suck(comp(y, -16, 3.0))
    s = reverb(layers[-1][:n] * gains[-1], 0.5, 0.15, damp)[:n]
    s *= np.max(np.abs(y)) / (np.max(np.abs(s)) + 1e-9) * 10 ** (sweet_db / 20)
    return y + s


N = int(LEN * SR)


# ── 1. 골렘: 돌·땅 ─────────────────────────────────────────
def golem(return_layers=False):
    # 서브: 땅이 울리는 30→46Hz, 점점 빨라지는 떨림(땅 흔들림)
    l_sub = sub(30, 46, N, drive=3.0) * accel_trem(N, 5, 17, 0.55)
    # 몸통: 돌 긁히는 녹음(stones_03)을 0.45배로 느리게 → 바위가 갈리는 낮은 '그르르르', 거꾸로 틀어 앞이 빨려 들어오게
    grind = speed(load("stones_03"), 0.45)
    grind = np.concatenate([grind, speed(load("stones_02"), 0.5)])[::-1]
    grind = lp(hp(grind, 60), 1400, 0.9)
    l_body = place_end(np.zeros(N), grind, PEAK) * swell(N, PEAK, 1.2)
    # 몸통2: 거인 신음(giant2) 0.6배, 억눌린 힘주는 소리
    groan = lp(speed(load("giant2"), 0.6), 900)
    l_groan = place_end(np.zeros(N), groan[:int(0.75 * SR)] * np.linspace(0.2, 1, int(0.75 * SR)) ** 1.5, PEAK)
    # 역재생 큰 폭발: 저역 폭발을 거꾸로 → 정점으로 빨려 들어오는 '우우우웅'
    boom = lp(load("low_frequency_explosion_000_48kHz_16bit_2s_84586419_s04"), 700)
    l_rev = place_end(np.zeros(N), boom[::-1][-int(0.9 * SR):], PEAK)
    # 질감: 돌 부스러기가 땅에서 떠오르듯 점점 촘촘하게 (초당 5개 → 45개)
    pebbles = [load(n) for n in ("item_stone_01", "item_stone_02", "item_stone_03", "item_stone_04",
                                 "sfx100v2_stones_01", "sfx100v2_stones_02", "stones_04")]
    l_grain = hp(grains(pebbles, N, 5, 45, (0.025, 0.07), (0.7, 1.4)), 350)
    # 공기: 낮게 시작하는 먼지 바람 150→2200Hz
    l_air = air(N, 150, 2200, 1.1)
    # 스위트너: 정점에 바위 '쩍' (Earth Spell Impact 앞 90ms) + 낮은 쿵
    crack = load("Earth_Spell_Impact_1_s07")[:int(0.11 * SR)]
    crack = crack * np.exp(-np.arange(len(crack)) / (0.03 * SR))
    l_sweet = place(np.zeros(N), crack, REL)
    thump = sat(np.sin(2 * np.pi * np.cumsum(expo(90, 38, int(0.25 * SR))) / SR) * np.exp(-tt(int(0.25 * SR)) / 0.07), 2)
    l_sweet = place(l_sweet, thump, REL, 0.9)
    layers = [l_sub, l_body, l_groan, l_rev, l_grain, l_air, l_sweet]
    gains = [0.9, 0.7, 0.35, 0.6, 0.45, 0.25, 0.8]
    if return_layers:
        return layers, gains
    return bus(layers, gains, 0.9, 0.18, 2500)


# ── 2. 혈월의 기사: 쇠·칼·피 ───────────────────────────────
def knight():
    l_sub = sub(42, 68, N, drive=2.2)
    # 심장 고동 두근-두근 (점점 빨라짐): 피의 기사라는 성격
    beat = np.zeros(N)
    th = sat(np.sin(2 * np.pi * np.cumsum(expo(75, 42, int(0.18 * SR))) / SR) * np.exp(-tt(int(0.18 * SR)) / 0.05), 2.5)
    for at, g in ((0.04, 0.55), (0.17, 0.4), (0.40, 0.75), (0.50, 0.55), (0.66, 0.95), (0.73, 0.75)):
        place(beat, th, at, g)
    # 몸통: 쇠가 울리는 소리(metal-ringing)를 거꾸로 + 속도 0.75→1.3 으로 올려 '칼에 힘이 차오르는' 음높이 상승
    ring = load("metal-ringing")
    l_ring = speed_ramp(ring[::-1], 0.75, 1.35, PEAK) * np.linspace(0.3, 1, int(PEAK * SR)) ** 1.5
    l_ring = place(np.zeros(N), hp(l_ring, 400), 0)
    # 역잔향 쇳소리: metal_04 잔향만 거꾸로 → '스으으응' 빨려 들어오는 쇠
    rr = reverse_reverb(speed(load("metal_04"), 0.7), 1.0, 5000)
    l_rr = place_end(np.zeros(N), bp(rr, 1800, 0.8), PEAK)
    # 칼날 긁기: blade 녹음 조각을 이어 붙여 하나의 긴 '스르르릉' 으로, 대역이 1k→6k 로 올라감
    blades = [load(n) for n in ("blade_01", "blade_02", "blade_03")]
    scrape = grains(blades, N, 30, 90, (0.04, 0.09), (0.9, 1.3), gain_curve=1.0)
    l_scrape = bp(scrape, np.concatenate([expo(900, 6000, int(PEAK * SR)), np.full(N - int(PEAK * SR), 6000)]), 1.2)
    # 쇠사슬: 처음엔 드문드문, 끝으로 갈수록 촤라라락
    chains = [load(n) for n in ("chain_01", "chain_02", "chain_03", "chainmail1")]
    l_chain = hp(grains(chains, N, 3, 26, (0.05, 0.12), (0.85, 1.15)), 500)
    # 어두운 음: 반음 부딪히는 두 음(E2-F2) 톱니파, 필터가 점점 열림 → 불길한 긴장
    n = N
    drone = saw(expo(82.4, 98, n), n) + saw(expo(87.3, 104, n), n) * 0.8
    l_drone = lp(drone, expo(200, 2600, n, 1.5), 2.0) * swell(n, PEAK, 1.8)
    # 스위트너: 정점에 칼 뽑는 '스링' (sword-unsheathe 대신 blade_03 앞부분) + 높은 금속 반짝
    shing = hp(load("blade_03")[:int(0.25 * SR)], 1500)
    shing *= np.exp(-np.arange(len(shing)) / (0.08 * SR))
    l_sweet = place(np.zeros(N), shing, REL)
    l_sweet = place(l_sweet, th, REL, 0.7)
    layers = [l_sub, beat, l_ring, l_rr, l_scrape, l_chain, l_drone, l_sweet]
    gains = [0.6, 0.8, 0.45, 0.5, 0.22, 0.35, 0.22, 0.7]
    return bus(layers, gains, 1.1, 0.22, 4000, sweet_db=3.0)


# ── 3. 탑의 수문장: 번개·종 ─────────────────────────────────
def warden():
    l_sub = sub(38, 62, N, drive=2.5)
    # 전기 웅웅: 60Hz 전선 소리(톱니파 배음)가 1.6배로 음이 오르고, 떨림이 6→28Hz 로 빨라짐
    n = N
    hum = saw(expo(55, 95, n, 1.3), n)
    l_hum = bp(hum, expo(300, 2400, n, 1.4), 1.5) * swell(n, PEAK, 1.6) * accel_trem(n, 6, 28, 0.7)
    # 역재생 종: bell_03 을 0.55배로 낮추고 역잔향을 걸어 거꾸로 → '우우우웅' 하고 정점으로 모이는 종
    bell = speed(load("bell_03"), 0.55)
    rb = reverse_reverb(bell, 1.6, 3500)
    l_bell = place_end(np.zeros(N), lp(rb, 3000), PEAK)
    # 역재생 번개: Lightning Spell Impact 거꾸로 + 필터 열림
    lt = load("Lightning_Spell_Impact_1_s06")[::-1]
    lt = lt[-int(0.7 * SR):] if len(lt) > 0.7 * SR else lt
    l_lt = place_end(np.zeros(N), lt, PEAK)
    l_lt = lp(l_lt, np.concatenate([expo(500, 9000, int(PEAK * SR), 2), np.full(N - int(PEAK * SR), 9000)]))
    # 질감: 전기 튀는 알갱이 (초당 6개 → 70개)
    sparks = [load("Retro_Electric_Sparks_01_s05"), load("Retro_Electric_Sparks_22_s05")]
    l_spark = hp(grains(sparks, N, 6, 70, (0.01, 0.035), (0.8, 1.6)), 2000)
    # 공기: 오존 바람 600→7000Hz
    l_air = air(N, 600, 7000, 1.8)
    # 스위트너: 정점에 번개 '딱' (Lightning Spell Impact 2 앞 70ms) + 작은 종 '팅'
    crack = load("Lightning_Spell_Impact_2_s06")[:int(0.09 * SR)]
    crack *= np.exp(-np.arange(len(crack)) / (0.025 * SR))
    ting = hp(head(load("bell_02"), 0.4, 0.12), 1200)
    l_sweet = place(place(np.zeros(N), crack, REL), ting, REL, 0.35)
    l_sweet = place(l_sweet, sat(np.sin(2 * np.pi * np.cumsum(expo(110, 45, int(0.2 * SR))) / SR) * np.exp(-tt(int(0.2 * SR)) / 0.05), 2), REL, 0.6)
    layers = [l_sub, l_hum, l_bell, l_lt, l_spark, l_air, l_sweet]
    gains = [0.65, 0.35, 0.6, 0.45, 0.3, 0.18, 0.85]
    return bus(layers, gains, 1.4, 0.25, 5000, sweet_db=2.0)


# ── 4. 마왕: 공허·합창 ─────────────────────────────────────
def formant_voice(f0, n, vowels):
    """톱니파를 모음 필터(포먼트) 세 개에 통과 → 사람 목소리 같은 '아-오' 합창."""
    src = saw(f0, n) * 0.6 + saw(f0 * 1.006, n) * 0.4
    u = np.linspace(0, 1, n)
    (a1, a2, a3), (b1, b2, b3) = vowels
    out = bp(src, a1 + (b1 - a1) * u, 6) + 0.6 * bp(src, a2 + (b2 - a2) * u, 8) + 0.25 * bp(src, a3 + (b3 - a3) * u, 10)
    return out


def demonlord():
    l_sub = sub(26, 44, N, drive=3.5)
    # 낮은 합창: D2·Ab2(트라이톤)·D3, 모음 '우'→'아' 로 바뀌며 열림, 음은 반음쯤 올라감
    n = N
    choir = np.zeros(n)
    for base, g in ((73.4, 1.0), (103.8, 0.7), (146.8, 0.5)):
        choir += formant_voice(expo(base, base * 1.06, n), n, ((300, 870, 2250), (730, 1090, 2440))) * g
    l_choir = choir * swell(n, PEAK, 1.4)
    # 역재생 징: gong_01 을 0.5배로 낮춰 역잔향 → 거대한 '우오오오옹' 이 정점으로 빨려 들어옴
    gong = speed(load("gong_01"), 0.5)
    l_gong = place_end(np.zeros(N), lp(reverse_reverb(gong, 2.0, 2500), 2500), PEAK)
    # 숨 들이마시는 짐승: creature_roar_03 을 0.5배로 낮추고 거꾸로 → 마왕이 숨을 빨아들이는 소리
    roar = lp(speed(load("creature_roar_03"), 0.5)[::-1], 1600)
    roar = roar[-int(0.85 * SR):]
    l_roar = place_end(np.zeros(N), roar * np.linspace(0.1, 1, len(roar)) ** 2, PEAK)
    # 공허 빨림: 소음 하이패스가 150→6000Hz 로 올라가며 커짐 (진공으로 빨려드는 바람)
    v = rng.standard_normal(N)
    l_void = hp(v, np.concatenate([expo(150, 6000, int(PEAK * SR), 1.5), np.full(N - int(PEAK * SR), 6000)]), 0.9)
    l_void = lp(l_void, 9000) * swell(N, PEAK, 3.5)
    # 셰퍼드 라이저: 옥타브 간격 사인 6개가 같이 올라가 '끝없이 올라가는' 착각
    sh = np.zeros(N)
    t = tt(N)
    for k in range(6):
        oc = (k + t / PEAK * 0.9) % 6
        f = 55 * 2 ** oc
        a = np.exp(-((oc - 3) ** 2) / 2.2)
        sh += np.sin(2 * np.pi * np.cumsum(f) / SR) * a
    l_shep = sh * swell(N, PEAK, 1.8)
    # 스위트너: 정점에 깊은 '훅' 꺼짐 + 낮은 징 머리
    whomp = sat(np.sin(2 * np.pi * np.cumsum(expo(120, 30, int(0.35 * SR))) / SR) * np.exp(-tt(int(0.35 * SR)) / 0.09), 3)
    g_head = lp(head(speed(load("gong_02"), 0.6), 0.35, 0.12), 1800)
    l_sweet = place(place(np.zeros(N), whomp, REL), g_head, REL, 0.5)
    layers = [l_sub, l_choir, l_gong, l_roar, l_void, l_shep, l_sweet]
    gains = [0.8, 0.3, 0.55, 0.5, 0.15, 0.12, 0.9]
    return bus(layers, gains, 1.8, 0.28, 3000)


# ── 5. 공통형: 보스 누구에게나 쓰는 한 개 (게임에 소리 하나만 넣을 때) ────────
def common():
    l_sub = sub(36, 58, N, drive=2.6)
    # 역재생 큰 타격 (impactPunch_heavy 계열) + 역잔향
    hit = load("explosion")
    l_rev = place_end(np.zeros(N), reverse_reverb(lp(speed(hit, 0.7), 2500), 1.0, 3000), PEAK)
    # 점점 빨라지는 맥박 웅웅
    n = N
    pulse = saw(expo(70, 120, n), n)
    l_pulse = lp(pulse, expo(250, 2000, n, 1.5), 1.4) * swell(n, PEAK, 1.5) * accel_trem(n, 4, 22, 0.85)
    # 금속 역잔향 (스위트너 겸 음색)
    l_met = place_end(np.zeros(N), bp(reverse_reverb(load("metal_05"), 1.0, 6000), 2500, 0.9), PEAK)
    l_air = air(N, 300, 6500, 1.5)
    tick = hp(head(load("metal_02"), 0.2, 0.05), 800)
    thump = sat(np.sin(2 * np.pi * np.cumsum(expo(100, 40, int(0.22 * SR))) / SR) * np.exp(-tt(int(0.22 * SR)) / 0.06), 2)
    l_sweet = place(place(np.zeros(N), tick, REL, 0.6), thump, REL, 0.8)
    layers = [l_sub, l_rev, l_pulse, l_met, l_air, l_sweet]
    gains = [0.7, 0.6, 0.3, 0.4, 0.2, 0.85]
    return bus(layers, gains, 1.0, 0.2, 4000)


# ── 보스 구슬·검기 쏘는 소리 (shot_*, 지용 11/27 '구슬 날리는 소리도') ─────────────
# 같은 방법을 짧게 (0.5초): 앞 5ms '탁'(트랜지언트) + 날아가는 바람(음이 내려가는 대역 소음 = 멀어지는 느낌)
# + 보스 재료 꼬리 + 중고음 존재감. 자주 나므로 짧고 매번 조금씩 다르게(게임에서 높낮이 ±5%).
SN = int(0.55 * SR)


def zip_whoosh(f0, f1, dur=0.4, q=1.6):
    n = int(dur * SR)
    x = bp(rng.standard_normal(n), expo(f0, f1, n), q)
    e = np.minimum(1, tt(n) / 0.015) * np.exp(-tt(n) / (dur * 0.35))
    return x * e


def kick(f0, f1, dur, tau, drive=2.0):
    n = int(dur * SR)
    return sat(np.sin(2 * np.pi * np.cumsum(expo(f0, f1, n)) / SR) * np.exp(-tt(n) / tau), drive)


def shot_mix(parts, rt, wet):
    y = np.zeros(SN)
    for x, at, g in parts:
        place(y, x, at, g)
    return reverb(comp(y, -14, 3.0), rt, wet, 5000)[:SN]


def shot_golem():
    # 돌덩이를 던지는 소리: 바위 '쩍' + 낮은 '부웅' + 자갈 꼬리
    crack = hp(head(load("Earth_Spell_Impact_1_s07"), 0.12, 0.035), 150)
    grav = hp(head(speed(load("sfx100v2_stones_01"), 0.85), 0.3, 0.1), 400)
    return shot_mix([(crack, 0, 1.0), (zip_whoosh(900, 180, 0.35, 1.2), 0.01, 0.9),
                     (kick(110, 50, 0.18, 0.05), 0, 0.7), (grav, 0.05, 0.5)], 0.6, 0.15)


def shot_knight():
    # 붉은 검기: 칼 '스왁' (밝은 바람이 내려감) + 쇠 '칭' + 어두운 반음 꼬리
    shing = hp(head(load("blade_02"), 0.25, 0.09), 1200)
    ring = bp(head(speed(load("metal-ringing"), 1.2), 0.35, 0.12), 2500, 1.0)
    n = int(0.4 * SR)
    dark = lp(saw(np.full(n, 98.0), n) + saw(np.full(n, 104.0), n), 1200) * np.exp(-tt(n) / 0.12)
    return shot_mix([(zip_whoosh(5000, 900, 0.32, 1.4), 0, 1.0), (shing, 0, 0.8), (ring, 0.02, 0.45),
                     (dark, 0.0, 0.3), (kick(140, 70, 0.1, 0.03), 0, 0.5)], 0.9, 0.2)


def shot_warden():
    # 마탄: 전기 '지직' + 작은 종 '팅' + 음이 내려가는 '퓨우'
    zap = hp(head(load("Lightning_Spell_Impact_2_s06"), 0.15, 0.04), 600)
    sp = hp(grains([load("Retro_Electric_Sparks_01_s05")], SN, 60, 20, (0.008, 0.02), (1.0, 1.6), end=0.3), 2500)
    ting = hp(head(speed(load("bell_02"), 1.25), 0.35, 0.1), 1500)
    n = int(0.3 * SR)
    pew = np.sin(2 * np.pi * np.cumsum(expo(1400, 500, n)) / SR) * np.exp(-tt(n) / 0.08)
    return shot_mix([(zap, 0, 1.0), (sp, 0, 0.35), (ting, 0.005, 0.5), (pew, 0, 0.35),
                     (zip_whoosh(3500, 700, 0.3, 1.8), 0, 0.5)], 1.0, 0.22)


def shot_demonlord():
    # 어둠 구체: 짧은 역잔향 '스읍' 다음 낮은 '우웅' 하고 떠나감 + 징 머리 + 공허 바람
    gong = speed(load("gong_02"), 0.75)
    pre = reverse_reverb(head(gong, 0.08, 0.03), 0.25, 3000)[-int(0.07 * SR):]
    body = lp(head(gong, 0.45, 0.15), 2200)
    n = int(0.45 * SR)
    hum = lp(saw(expo(110, 70, n), n), 900) * np.exp(-tt(n) / 0.14)
    y = shot_mix([(body, 0.07, 0.8), (hum, 0.07, 0.6), (kick(120, 40, 0.25, 0.07, 2.5), 0.07, 0.8),
                  (zip_whoosh(2500, 300, 0.4, 1.0), 0.07, 0.6)], 1.3, 0.25)
    place(y, pre * 0.6, 0)
    return y


def orb_burst_demonlord():
    # 어둠 구체가 터지며 사방으로 흩어짐: 낮은 '펑' + 징 + 흩어지는 바람
    gong = lp(head(speed(load("gong_01"), 0.6), 0.5, 0.18), 2500)
    boom = lp(head(load("explosion"), 0.4, 0.12), 3000)
    return shot_mix([(kick(90, 32, 0.35, 0.1, 3.0), 0, 1.0), (boom, 0, 0.7), (gong, 0, 0.6),
                     (zip_whoosh(400, 4000, 0.35, 1.0), 0.02, 0.4)], 1.4, 0.25)


if __name__ == "__main__":
    finish(golem(), "A-골렘-바위가갈리며-쩍")
    finish(knight(), "B-혈월의기사-심장고동-칼날-스링")
    finish(warden(), "C-탑의수문장-역재생종-전기-딱")
    finish(demonlord(), "D-마왕-합창-숨들이켜기-훅")
    finish(common(), "E-공통형-보스누구나")
    LEN = 0.55
    for nm, fn in (("shot_golem", shot_golem), ("shot_knight", shot_knight), ("shot_warden", shot_warden),
                   ("shot_demonlord", shot_demonlord), ("orbBurst_demonlord", orb_burst_demonlord)):
        finish(fn(), nm, -8.0)
    LEN = 1.25
    # 층 나눠 듣기 (골렘): 층 하나씩 → 전부 합친 것
    layers, gains = golem(return_layers=True)
    names = ["1서브", "2몸통-돌갈림", "3거인신음", "4역재생폭발", "5돌부스러기", "6먼지바람", "7정점-쩍쿵"]
    gap = np.zeros(int(0.35 * SR))
    seq = []
    for L, g, nm in zip(layers, gains, names):
        seq += [norm(L) * 0.5, gap]
    full = bus(layers, gains, 0.9, 0.18, 2500)
    seq += [gap, norm(full)]
    LEN_SAVE = LEN
    x = np.concatenate(seq)
    LEN = len(x) / SR
    finish(x, "층나눠듣기-골렘(서브→몸통→신음→역재생→부스러기→바람→쩍→전체)")

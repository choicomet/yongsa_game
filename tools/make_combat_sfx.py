"""용사 기본 전투 소리 + 보스 등장 소리 만들기 (2026-11-27, 지용 요청).

게임에는 보스 등장 소리 4개만 넣었다 (아래 GAME). 기본 공격·구르기 소리는 시안만 있고 쓰지 않는다
(해성이 11/26 일부러 뺌).

만드는 것 (각각 시안 a/b 가 있는 것도 있다):
  hero_swing   용사가 칼을 휘두르는 소리 (기본 공격, 0.4초마다 나도 귀가 피곤하지 않게 짧게)
  hero_hit     칼이 몬스터를 맞히는 소리 (살 베는 '퍽' + 짧은 쇳소리)
  hero_hurt    용사가 몬스터에게 맞는 소리 (둔탁한 '퍽' + 갑옷 철컥)
  hero_roll    구르기 (옷 스치는 소리 + 땅 구르는 '두둥' + 흙 긁힘, 구르기 0.32초에 맞춤)
  intro_golem / intro_knight / intro_warden / intro_demonlord
               보스가 처음 나올 때 한 번 나는 소리 (각 2.5~3.5초, 보스마다 다른 성격)

재료: assets/audio/sfx 의 Kenney CC0 녹음 + RPG Sound Pack (artisticdude, OpenGameArt, CC0) 녹음
      + 코드로 만든 층 (저음, 바람, 웅웅 화음, 잔향).
RPG Sound Pack 녹음은 tools/sfx_src/rpg 에 둔다 (CC0, 작은 WAV).

실행: python tools/make_combat_sfx.py [출력 폴더 [이름…]]   (numpy 필요, 아래 __main__ 참고)
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_skill_sfx as k  # noqa: E402  (load, pitch, env, noise, 필터, reverb 를 같이 쓴다)
from make_skill_sfx import SR, env, noise, onepole_lp, hp, bandsweep, sine_sweep, chord, crackle, place, reverb, sat, pitch, t  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RPG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sfx_src", "rpg")
rng = np.random.default_rng(1127)
k.rng = rng


def rpg(rel):
    """RPG Sound Pack WAV (16/24비트, 44.1/48kHz) → 44.1kHz 모노 float."""
    import wave
    path = os.path.join(RPG, rel)
    with wave.open(path, "rb") as w:
        ch, rate, width = w.getnchannels(), w.getframerate(), w.getsampwidth()
        raw = w.readframes(w.getnframes())
    if width == 2:
        x = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768
    elif width == 3:
        b = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3)
        v = (b[:, 0].astype(np.int32) | (b[:, 1].astype(np.int32) << 8) | (b[:, 2].astype(np.int32) << 16))
        v = np.where(v >= 1 << 23, v - (1 << 24), v)
        x = v.astype(np.float64) / (1 << 23)
    else:
        raise ValueError(rel + ": 16/24비트만 읽는다")
    if ch > 1:
        x = x.reshape(-1, ch).mean(axis=1)
    if rate != SR:
        x = pitch(x, rate / SR)
    return trim(x)


def trim(x, floor=0.02):
    nz = np.nonzero(np.abs(x) > floor * np.max(np.abs(x)))[0]
    return x[max(0, nz[0] - 40):nz[-1] + 1] if len(nz) else x


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def fade(x, dur):
    n = min(len(x), int(dur * SR))
    x = x.copy()
    x[-n:] *= np.linspace(1, 0, n) ** 2
    return x


def cut(x, dur):
    return fade(x[:int(dur * SR)], min(0.04, dur / 3))


def lp(x, hz, passes=2):
    for _ in range(passes):
        x = onepole_lp(x, hz)
    return x


def finish(x, path, level_db, fade_out=0.06):
    """앞 무음 자르기, 끝 페이드, 가장 큰 50ms RMS 를 level_db 로, 꼭대기는 -1dB 아래로."""
    x = np.asarray(x, dtype=np.float64)
    x = hp(x - np.mean(x), 20)  # 직류 성분(한쪽으로 쏠린 파형) 제거
    nz = np.nonzero(np.abs(x) > 1e-3 * np.max(np.abs(x)))[0]
    x = x[nz[0]:nz[-1] + 1]
    x = fade(x, fade_out)
    w = SR // 20
    best = max(np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, max(1, len(x) - w), w // 4))
    x *= 10 ** (level_db / 20) / best
    peak = np.max(np.abs(x))
    if peak > 10 ** (-1 / 20):
        x = np.tanh(x / peak * 1.2) / np.tanh(1.2) * 10 ** (-1 / 20)
    k.write_wav(path, x)
    print(f"{os.path.basename(path)}  {len(x) / SR:.2f}s")
    return x


# ── 용사 기본 공격 ─────────────────────────────────

def swing_a():
    """가벼운 '휙': 녹음 휘두르기(swing2)에 짧은 바람 소리와 칼날 '싱' 꼬리를 살짝."""
    base = rpg("battle/swing2.wav")
    air = bandsweep(noise(0.16), 700, 2600) * env(0.16, 0.03, 4) * 0.5
    ring = hp(rpg("battle/sword-unsheathe2.wav"), 3000)
    out = np.zeros(int(0.24 * SR))
    place(out, norm(base), 0.0, 1.0)
    place(out, norm(air), 0.005, 0.35)
    place(out, cut(norm(ring), 0.12) * env(0.12, 0.002, 5), 0.02, 0.12)
    return out


def swing_b():
    """묵직한 '부웅': 낮은 바람이 위로 올라가는 휘두르기 (큰 칼 느낌)."""
    base = pitch(rpg("battle/swing.wav"), 0.85)
    air = bandsweep(noise(0.22), 350, 1500) * env(0.22, 0.06, 3) * 0.7
    body = lp(noise(0.2), 300) * env(0.2, 0.05, 4) * 3
    out = np.zeros(int(0.28 * SR))
    place(out, norm(cut(base, 0.25)), 0.0, 1.0)
    place(out, norm(air), 0.0, 0.5)
    place(out, norm(body), 0.01, 0.25)
    return out


def hit_a():
    """살 베는 '퍽': 녹음 타격 + 썰리는 소리 + 낮은 쿵 (작은 몹 여럿 때려도 피곤하지 않게)."""
    punch = k.load("hit")
    chop = k.load("kill")
    thump = sine_sweep(180, 60, 0.12) * env(0.12, 0.001, 5)
    slice_ = bandsweep(noise(0.06), 5000, 1800) * env(0.06, 0.0008, 5)
    out = np.zeros(int(0.25 * SR))
    place(out, norm(punch), 0.0, 0.9)
    place(out, cut(norm(chop), 0.12), 0.003, 0.45)
    place(out, norm(thump), 0.0, 0.55)
    place(out, norm(slice_), 0.0, 0.35)
    return out


def hit_b():
    """'챙-퍽': 칼날이 닿는 짧은 쇳소리가 먼저, 바로 둔탁한 타격 (단단한 적 느낌)."""
    punch = k.load("hit")
    ting = hp(rpg("inventory/metal-ringing.wav"), 2500)
    thump = sine_sweep(150, 55, 0.14) * env(0.14, 0.001, 4.5)
    click = hp(noise(0.012), 3000) * env(0.012, 0.0003, 4)
    out = np.zeros(int(0.3 * SR))
    place(out, norm(click), 0.0, 0.6)
    place(out, cut(norm(ting), 0.16) * env(0.16, 0.001, 6), 0.0, 0.3)
    place(out, norm(punch), 0.008, 1.0)
    place(out, norm(thump), 0.008, 0.6)
    return out


def hurt():
    """용사가 맞음: 둔탁한 '퍽' + 갑옷 철컥 + 짧은 저음 (위험하다는 느낌, 너무 길지 않게)."""
    soft = k.load("hurt")
    mail = hp(rpg("inventory/chainmail1.wav"), 1500)
    thump = sine_sweep(120, 45, 0.18) * env(0.18, 0.001, 4)
    out = np.zeros(int(0.35 * SR))
    place(out, norm(soft), 0.0, 1.0)
    crunch = bandsweep(noise(0.07), 2500, 700) * env(0.07, 0.001, 4)
    place(out, norm(thump), 0.0, 0.4)
    place(out, norm(crunch), 0.0, 0.35)
    place(out, cut(norm(mail), 0.22), 0.01, 0.35)
    return sat(out, 1.4)


# ── 구르기 ─────────────────────────────────────

def roll_a():
    """'휘릭-두둥': 무거운 옷 스치는 소리 → 땅에 몸이 닿는 낮은 '두둥' 2번 → 흙 긁힘."""
    cloth = rpg("inventory/cloth-heavy.wav")
    out = np.zeros(int(0.45 * SR))
    place(out, cut(norm(cloth), 0.3), 0.0, 0.8)
    for at, g in ((0.08, 1.0), (0.2, 0.65)):
        th = sine_sweep(110, 50, 0.1) * env(0.1, 0.002, 4)
        dirt = lp(noise(0.08), 900) * env(0.08, 0.002, 4) * 3
        place(out, norm(th), at, 0.7 * g)
        place(out, norm(dirt), at, 0.35 * g)
    scrape = bandsweep(noise(0.18), 1800, 700) * env(0.18, 0.02, 3)
    place(out, norm(scrape), 0.2, 0.25)
    return out


def roll_b():
    """가벼운 '슉': 옷 스치는 소리 + 바람이 짧게 지나감 (닌자처럼 가볍게)."""
    cloth = rpg("inventory/cloth.wav")
    air = bandsweep(noise(0.26), 500, 2200) * env(0.26, 0.08, 3)
    tap = lp(noise(0.05), 700) * env(0.05, 0.001, 5) * 3
    out = np.zeros(int(0.38 * SR))
    place(out, cut(norm(cloth), 0.3), 0.0, 0.8)
    place(out, norm(air), 0.0, 0.4)
    place(out, norm(tap), 0.24, 0.35)
    return out


# ── 보스 등장 ───────────────────────────────────

def swell(dur, f0, f1, peak_at=0.8):
    """뒤집은 바람: 점점 커지다가 peak_at 에서 뚝 끊긴다 (등장 직전의 '쉬이익')."""
    x = bandsweep(noise(dur), f0, f1)
    tt = t(dur)
    e = np.clip(tt / (dur * peak_at), 0, 1) ** 2.5
    e[tt > dur * peak_at] = np.exp(-(tt[tt > dur * peak_at] - dur * peak_at) * 30)
    return norm(x * e)


def boom(dur=1.2, f0=60, f1=28):
    return fade(norm(sine_sweep(f0, f1, dur) * env(dur, 0.004, 3.5) + 0.3 * lp(noise(dur), 120) * env(dur, 0.004, 4) * 8), dur * 0.4)


def golem():
    """갱도를 삼킨 골렘: 땅이 우르르 울리고 → 자갈이 쏟아지고 → 바위 몸이 '그르릉' 깨어나며 '쿵'."""
    dur = 3.2
    out = np.zeros(int(dur * SR))
    # 땅울림: 낮은 소음이 천천히 커짐
    rumble = lp(noise(2.2), 140) * 8 * k.quake_roll(2.2, 5.0)
    rumble *= np.clip(t(2.2) / 1.6, 0, 1)
    place(out, norm(rumble), 0.0, 0.5)
    # 자갈 우수수
    rocks = k.load("destroy")
    mine = k.load("mine")
    for i, at in enumerate(np.sort(rng.uniform(0.3, 1.6, 9))):
        place(out, norm(pitch(mine if i % 2 else rocks, rng.uniform(0.45, 0.8))), at, rng.uniform(0.1, 0.22))
    # 바위가 깨어나는 신음 (거인 목소리를 낮게)
    groan = pitch(rpg("NPC/giant/giant2.wav"), 0.55)
    place(out, lp(norm(groan), 1800, 1), 1.0, 0.75)
    # 쿵!
    slam = pitch(k.load("slam"), 0.6)
    place(out, norm(boom(1.4, 55, 26)), 1.75, 1.0)
    place(out, norm(slam), 1.75, 0.8)
    place(out, norm(pitch(rocks, 0.5)), 1.78, 0.45)
    return reverb(fade(sat(out, 1.3), 0.5), size=0.6, wet=0.25, damp=2500)


def knight():
    """혈월의 기사: 갑옷 걸음 두 번 → 칼을 '스릉' 뽑음 → 낮은 종 + 어두운 단조 화음이 깔림."""
    dur = 3.2
    out = np.zeros(int(dur * SR))
    clank = k.load("block")
    mail = rpg("inventory/chainmail2.wav")
    for at in (0.0, 0.5):
        place(out, norm(pitch(clank, 0.7)), at, 0.55)
        place(out, norm(lp(noise(0.12), 200) * env(0.12, 0.002, 4) * 4), at, 0.5)
        place(out, cut(norm(mail), 0.3), at + 0.02, 0.25)
    # 칼 뽑기
    draw = rpg("battle/sword-unsheathe.wav")
    place(out, norm(pitch(draw, 0.85)), 1.0, 0.8)
    # 종 '댕' (대장간 종을 낮게) + 혈월 단조 화음 (C단조: C3 Eb3 G3 + 낮은 C2)
    bell = pitch(k.load("craft"), 0.45)
    place(out, norm(bell), 1.45, 0.9)
    place(out, norm(boom(0.9, 70, 40)), 1.45, 0.55)
    pad = chord([65.4, 130.8, 155.6, 196.0], 1.75, detune=0.006) * env(1.75, 0.15, 2.2)
    place(out, norm(lp(pad, 1500, 1)), 1.45, 0.45)
    # 낮은 한숨 같은 목소리
    voice = pitch(rpg("NPC/ogre/ogre2.wav"), 0.7)
    place(out, lp(norm(voice), 1500, 1), 1.6, 0.35)
    return reverb(fade(out, 0.5), size=0.75, wet=0.35, damp=4000)


def warden():
    """탑의 수문장 (마법사): 바람이 빨려 들어가고 → 번쩍 '파직' → 속삭이는 그림자 목소리 + 신비한 화음."""
    dur = 3.4
    out = np.zeros(int(dur * SR))
    place(out, swell(1.4, 300, 3500, 0.9), 0.0, 0.6)
    spell = rpg("battle/spell.wav")
    place(out, cut(norm(spell), 1.6), 0.2, 0.35)
    # 파직! (번개)
    crack = bandsweep(noise(0.06), 6000, 1200) * env(0.06, 0.0004, 5)
    zap = hp(crackle(0.4, 2200, 9000), 2500) * env(0.4, 0.002, 4)
    place(out, norm(crack), 1.26, 1.0)
    place(out, norm(zap), 1.27, 0.55)
    place(out, norm(boom(1.0, 80, 35)), 1.26, 0.6)
    # 그림자 목소리 둘 겹침 (하나는 낮게)
    s1 = pitch(rpg("NPC/shade/shade6.wav"), 0.8)
    s2 = pitch(rpg("NPC/shade/shade3.wav"), 0.62)
    place(out, norm(s1), 1.4, 0.5)
    place(out, norm(s2), 1.55, 0.45)
    # 반음 섞인 신비한 화음 (E단조 + 증4도)
    pad = chord([164.8, 196.0, 233.1, 329.6], 1.9, detune=0.008) * env(1.9, 0.25, 2.5)
    pad *= 1 + 0.3 * np.sin(2 * np.pi * 5.5 * t(1.9))
    place(out, norm(pad), 1.3, 0.3)
    return reverb(fade(out, 0.5), size=0.9, wet=0.4, damp=6000)


def demonlord():
    """마왕: 깊은 곳에서 '우우웅' 차오름 → 땅이 꺼지는 '콰앙' → 짐승 여럿이 겹친 낮은 포효 + 불길한 화음."""
    dur = 4.0
    out = np.zeros(int(dur * SR))
    # 차오르는 저음과 바람
    rise = sine_sweep(30, 55, 1.4) * np.clip(t(1.4) / 1.4, 0, 1) ** 2
    place(out, norm(rise), 0.0, 0.6)
    place(out, swell(1.4, 150, 1800, 0.95), 0.0, 0.5)
    # 콰앙
    place(out, norm(boom(1.8, 50, 22)), 1.33, 1.0)
    place(out, norm(pitch(k.load("slam"), 0.5)), 1.33, 0.8)
    place(out, norm(pitch(k.load("destroy"), 0.45)), 1.36, 0.5)
    # 포효: 짐승·거인 목소리를 낮게 3겹
    r1 = pitch(rpg("NPC/gutteral beast/mnstr5.wav"), 0.6)
    r2 = pitch(rpg("NPC/giant/giant5.wav"), 0.5)
    r3 = pitch(rpg("NPC/gutteral beast/mnstr14.wav"), 0.75)
    for r, at, g in ((r1, 1.45, 0.8), (r2, 1.5, 0.7), (r3, 1.55, 0.45)):
        place(out, sat(lp(norm(r), 2500, 1), 1.6), at, g)
    # 불길한 화음: 낮은 D 위에 증4도(Ab) — 길게 남음
    pad = chord([36.7, 73.4, 103.8, 146.8], 2.5, detune=0.01) * env(2.5, 0.2, 2.0)
    place(out, norm(lp(pad, 900, 1)), 1.4, 0.5)
    return reverb(fade(sat(out, 1.2), 0.6), size=1.0, wet=0.35, damp=2500)


def warden_b():
    """탑의 수문장 (시안 B, 탑을 지키는 문지기): 탑 꼭대기 종이 '댕-댕' 두 번 → 마법진이 '우우웅' 차오름
    → 번개가 '콰직' 떨어지고 → 불꽃이 회오리치며 '화르르' 퍼짐. 목소리 없이 묵직하게."""
    dur = 3.6
    out = np.zeros(int(dur * SR))
    bell = pitch(k.load("craft"), 0.38)
    place(out, norm(bell), 0.0, 0.8)
    place(out, norm(pitch(k.load("craft"), 0.36)), 0.55, 0.65)
    # 마법진 차오름: 낮은 화음이 떨리며 올라감 (E단조 → 위로 미끄러짐)
    hum = (sine_sweep(82.4, 110, 1.2, 0.01, 6) + 0.6 * sine_sweep(123.5, 165, 1.2, 0.01, 6.5)
           + 0.3 * sine_sweep(247, 330, 1.2, 0.02, 7))
    hum *= np.clip(t(1.2) / 1.2, 0, 1) ** 2
    place(out, norm(hum), 0.4, 0.5)
    place(out, swell(1.2, 400, 4000, 0.97), 0.4, 0.35)
    # 콰직: 날카로운 시작 + 갈라지는 결 + 천둥
    crack = bandsweep(noise(0.05), 7000, 1500) * env(0.05, 0.0003, 5)
    zap = hp(crackle(0.25, 3000, 10000), 3000) * env(0.25, 0.001, 5)
    thunder = lp(noise(1.6), 250) * 8 * k.quake_roll(1.6, 9.0) * env(1.6, 0.01, 2.5)
    place(out, norm(crack), 1.6, 1.0)
    place(out, norm(zap), 1.6, 0.6)
    place(out, norm(pitch(k.load("destroy"), 1.3)), 1.61, 0.5)
    place(out, norm(boom(1.2, 75, 30)), 1.6, 0.8)
    place(out, norm(thunder), 1.65, 0.6)
    # 불꽃 회오리: 아래에서 위로 도는 바람 + 타닥
    fire = bandsweep(noise(1.4), 300, 1600) * env(1.4, 0.15, 2.5)
    fire *= 1 + 0.5 * np.sin(2 * np.pi * 4 * t(1.4))
    place(out, norm(fire), 1.75, 0.45)
    place(out, norm(crackle(1.2, 400, 5000) * env(1.2, 0.05, 3)), 1.8, 0.3)
    return reverb(fade(out, 0.5), size=0.85, wet=0.32, damp=5000)


def warden_c():
    """탑의 수문장 (시안 C, 갑옷 입은 거대한 문지기): 무거운 쇠사슬 '촤르륵' → 쇠문 '쿵' → 창을 땅에 '쾅' 꽂음
    → 낮은 금관 같은 울림 '부우웅' (탑이 울리는 느낌)."""
    dur = 3.4
    out = np.zeros(int(dur * SR))
    mail = rpg("inventory/chainmail2.wav")
    for at, r in ((0.0, 0.7), (0.18, 0.62), (0.36, 0.66)):
        place(out, cut(norm(pitch(mail, r)), 0.35), at, 0.45)
    gate = pitch(k.load("gateClose_LEFT"), 0.7)
    place(out, fade(norm(gate), 0.1), 0.75, 0.8)
    place(out, norm(boom(0.8, 65, 35)), 0.75, 0.5)
    # 창 꽂기: 쇳소리 '챙' + 쾅
    place(out, fade(norm(pitch(k.load("block"), 0.8)), 0.08), 1.45, 0.7)
    place(out, fade(norm(pitch(k.load("slam"), 0.65)), 0.08), 1.45, 0.9)
    place(out, norm(boom(1.4, 55, 26)), 1.45, 0.9)
    place(out, fade(norm(pitch(k.load("destroy"), 0.6)), 0.08), 1.47, 0.35)
    # 탑이 울림: 금관처럼 배음 많은 낮은 화음 (E2 + B2 + E3), 천천히 커졌다 사라짐
    tt = t(1.9)
    horn = np.zeros_like(tt)
    for f, g in ((82.4, 1.0), (123.5, 0.6), (164.8, 0.45)):
        for h in range(1, 7):
            horn += g / h * np.sin(2 * np.pi * f * h * tt * (1 + 0.002 * np.sin(2 * np.pi * 4.5 * tt)))
    horn = lp(horn, 1200, 1) * np.clip(tt / 0.3, 0, 1) * np.exp(-tt * 1.3)
    place(out, norm(horn), 1.5, 0.55)
    return reverb(fade(out, 0.5), size=0.9, wet=0.35, damp=3500)


def formant(x, freqs=(700, 1150, 2800), gains=(1.0, 0.6, 0.25)):
    """사람 목소리 '아' 모음처럼 들리게 고정 대역 필터 3개를 겹친다 (합창 느낌)."""
    return sum(g * bandsweep(x, f, f) for f, g in zip(freqs, gains))


def saw_voices(freqs, dur, detune=0.006, vib_hz=5.0):
    """톱니파 여러 개를 살짝 어긋나게 겹친 소리 (합창·금관 재료)."""
    tt = t(dur)
    out = np.zeros_like(tt)
    for f in freqs:
        for d in (-detune, -detune / 3, detune / 3, detune):
            vib = 1 + 0.004 * np.sin(2 * np.pi * (vib_hz + rng.uniform(-0.7, 0.7)) * tt + rng.uniform(0, 6.28))
            ph = np.cumsum(f * (1 + d) * vib) / SR
            out += 2 * (ph % 1.0) - 1
    return out / (4 * len(freqs))


def brass(freqs, dur):
    tt = t(dur)
    out = np.zeros_like(tt)
    for f in freqs:
        for h in range(1, 9):
            out += (1 / h) * np.sin(2 * np.pi * f * h * tt + rng.uniform(0, 6.28))
    bright = np.clip(tt / 0.08, 0, 1)
    return lp(out, 1800, 1) * bright


def demonlord_b():
    """마왕 (시안 B, 더 웅장하게): 낮은 합창이 깔리고 심장 고동처럼 '쿵…쿵…쿵' 커지다가
    → 땅이 꺼지는 '콰아앙' + 금관 불협화음 + 짐승 포효 3겹 → 합창이 어둡게 길게 남는다. 약 6초."""
    dur = 6.2
    out = np.zeros(int(dur * SR))
    hit = 2.3  # 등장 연출에서 보스가 내리치는 때 (GameSession.INTRO_STRIKE_AT)
    # 1) 낮은 합창 (D단조 D2 A2 D3 F3), 점점 커짐
    ch1 = formant(saw_voices([73.4, 110.0, 146.8, 174.6], hit + 0.1))  # D단조
    ch1 *= np.clip(t(hit + 0.1) / hit, 0, 1) ** 1.6
    place(out, norm(ch1), 0.0, 0.22)
    drone = sine_sweep(36.7, 36.7, hit + 0.1) * np.clip(t(hit + 0.1) / hit, 0, 1)
    place(out, norm(drone), 0.0, 0.2)
    # 2) 심장 고동 세 번, 점점 크게
    for at, g in ((0.45, 0.4), (1.1, 0.55), (1.7, 0.7)):
        place(out, boom(0.6, 70, 38), at, g)
        place(out, boom(0.5, 70, 38), at + 0.17, g * 0.6)
    place(out, swell(0.8, 200, 5000, 0.98), hit - 0.78, 0.35)
    # 3) 콰아앙
    place(out, boom(2.4, 48, 20), hit, 1.0)
    place(out, fade(norm(pitch(k.load("slam"), 0.45)), 0.1), hit, 0.85)
    place(out, fade(norm(pitch(k.load("destroy"), 0.4)), 0.1), hit + 0.02, 0.55)
    crash = hp(noise(2.0), 3000) * env(2.0, 0.003, 4)
    place(out, norm(crash), hit, 0.3)
    # 금관 불협화음 (D2 + Ab2 + D3 + F3), 짧게 찌르고 길게 끌기
    br = brass([73.4, 103.8, 146.8, 174.6], 2.6) * (0.35 + 0.65 * np.exp(-t(2.6) * 4)) * np.exp(-t(2.6) * 0.8)
    place(out, sat(norm(br), 1.8), hit, 0.55)
    # 포효 3겹 (낮게)
    for r, rr, at, g in (("NPC/gutteral beast/mnstr5.wav", 0.55, hit + 0.08, 0.75),
                         ("NPC/giant/giant5.wav", 0.45, hit + 0.12, 0.7),
                         ("NPC/gutteral beast/mnstr14.wav", 0.68, hit + 0.2, 0.45)):
        place(out, sat(lp(norm(pitch(rpg(r), rr)), 2200, 1), 1.6), at, g)
    # 4) 어두운 합창이 남음 (D단조 위 반음 Eb 섞어 불길하게)
    tail = dur - hit
    ch2 = formant(saw_voices([73.4, 110.0, 155.6, 174.6, 220.0], tail), (650, 1080, 2650))
    ch2 *= np.clip(t(tail) / 0.4, 0, 1) * np.exp(-t(tail) * 0.75)
    place(out, norm(ch2), hit + 0.05, 0.5)
    return reverb(fade(sat(out, 1.15), 0.8), size=1.0, wet=0.38, damp=3000)


def oga(name):
    return rpg(os.path.join("..", "oga", name))


def demonlord_c():
    """마왕 (시안 C, 웃는 마왕): 낮은 합창·심장 고동 밑으로 '흐흐흐' 낮은 웃음이 새어 나오다가
    → 2.3초에 '콰아앙' + 금관 불협화음 → 크게 '하하하하' 웃음 (낮게 내리고 한 옥타브 아래 목소리를 겹쳐 괴물 같은 두 목소리)
    → 어두운 합창이 남는다. 짐승 포효는 쓰지 않는다."""
    dur = 6.0
    out = np.zeros(int(dur * SR))
    hit = 2.3  # 등장 연출에서 보스가 내리치는 때 (GameSession.INTRO_STRIKE_AT)
    ch1 = formant(saw_voices([73.4, 110.0, 146.8, 174.6], hit + 0.1))
    ch1 *= np.clip(t(hit + 0.1) / hit, 0, 1) ** 1.6
    place(out, norm(ch1), 0.0, 0.18)
    for at, g in ((0.45, 0.35), (1.1, 0.5), (1.7, 0.65)):
        place(out, boom(0.6, 70, 38), at, g)
        place(out, boom(0.5, 70, 38), at + 0.17, g * 0.6)
    # 낮은 킥킥 웃음 (점점 커지는 녹음의 앞부분을 낮게)
    chuckle = pitch(oga("laugh_evil_1_antumdeluge.wav"), 0.72)
    chuckle = fade(chuckle[:int(2.1 * SR)], 0.25) * np.clip(t(2.1) / 2.1, 0.25, 1)[:len(chuckle[:int(2.1 * SR)])]
    place(out, lp(norm(chuckle), 3500, 1), 0.15, 0.4)
    place(out, swell(0.8, 200, 5000, 0.98), hit - 0.78, 0.3)
    # 콰아앙
    place(out, boom(2.2, 48, 20), hit, 1.0)
    place(out, fade(norm(pitch(k.load("slam"), 0.45)), 0.1), hit, 0.8)
    place(out, fade(norm(pitch(k.load("destroy"), 0.4)), 0.1), hit + 0.02, 0.45)
    br = brass([73.4, 103.8, 146.8, 174.6], 2.4) * (0.35 + 0.65 * np.exp(-t(2.4) * 4)) * np.exp(-t(2.4) * 1.0)
    place(out, sat(norm(br), 1.8), hit, 0.4)
    # 크게 웃음: 녹음 앞 2초 (하하하하 부분)를 낮게 + 한 옥타브 아래 겹침
    laugh = oga("evil_laughter_qubodup.wav")[:int(2.0 * SR)]
    l1 = fade(pitch(laugh, 0.78), 0.3)
    l2 = fade(pitch(laugh, 0.52), 0.3)
    place(out, sat(norm(l1), 1.3), hit + 0.15, 0.85)
    place(out, lp(norm(l2), 2500, 1), hit + 0.15, 0.4)
    tail = dur - hit
    ch2 = formant(saw_voices([73.4, 110.0, 155.6, 174.6, 220.0], tail), (650, 1080, 2650))
    ch2 *= np.clip(t(tail) / 0.4, 0, 1) * np.exp(-t(tail) * 0.8)
    place(out, norm(ch2), hit + 0.05, 0.3)
    return reverb(fade(out, 0.8), size=1.0, wet=0.35, damp=3500)


def warden_d():
    """탑의 수문장 (시안 D, 합성 화음 없이 녹음 위주로 깔끔하게): 무거운 발걸음 '쿵·쿵'
    → 지팡이에 마법을 모으는 '우우웅' (녹음 마법 소리) → 번개 '콰직' + 천둥 '우르릉'."""
    dur = 3.4
    out = np.zeros(int(dur * SR))
    for at, g in ((0.0, 0.7), (0.5, 0.85)):
        place(out, fade(norm(pitch(k.load("slam"), 0.55)), 0.08), at, g)
        place(out, fade(norm(pitch(k.load("block"), 0.6)), 0.08), at + 0.01, 0.25)
    spell = rpg("battle/spell.wav")
    charge = fade(spell[:int(1.3 * SR)], 0.15) * np.clip(t(1.3) / 1.3, 0.2, 1) ** 1.5
    place(out, norm(charge), 0.75, 0.6)
    place(out, fade(norm(pitch(rpg("battle/magic1.wav"), 0.8)), 0.1), 1.25, 0.45)
    hit = 2.05
    crack = bandsweep(noise(0.04), 7000, 1500) * env(0.04, 0.0003, 5)
    place(out, norm(crack), hit, 0.9)
    place(out, fade(norm(pitch(k.load("destroy"), 1.35)), 0.06), hit, 0.75)
    place(out, boom(1.0, 80, 32), hit, 0.75)
    thunder = lp(noise(1.3), 300) * 8 * k.quake_roll(1.3, 8.0) * env(1.3, 0.02, 2.5)
    place(out, fade(norm(thunder), 0.4), hit + 0.05, 0.55)
    return reverb(fade(out, 0.3), size=0.8, wet=0.3, damp=5000)


def low_laugh(src, ratio, dur=None):
    """웃음 녹음을 낮게: 원래 높이 + 한 옥타브 아래를 겹치고 높은 쪽을 깎아 굵은 저음으로."""
    x = oga(src)
    if dur:
        x = x[:int(dur * SR)]
    a = pitch(x, ratio)
    b = pitch(x, ratio / 2)[:len(a)]
    b = np.pad(b, (0, len(a) - len(b)))
    v = lp(norm(a), 2200, 1) * 0.75 + lp(norm(b), 1200, 1) * 0.6
    v += 0.25 * lp(np.abs(v), 120, 2) * sine_sweep(45, 45, len(v) / SR)[:len(v)]
    return fade(hp(sat(norm(v), 1.4), 30), 0.3)


def warden_e():
    """탑의 수문장 (시안 E): 무거운 발걸음 '쿵·쿵' → 마법 모으는 소리 대신 낮은 저음 웃음 '흐흐흐'
    → 번개 '콰직' + 천둥 '우르릉'."""
    dur = 3.6
    out = np.zeros(int(dur * SR))
    for at, g in ((0.0, 0.7), (0.5, 0.85)):
        place(out, fade(norm(pitch(k.load("slam"), 0.55)), 0.08), at, g)
        place(out, fade(norm(pitch(k.load("block"), 0.6)), 0.08), at + 0.01, 0.25)
    laugh = low_laugh("laugh_evil_1_antumdeluge.wav", 0.62)
    laugh = laugh[int(0.6 * SR):int(2.9 * SR)]  # 커지는 가운데 부분
    laugh = fade(laugh, 0.3) * np.clip(np.arange(len(laugh)) / (0.05 * SR), 0, 1)
    place(out, laugh, 0.8, 0.8)
    hit = 2.2
    crack = bandsweep(noise(0.04), 7000, 1500) * env(0.04, 0.0003, 5)
    place(out, norm(crack), hit, 0.9)
    place(out, fade(norm(pitch(k.load("destroy"), 1.35)), 0.06), hit, 0.75)
    place(out, boom(1.0, 80, 32), hit, 0.75)
    thunder = lp(noise(1.3), 300) * 8 * k.quake_roll(1.3, 8.0) * env(1.3, 0.02, 2.5)
    place(out, fade(norm(thunder), 0.4), hit + 0.05, 0.55)
    return reverb(fade(out, 0.3), size=0.8, wet=0.3, damp=5000)


def demonlord_d():
    """마왕 (시안 D): C 와 같고 웃음만 더 낮은 저음으로 (0.62배 + 한 옥타브 아래, 높은 쪽 깎음)."""
    dur = 6.4
    out = np.zeros(int(dur * SR))
    hit = 2.3
    ch1 = formant(saw_voices([73.4, 110.0, 146.8, 174.6], hit + 0.1))
    ch1 *= np.clip(t(hit + 0.1) / hit, 0, 1) ** 1.6
    place(out, norm(ch1), 0.0, 0.18)
    for at, g in ((0.45, 0.35), (1.1, 0.5), (1.7, 0.65)):
        place(out, boom(0.6, 70, 38), at, g)
        place(out, boom(0.5, 70, 38), at + 0.17, g * 0.6)
    chuckle = low_laugh("laugh_evil_1_antumdeluge.wav", 0.6)[:int(2.1 * SR)]
    chuckle = fade(chuckle, 0.25) * np.clip(np.arange(len(chuckle)) / (2.1 * SR), 0.25, 1)
    place(out, chuckle, 0.15, 0.4)
    place(out, swell(0.8, 200, 5000, 0.98), hit - 0.78, 0.3)
    place(out, boom(2.2, 48, 20), hit, 1.0)
    place(out, fade(norm(pitch(k.load("slam"), 0.45)), 0.1), hit, 0.8)
    place(out, fade(norm(pitch(k.load("destroy"), 0.4)), 0.1), hit + 0.02, 0.45)
    br = brass([73.4, 103.8, 146.8, 174.6], 2.4) * (0.35 + 0.65 * np.exp(-t(2.4) * 4)) * np.exp(-t(2.4) * 1.0)
    place(out, sat(norm(br), 1.8), hit, 0.35)
    place(out, low_laugh("evil_laughter_qubodup.wav", 0.62, 2.0), hit + 0.15, 1.0)
    tail = dur - hit
    ch2 = formant(saw_voices([73.4, 110.0, 155.6, 174.6, 220.0], tail), (650, 1080, 2650))
    ch2 *= np.clip(t(tail) / 0.4, 0, 1) * np.exp(-t(tail) * 0.8)
    place(out, norm(ch2), hit + 0.05, 0.25)
    return reverb(fade(out, 0.8), size=1.0, wet=0.35, damp=3000)


def lightning(out, at, g=1.0):
    """번개 '콰광' 한 번: 날카로운 '콰직' + 깨지는 소리 + 쿵 + 천둥 꼬리."""
    crack = bandsweep(noise(0.04), 7000, 1500) * env(0.04, 0.0003, 5)
    place(out, norm(crack), at, 0.9 * g)
    place(out, fade(norm(pitch(k.load("destroy"), 1.35)), 0.06), at, 0.75 * g)
    place(out, boom(1.0, 80, 32), at, 0.75 * g)
    thunder = lp(noise(1.3), 300) * 8 * k.quake_roll(1.3, 8.0) * env(1.3, 0.02, 2.5)
    place(out, fade(norm(thunder), 0.4), at + 0.05, 0.55 * g)


def warden_f():
    """탑의 수문장 (시안 F): 무거운 발걸음 '쿵·쿵' → 번개 '콰광' → 더 큰 번개 '콰광'. 웃음·마법 모으기 없음."""
    dur = 3.4
    out = np.zeros(int(dur * SR))
    for at, g in ((0.0, 0.7), (0.5, 0.85)):
        place(out, fade(norm(pitch(k.load("slam"), 0.55)), 0.08), at, g)
        place(out, fade(norm(pitch(k.load("block"), 0.6)), 0.08), at + 0.01, 0.25)
    lightning(out, 1.15, 0.8)
    lightning(out, 2.05, 1.0)
    return reverb(fade(out, 0.3), size=0.8, wet=0.3, damp=5000)


def warden_h():
    """탑의 수문장 (시안 7f 에서 발걸음 '쿵쿵'을 뺌, 지용 11/27 밤): 번개 '콰광' → 더 큰 번개 '콰광'."""
    dur = 2.4
    out = np.zeros(int(dur * SR))
    lightning(out, 0.02, 0.8)
    lightning(out, 0.92, 1.0)
    return reverb(fade(out, 0.3), size=0.8, wet=0.3, damp=5000)


def demonlord_e():
    """마왕 (시안 E): 합창·심장 고동 → 2.3초 '콰아앙' → 수문장 시안 E 에 있던 낮은 저음 웃음 '흐흐흐하하'
    (조금 더 낮게, 길게) → 어두운 합창."""
    dur = 6.2
    out = np.zeros(int(dur * SR))
    hit = 2.3
    ch1 = formant(saw_voices([73.4, 110.0, 146.8, 174.6], hit + 0.1))
    ch1 *= np.clip(t(hit + 0.1) / hit, 0, 1) ** 1.6
    place(out, norm(ch1), 0.0, 0.2)
    for at, g in ((0.45, 0.4), (1.1, 0.55), (1.7, 0.7)):
        place(out, boom(0.6, 70, 38), at, g)
        place(out, boom(0.5, 70, 38), at + 0.17, g * 0.6)
    place(out, swell(0.8, 200, 5000, 0.98), hit - 0.78, 0.3)
    place(out, boom(2.2, 48, 20), hit, 1.0)
    place(out, fade(norm(pitch(k.load("slam"), 0.45)), 0.1), hit, 0.8)
    place(out, fade(norm(pitch(k.load("destroy"), 0.4)), 0.1), hit + 0.02, 0.45)
    br = brass([73.4, 103.8, 146.8, 174.6], 2.4) * (0.35 + 0.65 * np.exp(-t(2.4) * 4)) * np.exp(-t(2.4) * 1.0)
    place(out, sat(norm(br), 1.8), hit, 0.35)
    laugh = low_laugh("laugh_evil_1_antumdeluge.wav", 0.58)
    laugh = laugh[int(0.6 * SR):int(3.4 * SR)]
    laugh = fade(laugh, 0.4) * np.clip(np.arange(len(laugh)) / (0.05 * SR), 0, 1)
    place(out, laugh, hit + 0.2, 1.0)
    tail = dur - hit
    ch2 = formant(saw_voices([73.4, 110.0, 155.6, 174.6, 220.0], tail), (650, 1080, 2650))
    ch2 *= np.clip(t(tail) / 0.4, 0, 1) * np.exp(-t(tail) * 0.8)
    place(out, norm(ch2), hit + 0.05, 0.22)
    return reverb(fade(out, 0.8), size=1.0, wet=0.35, damp=3000)


def stomp(out, at, g=1.0):
    """무거운 발걸음 '쿵': 쇳소리 없이 낮은 울림 + 둔탁한 흙먼지 + 돌바닥 자갈 조금."""
    place(out, boom(0.7, 65, 32), at, 0.9 * g)
    thud = lp(noise(0.12), 180, 2) * env(0.12, 0.002, 4)
    place(out, norm(thud), at, 0.6 * g)
    grit = lp(fade(norm(pitch(k.load("destroy"), 0.5)), 0.08), 1200, 1)
    place(out, norm(grit), at + 0.01, 0.18 * g)


def warden_g():
    """탑의 수문장 (시안 G): F 와 같고 발걸음만 바꿈 (병 깨지는 듯한 쇳소리 빼고 낮고 둔탁하게)."""
    dur = 3.4
    out = np.zeros(int(dur * SR))
    stomp(out, 0.0, 0.8)
    stomp(out, 0.5, 1.0)
    lightning(out, 1.15, 0.8)
    lightning(out, 2.05, 1.0)
    return reverb(fade(out, 0.3), size=0.8, wet=0.3, damp=5000)


# ── 보스 힘 모으기 (예전엔 몹과 같은 가는 'cast' 소리, 지용: 힘 빠지는 느낌) ─────────
# 보스가 공격하기 전 0.6~1초쯤 힘을 모을 때. 끝이 풀리지 않고 '터지기 직전'에서 멈춰야 한다.

def charge_a():
    """'우우웅↑': 낮은 울림이 올라가고 주변 공기가 빨려 들어가다가 꼭대기에서 '칭' 하고 멈춤."""
    dur = 0.75
    out = np.zeros(int(0.85 * SR))
    rise = t(dur) / dur
    sub = sine_sweep(45, 110, dur) * rise ** 1.5
    mid = sine_sweep(90, 220, dur, 0.02, 9) * rise ** 2
    place(out, fade(norm(sub), 0.015), 0, 0.8)
    place(out, fade(norm(lp(mid, 900, 1)), 0.015), 0, 0.35)
    place(out, swell(dur, 250, 3500, 0.98), 0, 0.5)
    ting = hp(rpg("battle/sword-unsheathe2.wav"), 3000)
    place(out, fade(norm(ting[:int(0.12 * SR)]), 0.08), dur - 0.03, 0.25)
    return fade(out, 0.05)


def charge_b():
    """역재생 '쉬이익-': 큰 쿵 소리를 거꾸로 틀어 점점 다가오는 느낌 (터지기 직전에 뚝)."""
    slam = pitch(k.load("slam"), 0.6)
    wet = reverb(np.concatenate([slam, np.zeros(int(0.3 * SR))]), size=0.9, wet=0.6, damp=4000)
    rev = wet[::-1]
    rev = rev[-int(0.8 * SR):]
    air = swell(0.8, 300, 4000, 1.0)
    out = norm(rev) + 0.35 * air
    out *= np.clip(np.arange(len(out)) / (0.15 * SR), 0, 1)
    return fade(out, 0.02)


def charge_c():
    """'그르르르↑': 낮게 으르렁거리며 힘을 주는 소리 + 땅이 떨림 (몸이 무거운 보스 느낌)."""
    dur = 0.8
    growl = pitch(rpg("NPC/ogre/ogre2.wav"), 0.55)[:int(dur * SR)]
    growl = np.pad(growl, (0, max(0, int(dur * SR) - len(growl))))
    growl = lp(norm(growl), 1500, 1) * (0.4 + 0.6 * t(dur) / dur)
    rumble = lp(noise(dur), 160) * 8 * (t(dur) / dur) ** 1.2 * (1 + 0.6 * np.sin(2 * np.pi * 14 * t(dur)))
    sub = sine_sweep(40, 70, dur) * (t(dur) / dur)
    out = norm(growl) * 0.8 + norm(rumble) * 0.5 + norm(sub) * 0.6
    out *= np.clip(np.arange(len(out)) / (0.06 * SR), 0, 1)
    return fade(sat(out, 1.5), 0.05)


# ── 탑의 수문장 패턴 소리 (지용 11/27 밤) ─────────────────────────────

def bolt_strike():
    """번개 내려치기 한 방 '콰광': 번개 장판이 터지는 순간마다 난다 (0.28초 간격으로 여러 번 → 꼬리를 짧게)."""
    out = np.zeros(int(0.75 * SR))
    crack = bandsweep(noise(0.04), 7000, 1500) * env(0.04, 0.0003, 5)
    place(out, norm(crack), 0, 0.9)
    place(out, fade(norm(pitch(k.load("destroy"), 1.35)), 0.06), 0, 0.7)
    place(out, boom(0.5, 85, 40), 0, 0.7)
    thunder = lp(noise(0.6), 320) * 8 * env(0.6, 0.01, 3.5)
    place(out, fade(norm(thunder), 0.25), 0.03, 0.4)
    return fade(out, 0.1)


def bolt_strike_real():
    """번개 내려치기 한 방, 실제 녹음 (지용 11/27 고름 'B 짧게'): WuxiaScrub 'Rain + Long Thunder'(OGA CC0) 의 벼락
    '빠직·콰광' 부분 0.9초. 0.3초부터 우르릉 꼬리를 빠르게 줄여 0.28초 간격으로 여러 번 나도 뭉개지지 않게."""
    x = oga("thunder_rain_wuxiascrub.wav")[:int(0.9 * SR)].copy()
    x[:220] *= np.linspace(0, 1, 220)
    f0 = int(0.3 * SR)
    x[f0:] *= np.linspace(1, 0, len(x) - f0) ** 2.5
    return x


def warden_intro_real():
    """탑의 수문장 등장, 실제 벼락 녹음 (지용 11/27 고름 'A 긴 것'): InspectorJ 'Thunder, Very Close, Rain, 01'
    (OpenGameArt CC-BY 3.0, 크레딧 필요) 의 가까운 벼락 '빠지직·콰광' + 우르릉 3초. 1초부터 줄인다."""
    x = oga("thunder_close_inspectorj.wav").copy()
    x[:220] *= np.linspace(0, 1, 220)
    f0 = int(1.0 * SR)
    x[f0:] *= np.linspace(1, 0, len(x) - f0) ** 2
    return x


def orb_shot():
    """마법 구슬 한 발 '퓽': 회전하며 쏘는 불 구슬마다 (짧고 가볍게, 여러 번 나도 시끄럽지 않게)."""
    dur = 0.16
    zap = sine_sweep(1500, 520, dur) * env(dur, 0.002, 4)
    body = sine_sweep(380, 200, dur) * env(dur, 0.002, 5)
    air = bandsweep(noise(dur), 2500, 900) * env(dur, 0.003, 4)
    out = norm(zap) * 0.55 + norm(body) * 0.45 + norm(air) * 0.3
    return fade(out, 0.04)


def firespin_start():
    """불 구슬 회전 시작 '후우웅': 회오리 바람이 돌며 커지고 구슬이 '화륵' 붙는 소리 (예전 포효 대신)."""
    dur = 0.7
    swirl = bandsweep(noise(dur), 400, 2200) * (t(dur) / dur) ** 1.2 * (1 + 0.6 * np.sin(2 * np.pi * 7 * t(dur)))
    hum = sine_sweep(110, 220, dur, 0.02, 7) * (t(dur) / dur)
    ign = crackle(0.35, 700, 6000) * env(0.35, 0.01, 3)
    out = np.zeros(int(0.95 * SR))
    place(out, norm(swirl), 0, 0.7)
    place(out, fade(norm(hum), 0.05), 0, 0.4)
    place(out, norm(ign), 0.55, 0.45)
    place(out, norm(orb_shot()), 0.62, 0.5)
    return fade(out, 0.08)


SOUNDS = [
    ("bs_bolt_strike", bolt_strike, -9), ("bs_orb_shot", orb_shot, -12), ("bs_firespin_start", firespin_start, -10),
    ("boss_charge_a", charge_a, -12), ("boss_charge_b", charge_b, -12), ("boss_charge_c", charge_c, -12),
    # (파일 이름, 함수, 크기 dB)  크기는 Kenney 녹음 기준(-12)과 맞춘다. 보스 등장은 조금 크게.
    ("hero_swing_a", swing_a, -12), ("hero_swing_b", swing_b, -12),
    ("hero_hit_a", hit_a, -12), ("hero_hit_b", hit_b, -12),
    ("hero_hurt", hurt, -12),
    ("hero_roll_a", roll_a, -12), ("hero_roll_b", roll_b, -12),
    ("intro_golem", golem, -9), ("intro_knight", knight, -9),
    ("intro_warden", warden, -9), ("intro_warden_b", warden_b, -9), ("intro_warden_c", warden_c, -9), ("intro_demonlord", demonlord, -9), ("intro_demonlord_b", demonlord_b, -9), ("intro_demonlord_c", demonlord_c, -9), ("intro_warden_d", warden_d, -9), ("intro_warden_e", warden_e, -9), ("intro_demonlord_d", demonlord_d, -9), ("intro_warden_f", warden_f, -9), ("intro_warden_g", warden_g, -9), ("intro_demonlord_e", demonlord_e, -9),
]

# 게임에 넣은 것 (2026-11-27 밤 지용 결정). 보스 등장·준비·타격음은 해성이 tools/make_boss_intro_sfx.py (bs_*) 로
# 정리했고, 그중 탑의 수문장 등장 소리만 이 도구의 번개 두 번으로 바꾼다. 시안 8e 는 마왕이 2페이즈로 변할 때 쓴다.
# bs_ 파일은 SfxMix.SKILL_FILE_DB(-7dB) 로 잰다.
GAME_LEVEL_DB = -7
GAME = [
    ("bs_intro_warden_bolts", warden_intro_real),  # 탑의 수문장 등장: 실제 벼락 녹음 3초 (합성 번개 두 번 warden_h 대신)
    ("bs_demonlord_phase2", demonlord_e),    # 마왕 변신: 합창·심장 고동 → 콰아앙 → 낮은 저음 웃음
    ("bs_bolt_strike", bolt_strike_real),    # 탑의 수문장 번개 장판이 터질 때 (hz_bolt), 실제 벼락 녹음 (합성 bolt_strike 대신)
    # bs_orb_shot.wav (수문장 구슬)은 이 도구로 만들지 않는다: 지용이 고른 마왕 쏘기 소리(shot_demonlord,
    # tools/make_boss_charge_sfx.py)를 -7dB 로 맞춰 넣었다. orb_shot·firespin_start 는 시안으로만 남김.
]

if __name__ == "__main__":
    # python tools/make_combat_sfx.py            → 게임 파일(GAME)을 assets/audio/sfx 에 쓴다
    # python tools/make_combat_sfx.py 폴더 [이름…] → 모든 시안을 그 폴더에 쓴다 (미리듣기용)
    if len(sys.argv) == 1:
        sfx = os.path.join(ROOT, "assets", "audio", "sfx")
        for name, fn in GAME:
            finish(fn(), os.path.join(sfx, name + ".wav"), GAME_LEVEL_DB)
        sys.exit(0)
    out_dir = sys.argv[1]
    only = sys.argv[2:]
    os.makedirs(out_dir, exist_ok=True)
    for name, fn, db in SOUNDS:
        if only and name not in only:
            continue
        finish(fn(), os.path.join(out_dir, name + ".wav"), db)

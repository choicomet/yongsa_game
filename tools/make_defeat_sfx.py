"""용사 패배 효과음 assets/audio/sfx/hero_defeat.wav 를 만든다 (지용 2026-11-28, 시안 '직접1 무너지는 용사').

    python tools/make_defeat_sfx.py            # hero_defeat.wav 굽기
    python tools/make_defeat_sfx.py --draft B  # 시안 B(꺼지는 불씨)도 build/defeat_B.wav 로

재료: tools/sfx_src 의 CC0 녹음(Kenney Impact·metal, artisticdude RPG Sound Pack) + 합성 현악. ffmpeg·numpy 필요.
"""
import os, sys
import numpy as np
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, os.path.join(ROOT, "tools"))
import make_skill_sfx as k
from make_skill_sfx import SR, t, env, noise, onepole_lp, hp, place, reverb, sat, pitch

def src(rel):
    import subprocess
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", os.path.join(ROOT, "tools", "sfx_src", rel), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    x = np.frombuffer(raw, np.float32).astype(np.float64)
    return x / (np.max(np.abs(x)) + 1e-9)

def tone(f, dur, harm=(1, .5, .25, .12), att=0.25, decay=1.2, vib=0.0):
    tt = t(dur); out = np.zeros_like(tt)
    for i, a in enumerate(harm, 1):
        for d in (-0.003, 0, 0.003):
            out += a * np.sin(2*np.pi*f*i*(1+d)*tt + (vib*np.sin(2*np.pi*5*tt) if vib else 0))
    e = np.clip(tt/att, 0, 1) * np.exp(-decay*tt/dur) * np.clip((dur-tt)/(0.45*dur), 0, 1)
    return out * e

def strings(freqs, dur, att=0.35):
    out = sum(tone(f, dur, att=att, decay=1.0, vib=0.004*f/100) for f in freqs)
    return onepole_lp(out, 2200)

def pluck(f, dur=1.6):
    tt = t(dur)
    x = sum(a*np.sin(2*np.pi*f*i*tt) for i, a in ((1,1),(2,.45),(3,.2),(4,.08)))
    return x * np.exp(-3.2*tt) * np.clip(tt/0.004,0,1)

def thud(dur=0.6, f0=90, f1=38):
    tt = t(dur); f = f0*(f1/f0)**(tt/dur)
    return np.sin(2*np.pi*np.cumsum(f)/SR) * np.exp(-7*tt)

def norm_out(x, path, rms_db=-14.0):
    nz = np.nonzero(np.abs(x) > 3e-4*np.max(np.abs(x)))[0]; x = x[nz[0]:nz[-1]+1]
    f = int(0.08*SR); x[-f:] *= np.linspace(1, 0, f)
    w = SR//20
    best = max(np.sqrt(np.mean(x[i:i+w]**2)) for i in range(0, len(x)-w, w//4))
    x = x * 10**(rms_db/20)/best
    p = np.max(np.abs(x))
    if p > 0.89: x = np.tanh(x/p*1.2)/np.tanh(1.2)*0.89
    k.write_wav(path, x); print(path, f"{len(x)/SR:.2f}s")

D3, F3, A3, Bb2, D2, A2 = 146.83, 174.61, 220.0, 116.54, 73.42, 110.0

def collapse():
    """A 무너지는 용사: 몸이 쓰러지는 쿵 + 갑옷 철컥 + 칼이 돌바닥에 떨어짐 → 낮은 현악 두 화음."""
    out = np.zeros(int(4.6*SR))
    body = pitch(src("beam/impact_punch_heavy_002.wav"), 0.72)
    place(out, body, 0.00, 0.9); place(out, thud(0.7), 0.00, 0.8)
    place(out, src("rpg/inventory/chainmail1.wav"), 0.06, 0.35)
    clank = pitch(src("beam/metal_02.wav"), 0.9)
    place(out, clank, 0.34, 0.55); place(out, pitch(src("beam/metal_02.wav"), 1.12), 0.47, 0.28)
    ring = src("rpg/inventory/metal-ringing.wav"); place(out, ring[:int(0.9*SR)]*np.linspace(1,0,int(0.9*SR))[:len(ring[:int(0.9*SR)])], 0.36, 0.18)
    mus = np.zeros_like(out)
    place(mus, strings([D3, F3, A3, D2], 1.7), 0.45, 0.55)
    place(mus, strings([Bb2, D3, F3], 1.2), 1.55, 0.5)
    place(mus, strings([A2, D3, A3, D2], 2.4), 2.35, 0.5)   # 열린 5도로 끝: 끝나지 않은 느낌
    r = reverb(mus, size=0.8, wet=0.45); out = np.concatenate([out, np.zeros(len(r)-len(out))]) + r
    return reverb(out, size=0.5, wet=0.18)

def ember():
    """B 꺼지는 불씨: 느려지는 심장 두 번 → 낮은 종 한 번 → 내려가는 단조 뜯는 소리."""
    out = np.zeros(int(5.0*SR))
    def beat(at, g):
        place(out, thud(0.25, 70, 40), at, g); place(out, thud(0.22, 60, 36), at+0.17, g*0.7)
    beat(0.0, 0.9); beat(0.85, 0.65)
    bell = pitch(src("beam/bell_02.wav"), 0.5)
    place(out, bell, 1.75, 0.6); place(out, thud(1.2, 55, 40), 1.75, 0.35)
    mus = np.zeros_like(out)
    for i, f in enumerate([440.0, 349.23, 293.66, 220.0]):
        place(mus, pluck(f), 2.05 + i*0.32, 0.38)
    place(mus, strings([D2, A2, D3], 2.6, att=0.6), 2.1, 0.32)
    r = reverb(mus, size=0.9, wet=0.5); out = np.concatenate([out, np.zeros(len(r)-len(out))]) + r
    return reverb(out, size=0.6, wet=0.2)

if __name__ == "__main__":
    # 게임 파일은 가장 큰 50ms RMS -12dB 로 굽는다 (SfxMix.FILE_REFERENCE_DB)
    norm_out(collapse(), os.path.join(ROOT, "assets", "audio", "sfx", "hero_defeat.wav"), rms_db=-12.0)
    if "--draft" in sys.argv:
        os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
        norm_out(ember(), os.path.join(ROOT, "build", "defeat_B.wav"), rms_db=-12.0)

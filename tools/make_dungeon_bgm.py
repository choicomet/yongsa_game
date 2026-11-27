"""던전 세 곳의 탐험 배경음악을 코드로 작곡·합성한다 (assets/audio/bgm/dungeon_*.ogg).

- dungeon_mine.ogg    무너진 갱도 (LEFT)  : D단조 84BPM 4/4. 피치카토 발걸음, 바순 같은 낮은 주제,
                                          곡괭이 소리, 약한 큰북, 물방울, 가끔 박쥐 날갯짓 같은 피리.
- dungeon_citadel.ogg 저주받은 성채 (RIGHT): C단조 100BPM 4/4. 하프시코드 반복 음형, 낮은 현,
                                          멀리서 허밍하는 합창, 호른 주제, 약음 스네어 행진, 가끔 큰 종.
- dungeon_tower.ogg   마왕의 탑 (TOP)     : E단조 112BPM 4/4. 바람 같은 트레몰로 현, 하프·첼레스타 아르페지오,
                                          클라리넷 주제, 낮은 현 박동, 팀파니 롤, 먼 천둥, 유리 종.

각 던전 곡은 그 던전 보스곡과 같은 조로 맞췄다 (보스방으로 넘어갈 때 덜 튄다).
첫 던전에서 마지막 던전으로 갈수록 템포와 밀도가 올라간다.
캠프 곡과 같은 방식으로 세 바퀴를 이어 만든 뒤 가운데만 잘라 써서 반복 이음매가 끊기지 않는다.

실행: python tools/make_dungeon_bgm.py [미리듣기 wav 폴더]   (numpy, ffmpeg 필요)
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_camp_bgm as C  # noqa: E402
import make_village_bgm as V  # noqa: E402

SR = C.SR
rng = C.rng
n, hz, place = C.n, C.midi_hz, C.place


# ---------- 악기 ----------

def pluck(freq, dur, bright=0.5, damp=0.996):
    """Karplus-Strong을 한 주기씩 벡터로 계산하는 빠른 뜯는 소리."""
    nsamp = int(dur * SR)
    period = max(2, int(SR / freq))
    buf = C.lowpass_fft(rng.uniform(-1, 1, period * 4), 1000 + 4000 * bright)[:period]
    reps = nsamp // period + 1
    out = np.empty(reps * period)
    cur = buf
    for k in range(reps):
        out[k * period:(k + 1) * period] = cur
        cur = damp * 0.5 * (cur + np.roll(cur, -1))
    out = out[:nsamp]
    t = np.arange(nsamp) / SR
    return out * np.minimum(1, t / 0.002) / (np.max(np.abs(out)) + 1e-9)


def pizz(freq, dur=0.6):
    s = pluck(freq, dur, 0.25, 0.99)
    t = np.arange(len(s)) / SR
    return s * np.exp(-t * 7)


def harpsichord(freq, dur=1.2):
    return pluck(freq, dur, 0.9, 0.995) + 0.5 * pluck(freq * 2, dur, 0.9, 0.993)


def reed(freq, dur, cutoff=900, duty=0.3, vib=0.004):
    """바순·클라리넷 같은 리드 소리 (펄스파를 거른다)."""
    nsamp = int((dur + 0.25) * SR)
    t = np.arange(nsamp) / SR
    v = 1 + vib * np.sin(2 * np.pi * 5 * t) * np.clip((t - 0.2) / 0.3, 0, 1)
    ph = np.cumsum(freq * v / SR) % 1.0
    s = np.where(ph < duty, 1.0, -duty / (1 - duty))
    s = C.lowpass_fft(s - s.mean(), cutoff, 2)
    return s * C.adsr(nsamp, 0.06, 0.22)


def horn(freq, dur):
    """호른: 부드럽게 부풀었다 가라앉는 낮게 거른 톱니파."""
    nsamp = int((dur + 0.3) * SR)
    t = np.arange(nsamp) / SR
    v = 1 + 0.003 * np.sin(2 * np.pi * 4.8 * t)
    ph = np.cumsum(freq * v / SR) % 1.0
    s = C.lowpass_fft(2 * ph - 1, 700, 2) + 0.4 * np.sin(2 * np.pi * np.cumsum(freq * v / SR))
    env = C.adsr(nsamp, 0.12, 0.3) * (0.8 + 0.2 * np.sin(np.pi * np.clip(t / max(dur, 0.1), 0, 1)))
    return s * env


def choir(notes, dur):
    """멀리서 허밍하는 합창: 사인 여러 개 + 느린 떨림, '우-' 처럼 낮게 거른다."""
    nsamp = int((dur + 1.0) * SR)
    t = np.arange(nsamp) / SR
    s = np.zeros(nsamp)
    for m in notes:
        f = hz(n(m) + 12)
        for det in (-0.06, 0.05):
            ph = 2 * np.pi * np.cumsum(f * 2 ** (det / 12) * (1 + 0.004 * np.sin(2 * np.pi * (4.5 + rng.random()) * t)) / SR)
            s += np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)
    s = C.lowpass_fft(s, 1100, 1)
    return s * C.adsr(nsamp, 0.8, 1.0) / (2 * len(notes))


def tremolo_strings(notes, dur, rate=11):
    nsamp = int((dur + 0.3) * SR)
    t = np.arange(nsamp) / SR
    s = np.zeros(nsamp)
    for m in notes:
        s += V.bowed(hz(n(m)), dur, 2600, 0.003)[:nsamp]
    am = 0.55 + 0.45 * np.abs(np.sin(np.pi * rate * t + rng.random()))
    return s * am / len(notes)


def low_drum(freq=55, dur=0.6, decay=6):
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    f = freq + freq * 0.8 * np.exp(-t * 25)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * decay)
    s += C.lowpass_fft(rng.normal(0, 1, nsamp), 900) * 0.2 * np.exp(-t * 35)
    return s


def snare(dur=0.18, tone=1800):
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    x = rng.normal(0, 1, nsamp)
    x = C.lowpass_fft(x, tone * 2) - C.lowpass_fft(x, tone / 2)
    return (x + 0.4 * np.sin(2 * np.pi * 190 * t)) * np.exp(-t * 28)


def pick_tap(freq=2600):
    """곡괭이가 돌을 치는 짧은 쇳소리."""
    nsamp = int(0.35 * SR)
    t = np.arange(nsamp) / SR
    s = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t * d) for r, a, d in ((1, 1, 18), (2.3, 0.5, 30), (3.7, 0.3, 45)))
    s += C.lowpass_fft(rng.normal(0, 1, nsamp), 6000) * 0.4 * np.exp(-t * 90)
    return s


def drip():
    """물방울: 아주 짧게 음이 올라가는 사인."""
    nsamp = int(0.12 * SR)
    t = np.arange(nsamp) / SR
    f0 = 900 + 700 * rng.random()
    return np.sin(2 * np.pi * np.cumsum(f0 * (1 + 2.5 * t / 0.12)) / SR) * np.exp(-t * 40) * np.minimum(1, t / 0.001)


def chain():
    """멀리서 사슬·갑옷이 철컥이는 소리."""
    out = np.zeros(int(0.6 * SR))
    for k in range(4 + int(rng.random() * 3)):
        at = int((k * 0.07 + rng.random() * 0.04) * SR)
        s = pick_tap(1800 + 1500 * rng.random())[: len(out) - at] * (0.5 + 0.5 * rng.random())
        out[at:at + len(s)] += s
    return out


def thunder(dur=4.0):
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    x = C.lowpass_fft(rng.normal(0, 1, nsamp), 160, 2)
    env = np.minimum(1, t / 0.4) * np.exp(-t * 1.1) * (0.7 + 0.3 * np.sin(2 * np.pi * 1.3 * t))
    return x * env / (np.max(np.abs(x)) + 1e-9)


def swell(notes, dur):
    """금관이 부풀었다가 풀리지 않고 가라앉는다."""
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    s = sum(horn(hz(n(m)), dur)[:nsamp] for m in notes) / len(notes)
    return s * np.sin(np.pi * t / dur) ** 2


def bass_note(name, dur, gain_hint=1.0):
    return C.bass(hz(n(name)), dur) * gain_hint


# ---------- 곡 ----------

def mine(buf, off, beat):
    bar = 4 * beat
    chords = [("D2", ["D3", "F3", "A3"]), ("D2", ["D3", "F3", "A3"]), ("A#1", ["A#2", "D3", "F3"]), ("C2", ["C3", "E3", "G3"]),
              ("D2", ["D3", "F3", "A3"]), ("D2", ["D3", "F3", "A3"]), ("G1", ["G2", "A#2", "D3"]), ("A1", ["A2", "C#3", "E3"])]
    theme = [(0, 0, "D3", 2), (0, 2, "F3", 1), (0, 3, "E3", 1), (1, 0, "D3", 2), (1, 2, "A2", 2),
             (2, 0, "A#2", 1.5), (2, 1.5, "C3", 0.5), (2, 2, "D3", 2), (3, 0, "C3", 4),
             (4, 0, "D3", 2), (4, 2, "F3", 1), (4, 3, "G3", 1), (5, 0, "A3", 3), (5, 3, "G3", 1),
             (6, 0, "F3", 1), (6, 1, "E3", 1), (6, 2, "D3", 1), (6, 3, "E3", 1), (7, 0, "A2", 3)]
    for b in range(24):
        root, notes = chords[b % 8]
        at = off + b * bar
        sec = b // 8
        place(buf, C.pad(notes, bar), at - 0.3, 0.16)
        place(buf, bass_note(root, bar * 0.9), at, 0.3)
        # 피치카토가 조심스러운 발걸음처럼 8분음표로 걷는다
        walk = [notes[0], notes[1], notes[2], notes[1]] * 2
        for k, m in enumerate(walk):
            place(buf, pizz(hz(n(m) - (12 if k % 4 == 0 else 0))), at + k * beat / 2 + rng.normal(0, 0.004),
                  0.16 if k % 2 == 0 else 0.1, pan=-0.2 + 0.1 * (k % 3))
        # 곡괭이 소리가 리듬을 만든다 (2, 4박, 가끔 엇박)
        place(buf, pick_tap(2400 + 300 * rng.random()), at + beat, 0.05, pan=0.45)
        place(buf, pick_tap(2400 + 300 * rng.random()), at + 3 * beat, 0.06, pan=0.45)
        if b % 2 == 1:
            place(buf, pick_tap(2800), at + 3.5 * beat, 0.035, pan=0.5)
        # 약한 큰북은 마디 첫 박
        place(buf, low_drum(50, 0.8, 5), at, 0.22 if sec else 0.14)
        # 물방울
        for _ in range(2):
            place(buf, drip(), at + rng.random() * bar, 0.03, pan=rng.uniform(-0.7, 0.7))
        # 8마디마다 박쥐 날갯짓 같은 짧은 피리
        if b % 8 == 5:
            for j in range(6):
                place(buf, C.flute(hz(n("A5") + (j % 2) * 2), beat * 0.18), at + 2 * beat + j * beat / 6, 0.05, pan=0.6)
    for b, bt, name, length in theme:
        for sec in (1, 2):
            at = off + (sec * 8 + b) * bar + bt * beat
            place(buf, reed(hz(n(name)), length * beat * 0.95, 800, 0.32), at, 0.14, pan=-0.05)
            if sec == 2:
                place(buf, C.flute(hz(n(name) + 24), length * beat * 0.9), at, 0.07, pan=0.15)


def citadel(buf, off, beat):
    bar = 4 * beat
    chords = [("C2", ["C3", "D#3", "G3"]), ("G#1", ["G#2", "C3", "D#3"]), ("F2", ["F2", "G#2", "C3"]), ("G1", ["G2", "B2", "D3"]),
              ("C2", ["C3", "D#3", "G3"]), ("G#1", ["G#2", "C3", "D#3"]), ("D#2", ["D#3", "G3", "A#3"]), ("G1", ["G2", "B2", "D3"])]
    theme = [(0, 0, "C4", 2), (0, 2, "D#4", 1), (0, 3, "G4", 1), (1, 0, "G#4", 3), (1, 3, "G4", 1),
             (2, 0, "F4", 2), (2, 2, "D#4", 1), (2, 3, "D4", 1), (3, 0, "D4", 4),
             (4, 0, "C4", 2), (4, 2, "D#4", 1), (4, 3, "G4", 1), (5, 0, "C5", 3), (5, 3, "A#4", 1),
             (6, 0, "G#4", 2), (6, 2, "G4", 1), (6, 3, "F4", 1), (7, 0, "G4", 4)]
    for b in range(32):
        root, notes = chords[b % 8]
        at = off + b * bar
        sec = b // 8
        place(buf, V.bowed(hz(n(root) + 12), bar, 700, 0.003), at, 0.16)
        place(buf, C.pad(notes, bar), at - 0.3, 0.14)
        place(buf, bass_note(root, bar), at, 0.24)
        # 하프시코드 반복 음형: 8분음표 8개
        fig = [0, 1, 2, 1, 2, 1, 0, 1] if b % 2 == 0 else [0, 2, 1, 2, 0, 2, 1, 2]
        if sec != 3 or b % 2 == 0:
            for k, idx in enumerate(fig):
                place(buf, harpsichord(hz(n(notes[idx]) + 12)), at + k * beat / 2, 0.07 if k % 2 else 0.1, pan=0.3)
        # 약음 스네어 행진
        for k, v in ((0, 0.09), (1.5, 0.05), (2, 0.09), (3, 0.06), (3.5, 0.05)):
            place(buf, snare(), at + k * beat + rng.normal(0, 0.003), v * (0.5 if sec == 0 else 1), pan=-0.3)
        place(buf, low_drum(48, 0.9, 4), at, 0.16)
        if sec >= 2:
            place(buf, choir(notes, bar), at - 0.2, 0.16, pan=-0.15)
        if b % 8 == 0:
            place(buf, C.bell(hz(n("C3")), 6.0), at, 0.13, pan=0.2)
        if b % 8 == 3 and sec != 0:
            place(buf, chain(), at + 2.5 * beat, 0.05, pan=-0.6)
    for b, bt, name, length in theme:
        for sec in (1, 2):
            at = off + (sec * 8 + b) * bar + bt * beat
            place(buf, horn(hz(n(name) - (12 if sec == 1 else 0)), length * beat * 0.96), at, 0.22, pan=0.0)


def tower(buf, off, beat):
    bar = 4 * beat
    chords = [("E2", ["E3", "G3", "B3"]), ("C2", ["C3", "E3", "G3"]), ("A1", ["A2", "C3", "E3"]), ("B1", ["B2", "D#3", "F#3"]),
              ("E2", ["E3", "G3", "B3"]), ("C2", ["C3", "E3", "G3"]), ("D2", ["D3", "F#3", "A3"]), ("B1", ["B2", "D#3", "A3"])]
    theme = [(0, 0, "E4", 1.5), (0, 1.5, "F#4", 0.5), (0, 2, "G4", 1), (0, 3, "B4", 1), (1, 0, "A4", 2), (1, 2, "G4", 1), (1, 3, "F#4", 1),
             (2, 0, "E4", 1), (2, 1, "G4", 1), (2, 2, "C5", 1), (2, 3, "B4", 1), (3, 0, "F#4", 4),
             (4, 0, "E4", 1.5), (4, 1.5, "F#4", 0.5), (4, 2, "G4", 1), (4, 3, "B4", 1), (5, 0, "E5", 2), (5, 2, "D5", 1), (5, 3, "C5", 1),
             (6, 0, "B4", 1), (6, 1, "A4", 1), (6, 2, "G4", 1), (6, 3, "F#4", 1), (7, 0, "D#4", 4)]
    for b in range(32):
        root, notes = chords[b % 8]
        at = off + b * bar
        sec = b // 8
        place(buf, tremolo_strings([m[:-1] + str(int(m[-1]) + 1) for m in notes], bar), at, 0.12, pan=-0.35)
        place(buf, bass_note(root, bar), at, 0.2)
        # 낮은 현 박동: 8분음표
        for k in range(8):
            place(buf, V.bowed(hz(n(root) + 12), beat * 0.4, 800, 0.0), at + k * beat / 2, 0.09 if k % 2 == 0 else 0.06)
        # 하프(짝수 칸)·첼레스타(홀수 칸) 아르페지오: 16분음표
        arp = [0, 1, 2, 1] * 4
        for k, idx in enumerate(arp):
            f = hz(n(notes[idx]) + 24 + (12 if k % 8 >= 4 else 0))
            if k % 2 == 0:
                place(buf, pluck(f, 0.9, 0.6), at + k * beat / 4, 0.06, pan=0.35)
            elif sec >= 1:
                place(buf, V.celesta(f, 0.8), at + k * beat / 4, 0.025, pan=0.45)
        # 8마디째마다 팀파니 롤로 긴장을 쌓는다
        if b % 8 == 7:
            for k in range(16):
                place(buf, low_drum(65, 0.3, 14), at + 2 * beat + k * beat / 8, 0.04 + 0.1 * k / 16)
        else:
            place(buf, low_drum(65, 0.6, 8), at, 0.12)
        if b % 8 == 2:
            place(buf, thunder(), at + beat, 0.12, pan=-0.2)
        if b % 4 == 1:
            place(buf, V.music_box(hz(n(notes[2]) + 36), 1.2), at + 3.5 * beat, 0.04, pan=0.6)
        if sec >= 2 and b % 4 == 3:
            place(buf, swell(notes, 2 * beat + 0.4), at + 2 * beat, 0.16)
    for b, bt, name, length in theme:
        for sec in (1, 2):
            at = off + (sec * 8 + b) * bar + bt * beat
            place(buf, reed(hz(n(name)), length * beat * 0.94, 1700, 0.5, 0.005), at, 0.09, pan=0.05)


SONGS = {
    "dungeon_mine": (mine, 84, 24, -19, 2.4, 0.38),
    "dungeon_citadel": (citadel, 100, 32, -18, 2.8, 0.34),
    "dungeon_tower": (tower, 112, 32, -18, 2.2, 0.28),
}


def render(name, preview_dir):
    song, bpm, bars, target, rev, wet = SONGS[name]
    beat = 60 / bpm
    loop = bars * 4 * beat
    buf = np.zeros((int((3 * loop + 6) * SR), 2))
    state = rng.bit_generator.state
    for k in range(3):
        rng.bit_generator.state = state
        song(buf, k * loop, beat)
    buf = C.reverb(buf, seconds=rev, wet=wet)
    out = buf[int(loop * SR): int(loop * SR) + int(loop * SR)].copy()
    for ch in range(2):
        X = np.fft.rfft(out[:, ch])
        f = np.fft.rfftfreq(len(out), 1 / SR)
        X *= 1 / np.sqrt(1 + (35 / np.maximum(f, 1)) ** 4)
        out[:, ch] = np.fft.irfft(X, len(out))
    out *= 10 ** (target / 20) / np.sqrt(np.mean(out ** 2))
    peak = np.max(np.abs(out))
    if peak > 0.89:
        out = np.tanh(out / 0.89) * 0.89
    pcm = (np.clip(out, -1, 1) * 32767).astype("<i2")
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dst = os.path.join(root, "assets", "audio", "bgm", name + ".ogg")
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, "x.wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "5", dst], check=True)
        if preview_dir:
            subprocess.run(["cp", wav_path, os.path.join(preview_dir, name + ".wav")], check=True)
    print(f"{dst}  {loop:.1f}s  rms {target} dBFS  peak {20 * np.log10(peak):.1f} dBFS")


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    names = [a for a in sys.argv[2:]] or list(SONGS)
    for name in names:
        render(name, preview)


if __name__ == "__main__":
    main()

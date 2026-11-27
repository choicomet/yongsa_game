"""마을 배경음악 두 곡을 코드로 작곡·합성한다.

- assets/audio/bgm/village_frozen.ogg : 마왕을 잡기 전. 사람들이 수정 속에 잠든 고향.
  G단조, 3/4박자, 60BPM. 오르골·첼레스타가 마을 주제를 느리게 치고, 유리 같은 수정 울림과
  차가운 높은 현이 깔린다. 가끔 멀리서 종이 혼자 울리고, 후반에는 첼로가 같은 주제를 받는다(용사의 다짐).
- assets/audio/bgm/village_free.ogg : 마왕을 잡은 후. 사람들이 풀려나 작은 축제가 열린 마을.
  같은 주제를 G장조 96BPM으로 밝게. 피들·피리 멜로디, 기타 스트로크, 아코디언, 손 타악기, 종.

두 곡은 같은 주제 멜로디를 쓴다 (얼어붙은 노래가 다시 살아나는 느낌).
캠프 곡과 같은 방식으로 세 바퀴를 이어 만든 뒤 가운데만 잘라 써서 반복 이음매가 끊기지 않는다.

실행: python tools/make_village_bgm.py [미리듣기 wav 폴더]   (numpy, ffmpeg 필요)
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_camp_bgm as C  # 칼림바·피리·패드·저음·종 소리, 잔향, 배치 함수를 같이 쓴다

SR = C.SR
rng = C.rng
n, hz, place = C.n, C.midi_hz, C.place

# 마을 주제 (장조 기준). (마디, 박, 음, 길이(박))
THEME = [
    (0, 0, "D5", 2), (0, 2, "B4", 1), (1, 0, "G4", 2), (1, 2, "A4", 1),
    (2, 0, "B4", 1), (2, 1, "A4", 1), (2, 2, "G4", 1), (3, 0, "E4", 3),
    (4, 0, "D4", 1), (4, 1, "G4", 1), (4, 2, "B4", 1), (5, 0, "A4", 2), (5, 2, "G4", 1),
    (6, 0, "A4", 3),
    (8, 0, "D5", 2), (8, 2, "B4", 1), (9, 0, "G4", 2), (9, 2, "A4", 1),
    (10, 0, "B4", 1), (10, 1, "D5", 1), (10, 2, "E5", 1), (11, 0, "D5", 3),
    (12, 0, "B4", 1), (12, 1, "A4", 1), (12, 2, "G4", 1), (13, 0, "A4", 2), (13, 2, "B4", 1),
    (14, 0, "G4", 3),
]
# 마디마다 화음 (16마디 한 바퀴)
CHORDS_MAJOR = [
    ("G", ["G3", "B3", "D4"]), ("E", ["E3", "G3", "B3"]), ("C", ["C3", "E3", "G3"]), ("C", ["C3", "E3", "A3"]),
    ("G", ["G3", "B3", "D4"]), ("D", ["D3", "F#3", "A3"]), ("D", ["D3", "F#3", "A3"]), ("D", ["D3", "F#3", "C4"]),
    ("G", ["G3", "B3", "D4"]), ("E", ["E3", "G3", "B3"]), ("C", ["C3", "E3", "G3"]), ("G", ["G3", "B3", "D4"]),
    ("E", ["E3", "G3", "B3"]), ("D", ["D3", "F#3", "A3"]), ("G", ["G3", "B3", "D4"]), ("D", ["D3", "F#3", "A3"]),
]
CHORDS_MINOR = [
    ("G", ["G3", "A#3", "D4"]), ("D#", ["D#3", "G3", "A#3"]), ("C", ["C3", "D#3", "G3"]), ("C", ["C3", "D#3", "G3", "A#3"]),
    ("G", ["G3", "A#3", "D4"]), ("D", ["D3", "F3", "A3"]), ("D", ["D3", "F3", "A3"]), ("D", ["D3", "F#3", "A3"]),
    ("G", ["G3", "A#3", "D4"]), ("D#", ["D#3", "G3", "A#3"]), ("C", ["C3", "D#3", "G3"]), ("G", ["G3", "A#3", "D4"]),
    ("D#", ["D#3", "G3", "A#3"]), ("D", ["D3", "F#3", "A3"]), ("G", ["G3", "A#3", "D4"]), ("G", ["G3", "A#3", "D4"]),
]


def to_minor(name):
    """장조 멜로디를 G단조로: 시→시♭, 미→미♭."""
    return name.replace("B", "A#").replace("E", "D#") if name[0] in "BE" and "#" not in name else name


# ---------- 악기 ----------

def music_box(freq, dur=2.5):
    """오르골: 짧게 맑게 울리는 쇠 빗 소리 (비정수 배음)."""
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    s = (np.sin(2 * np.pi * freq * t) * np.exp(-t * 2.2)
         + 0.35 * np.sin(2 * np.pi * freq * 3.98 * t) * np.exp(-t * 6)
         + 0.12 * np.sin(2 * np.pi * freq * 8.1 * t) * np.exp(-t * 12))
    return s * np.minimum(1, t / 0.0015)


def celesta(freq, dur=2.0):
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    s = np.sin(2 * np.pi * freq * t) + 0.25 * np.sin(2 * np.pi * freq * 4 * t) * np.exp(-t * 5)
    return s * np.exp(-t * 1.6) * np.minimum(1, t / 0.003)


def bowed(freq, dur, bright=1200, vib=0.006):
    """활로 켜는 현 (첼로·피들): 톱니파 + 늦게 들어오는 떨림, 부드럽게 시작."""
    nsamp = int((dur + 0.3) * SR)
    t = np.arange(nsamp) / SR
    v = 1 + vib * np.sin(2 * np.pi * 5.5 * t) * np.clip((t - 0.15) / 0.3, 0, 1)
    ph = np.cumsum(freq * v / SR) % 1.0
    s = 2 * ph - 1
    s = C.lowpass_fft(s, bright, 2)
    return s * C.adsr(nsamp, 0.14, 0.3)


def shimmer(notes, dur):
    """수정 울림: 높은 사인들이 제각각 천천히 일렁인다."""
    nsamp = int((dur + 1.5) * SR)
    t = np.arange(nsamp) / SR
    s = np.zeros(nsamp)
    for m in notes:
        f = hz(n(m) + 24)
        rate, ph = 0.2 + 0.4 * rng.random(), rng.random() * 6.28
        s += np.sin(2 * np.pi * f * t) * (0.5 + 0.5 * np.sin(2 * np.pi * rate * t + ph))
    return s * C.adsr(nsamp, 1.2, 1.5) / len(notes)


def accordion(notes, dur):
    """아코디언: 살짝 어긋난 두 리드(펄스파) + 떨림."""
    nsamp = int((dur + 0.2) * SR)
    t = np.arange(nsamp) / SR
    s = np.zeros(nsamp)
    for m in notes:
        f = hz(n(m) + 12)
        for det in (0.0, 0.12):
            ph = (f * 2 ** (det / 12) * t + rng.random()) % 1.0
            s += np.where(ph < 0.35, 1.0, -0.55)
    s = C.lowpass_fft(s, 1600, 2)
    s *= 1 + 0.1 * np.sin(2 * np.pi * 6 * t)
    return s * C.adsr(nsamp, 0.05, 0.2) / (2 * len(notes))


def strum(notes, at, buf, gain, down=True):
    order = notes if down else notes[::-1]
    for k, m in enumerate(order + [order[0]]):
        f = hz(n(m) + (12 if k == len(order) else 0))
        place(buf, C.pluck(f, 1.2, 0.55), at + k * 0.014, gain * (0.8 + 0.3 * rng.random()), pan=0.3)


def frame_drum(dur=0.4):
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    f = 75 + 60 * np.exp(-t * 30)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)
    s += C.lowpass_fft(rng.normal(0, 1, nsamp), 1500) * 0.15 * np.exp(-t * 40)
    return s


def shaker(dur=0.08):
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    x = rng.normal(0, 1, nsamp)
    x = x - C.lowpass_fft(x, 5000)
    return x * np.minimum(1, t / 0.01) * np.exp(-t * 45)


# ---------- 곡 ----------

def frozen(buf, off, beat):
    bar = 3 * beat
    for b, (root, notes) in enumerate(CHORDS_MINOR * 2):
        at = off + b * bar
        place(buf, C.pad(notes, bar), at - 0.3, 0.32)
        place(buf, shimmer(notes, bar), at - 0.5, 0.05, pan=0.3 * np.sin(b))
        place(buf, C.bass(hz(n(root + "2") + (12 if n(root + "2") < n("E2") else 0)), bar), at, 0.22)
        # 높은 현이 차갑게 오래 끈다 (한 마디씩 걸러서)
        if b % 2 == 0:
            place(buf, bowed(hz(n(notes[-1]) + 12), 2 * bar, 2200, 0.004), at, 0.035, pan=-0.3)
        # 오르골이 화음을 아주 드문드문 짚는다
        place(buf, music_box(hz(n(notes[0]) + 24)), at + beat, 0.05, pan=0.35)
        place(buf, music_box(hz(n(notes[1]) + 24)), at + 2 * beat, 0.04, pan=0.35)
        # 멀리서 혼자 울리는 종 (8마디마다)
        if b % 8 == 6:
            place(buf, C.bell(hz(n("G4")), 5.0), at + beat, 0.12, pan=-0.5)
    for k in range(2):
        for b, bt, name, length in THEME:
            at = off + (k * 16 + b) * bar + bt * beat
            f = hz(n(to_minor(name)))
            if k == 0:
                # 첫 바퀴: 오르골 + 첼레스타가 한 옥타브 위에서
                place(buf, music_box(f * 2, 3.0), at, 0.2, pan=0.1)
                place(buf, celesta(f * 2), at + 0.01, 0.08, pan=-0.1)
            else:
                # 두 번째 바퀴: 첼로가 같은 주제를 받는다 (용사의 다짐), 오르골은 메아리처럼 작게
                place(buf, bowed(f / 2, length * beat * 0.97, 1100, 0.007), at, 0.17, pan=-0.05)
                place(buf, music_box(f * 2, 2.0), at + beat / 2, 0.05, pan=0.4)


def free(buf, off, beat):
    bar = 3 * beat
    for b, (root, notes) in enumerate(CHORDS_MAJOR * 2):
        at = off + b * bar
        place(buf, accordion(notes, bar), at, 0.11, pan=-0.25)
        r = n(root + "2")
        if r < n("E2"):
            r += 12
        place(buf, C.bass(hz(r), beat * 1.8), at, 0.34)
        place(buf, C.bass(hz(r + 7), beat * 0.9), at + 2 * beat, 0.2)
        # 기타: 쿵-짝-짝
        strum(notes, at + beat, buf, 0.09, True)
        strum(notes, at + 2 * beat, buf, 0.07, False)
        # 손 타악기
        place(buf, frame_drum(), at, 0.22)
        for k in range(6):
            place(buf, shaker(), at + k * beat / 2 + rng.normal(0, 0.004), 0.05 if k % 2 else 0.08, pan=0.45)
        if b % 4 == 3:
            place(buf, C.bell(hz(n(notes[0]) + 24), 2.5), at + 2 * beat, 0.05, pan=0.5)
    for k in range(2):
        for b, bt, name, length in THEME:
            at = off + (k * 16 + b) * bar + bt * beat
            f = hz(n(name))
            dur = length * beat * 0.92
            if k == 0:
                # 첫 바퀴: 피리
                place(buf, C.flute(f, dur), at, 0.2, pan=0.1)
            else:
                # 두 번째 바퀴: 피들이 받고, 피리가 한 옥타브 위에서 같이
                place(buf, bowed(f, dur, 3200, 0.008), at, 0.12, pan=0.05)
                place(buf, C.flute(f * 2, dur), at, 0.1, pan=0.2)
        # 두 번째 바퀴 쉬는 마디(7, 15)에는 피리가 짧게 장식음을 넣는다
        if k == 1:
            for b, notes in ((7, ["F#5", "A5", "D6"]), (15, ["D5", "F#5", "A5"])):
                for j, m in enumerate(notes):
                    place(buf, C.flute(hz(n(m)), beat * 0.45), off + (16 + b) * bar + j * beat / 2, 0.08, pan=0.2)


def render(song, bpm, bars, dst, preview_dir):
    beat = 60 / bpm
    loop = bars * 3 * beat
    buf = np.zeros((int((3 * loop + 5) * SR), 2))
    state = rng.bit_generator.state
    for k in range(3):
        rng.bit_generator.state = state
        song(buf, k * loop, beat)
    buf = C.reverb(buf, seconds=3.2 if song is frozen else 2.0, wet=0.4 if song is frozen else 0.22)
    out = buf[int(loop * SR): int(loop * SR) + int(loop * SR)].copy()
    for ch in range(2):
        X = np.fft.rfft(out[:, ch])
        f = np.fft.rfftfreq(len(out), 1 / SR)
        X *= 1 / np.sqrt(1 + (35 / np.maximum(f, 1)) ** 4)
        out[:, ch] = np.fft.irfft(X, len(out))
    target = -20 if song is frozen else -17
    out *= 10 ** (target / 20) / np.sqrt(np.mean(out ** 2))
    peak = np.max(np.abs(out))
    if peak > 0.89:
        out = np.tanh(out / 0.89) * 0.89
    pcm = (np.clip(out, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, "x.wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "5", dst], check=True)
        if preview_dir:
            subprocess.run(["cp", wav_path, os.path.join(preview_dir, os.path.basename(dst)[:-4] + ".wav")], check=True)
    print(f"{dst}  {loop:.1f}s  rms {target} dBFS  peak {20 * np.log10(peak):.1f} dBFS")


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    bgm = os.path.join(root, "assets", "audio", "bgm")
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    render(frozen, 60, 32, os.path.join(bgm, "village_frozen.ogg"), preview)
    render(free, 96, 32, os.path.join(bgm, "village_free.ogg"), preview)


if __name__ == "__main__":
    main()

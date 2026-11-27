"""캠프 배경음악을 코드로 작곡·합성한다 (assets/audio/bgm/camp.ogg).

포근한 지하 탐험 게임의 쉼터 느낌: 느린 3/4박자, G장조, 따뜻한 패드 위에
칼림바 같은 뜯는 소리가 아르페지오를 깔고, 부드러운 오카리나 같은 피리가 멜로디를 분다.
32마디(약 76초)가 끊김 없이 반복되도록, 같은 곡을 세 번 이어 그린 뒤 가운데 바퀴만 잘라 쓴다
(앞 바퀴의 잔향과 다음 바퀴의 미리 들어오는 패드가 모두 들어가 있어서 이음매가 자연스럽다).

실행: python tools/make_camp_bgm.py   (numpy, ffmpeg 필요)
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
BPM = 76
BEAT = 60 / BPM
BAR = 3 * BEAT
BARS = 32
LOOP = BARS * BAR
rng = np.random.default_rng(26)


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


NOTE = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def n(name):
    """'F#4' 같은 이름을 MIDI 번호로."""
    pitch, octave = name[:-1], int(name[-1])
    return 12 * (octave + 1) + NOTE[pitch]


# 2마디마다 화음 하나 (근음, 화음 구성음들)
CHORDS = [
    # A: 쉼터에 들어와 숨 돌리기
    ("G", ["G3", "B3", "D4", "F#4"]), ("E", ["E3", "G3", "B3", "D4"]),
    ("C", ["C3", "E3", "G3", "B3"]), ("D", ["D3", "F#3", "A3", "E4"]),
    ("G", ["G3", "B3", "D4", "A4"]), ("B", ["B2", "D3", "F#3", "A3"]),
    ("C", ["C3", "E3", "G3", "D4"]), ("D", ["D3", "G3", "A3", "D4"]),
    # B: 조금 더 멀리, 그리고 다시 불가로
    ("E", ["E3", "G3", "B3", "F#4"]), ("C", ["C3", "G3", "B3", "E4"]),
    ("G", ["G3", "B3", "D4", "G4"]), ("D", ["D3", "F#3", "A3", "C#4"]),
    ("A", ["A2", "C3", "E3", "G3"]), ("C", ["C3", "E3", "G3", "D4"]),
    ("D", ["D3", "G3", "A3", "C4"]), ("G", ["G3", "B3", "D4", "F#4"]),
]

# 멜로디: (마디, 박, 음, 길이(박)). 쉬는 곳을 많이 둬서 오래 들어도 지치지 않게.
MELODY = [
    (0, 0, "B4", 2), (0, 2, "D5", 1), (1, 0, "E5", 3),
    (2, 0, "D5", 1), (2, 1, "B4", 1), (2, 2, "G4", 1), (3, 0, "A4", 3),
    (4, 0, "G4", 1), (4, 1, "A4", 1), (4, 2, "B4", 1), (5, 0, "E5", 2), (5, 2, "D5", 1),
    (6, 0, "B4", 3), (7, 0, "A4", 2), (7, 2, "F#4", 1),
    (8, 0, "B4", 2), (8, 2, "D5", 1), (9, 0, "F#5", 2), (9, 2, "E5", 1),
    (10, 0, "E5", 2), (10, 2, "G5", 1), (11, 0, "E5", 3),
    (12, 0, "D5", 1), (12, 1, "B4", 1), (12, 2, "A4", 1), (13, 0, "B4", 3),
    (14, 0, "A4", 2), (14, 2, "D5", 1), (15, 0, "A4", 3),
    (16, 0, "G5", 2), (16, 2, "F#5", 1), (17, 0, "E5", 3),
    (18, 0, "E5", 1), (18, 1, "D5", 1), (18, 2, "C5", 1), (19, 0, "G4", 3),
    (20, 0, "B4", 1), (20, 1, "D5", 1), (20, 2, "G5", 1), (21, 0, "D5", 3),
    (22, 0, "A4", 1), (22, 1, "D5", 1), (22, 2, "F#5", 1), (23, 0, "E5", 2), (23, 2, "D5", 1),
    (24, 0, "C5", 2), (24, 2, "E5", 1), (25, 0, "A4", 3),
    (26, 0, "G4", 1), (26, 1, "C5", 1), (26, 2, "E5", 1), (27, 0, "D5", 3),
    (28, 0, "A4", 1), (28, 1, "B4", 1), (28, 2, "C5", 1), (29, 0, "A4", 3),
    (30, 0, "G4", 4.5),
]


def lowpass(x, hz, passes=1):
    a = 1 - np.exp(-2 * np.pi * hz / SR)
    for _ in range(passes):
        y = np.empty_like(x)
        acc = 0.0
        for i in range(len(x)):  # 짧은 소리에만 쓴다
            acc += (x[i] - acc) * a
            y[i] = acc
        x = y
    return x


def lowpass_fft(x, hz, order=2):
    """긴 신호용: 주파수 영역에서 부드럽게 깎는다."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X /= np.sqrt(1 + (f / hz) ** (2 * order))
    return np.fft.irfft(X, len(x))


def adsr(nsamp, attack, release, sustain_level=1.0):
    t = np.arange(nsamp) / SR
    env = np.minimum(1.0, t / max(attack, 1e-4))
    rel_start = nsamp / SR - release
    env *= np.where(t > rel_start, np.clip(1 - (t - rel_start) / release, 0, 1), 1.0)
    return env * sustain_level


def pluck(freq, dur, bright=0.5):
    """칼림바·하프 같은 뜯는 소리 (Karplus-Strong + 몸통 울림)."""
    nsamp = int(dur * SR)
    period = int(SR / freq)
    buf = rng.uniform(-1, 1, period)
    buf = lowpass(buf, 1200 + 3000 * bright, 2)
    out = np.empty(nsamp)
    damp = 0.996
    idx = 0
    for i in range(nsamp):
        nxt = (idx + 1) % period
        v = buf[idx]
        out[i] = v
        buf[idx] = damp * 0.5 * (v + buf[nxt])
        idx = nxt
    t = np.arange(nsamp) / SR
    tine = np.sin(2 * np.pi * freq * t) * np.exp(-t * 3.5) * 0.6  # 칼림바 쇠 혀의 맑은 울림
    sig = out + tine
    sig *= np.minimum(1, t / 0.003)
    return sig / (np.max(np.abs(sig)) + 1e-9)


def flute(freq, dur):
    """오카리나 같은 부드러운 피리: 사인 + 약한 배음 + 늦게 들어오는 떨림 + 숨소리."""
    nsamp = int((dur + 0.35) * SR)
    t = np.arange(nsamp) / SR
    vib = 1 + 0.0045 * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - 0.25) / 0.4, 0, 1)
    phase = 2 * np.pi * freq * np.cumsum(vib) / SR
    tone = np.sin(phase) + 0.18 * np.sin(2 * phase) + 0.06 * np.sin(3 * phase)
    breath = lowpass_fft(rng.normal(0, 1, nsamp), 1800) * 0.025
    env = adsr(nsamp, 0.08, 0.35)
    env *= 0.85 + 0.15 * np.exp(-t * 4)
    return (tone + breath) * env


def pad(notes, dur):
    """따뜻한 패드: 살짝 어긋난 톱니파 여러 개를 낮게 거른다. 천천히 들어오고 천천히 사라진다."""
    nsamp = int((dur + 1.2) * SR)
    t = np.arange(nsamp) / SR
    sig = np.zeros(nsamp)
    for m in notes:
        f = midi_hz(n(m))
        for det in (-0.08, 0.0, 0.07):
            ph = (f * 2 ** (det / 12) * t + rng.random()) % 1.0
            sig += 2 * ph - 1
    sig = lowpass_fft(sig, 750, 2)
    sig *= 1 + 0.08 * np.sin(2 * np.pi * 0.23 * t)  # 숨 쉬듯 일렁임
    return sig * adsr(nsamp, 0.9, 1.4) / (len(notes) * 3)


def bass(freq, dur):
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    s = np.sin(2 * np.pi * freq * t) + 0.25 * np.sin(4 * np.pi * freq * t)
    return s * adsr(nsamp, 0.02, 0.5) * np.exp(-t * 0.6)


def bell(freq, dur=3.0):
    """가끔 울리는 작은 풍경 소리 (비정수 배음)."""
    nsamp = int(dur * SR)
    t = np.arange(nsamp) / SR
    s = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-t * d)
            for r, a, d in ((1, 1, 1.6), (2.76, 0.4, 3), (5.4, 0.2, 5)))
    return s * np.minimum(1, t / 0.002)


def place(buf, sig, at, gain, pan=0.0):
    i = int(at * SR)
    if i < 0:
        sig, i = sig[-i:], 0
    j = min(len(buf), i + len(sig))
    if j <= i:
        return
    l = np.cos((pan + 1) * np.pi / 4)
    r = np.sin((pan + 1) * np.pi / 4)
    buf[i:j, 0] += sig[: j - i] * gain * l
    buf[i:j, 1] += sig[: j - i] * gain * r


def render_loop(buf, offset):
    for ci, (root, notes) in enumerate(CHORDS):
        start = offset + ci * 2 * BAR
        place(buf, pad(notes, 2 * BAR), start - 0.3, 0.55)
        root_midi = n(root + "2")
        if root_midi < n("E2"):
            root_midi += 12
        place(buf, bass(midi_hz(root_midi), 2 * BAR), start, 0.32)
        place(buf, bass(midi_hz(root_midi + 7), BAR), start + BAR + 2 * BEAT, 0.14)
        # 칼림바 아르페지오: 8분음표 6개, 마디마다 모양을 조금씩 바꾼다
        tones = [n(x) + 12 for x in notes]
        patterns = ([0, 1, 2, 3, 2, 1], [0, 2, 1, 3, 2, 3])
        for bar in range(2):
            pat = patterns[(ci + bar) % 2]
            for k, idx in enumerate(pat):
                at = start + bar * BAR + k * BEAT / 2
                vel = (0.32 if k == 0 else 0.2) * (0.85 + 0.3 * rng.random())
                # 같은 박자에 딱 맞추지 않고 사람처럼 아주 조금 흔들리게
                at += rng.normal(0, 0.006)
                place(buf, pluck(midi_hz(tones[idx]), 1.6, 0.35), at, vel, pan=-0.25 + 0.5 * (idx / 3))
        # 4마디마다 높은 풍경 소리 하나
        if ci % 2 == 1:
            top = n(notes[-1]) + 24
            place(buf, bell(midi_hz(top)), start + BAR + BEAT, 0.05, pan=0.4)
    for bar, beat, name, length in MELODY:
        at = offset + bar * BAR + beat * BEAT
        place(buf, flute(midi_hz(n(name)), length * BEAT * 0.95), at, 0.24, pan=0.05)


def reverb(stereo, seconds=2.6, wet=0.32):
    """방 울림: 지수로 줄어드는 잡음을 좌우 따로 만들어 FFT로 컨볼루션."""
    nsamp = int(seconds * SR)
    t = np.arange(nsamp) / SR
    out = np.empty_like(stereo)
    for ch in range(2):
        ir = rng.normal(0, 1, nsamp) * np.exp(-t * 6.9 / seconds)
        ir = lowpass_fft(ir, 3500, 1)
        ir[: int(0.012 * SR)] = 0  # 앞쪽 반사 지연
        ir /= np.sqrt(np.sum(ir ** 2))
        size = len(stereo) + nsamp
        nfft = 1 << (size - 1).bit_length()
        y = np.fft.irfft(np.fft.rfft(stereo[:, ch], nfft) * np.fft.rfft(ir, nfft), nfft)[: len(stereo)]
        out[:, ch] = stereo[:, ch] + wet * y
    return out


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    dst = os.path.join(root, "assets", "audio", "bgm", "camp.ogg")
    total = int((3 * LOOP + 4) * SR)
    buf = np.zeros((total, 2))
    rng_state = rng.bit_generator.state
    for k in range(3):
        rng.bit_generator.state = rng_state  # 세 바퀴가 완전히 같은 소리가 되도록
        render_loop(buf, k * LOOP)
    buf = reverb(buf)
    loop = buf[int(LOOP * SR): int(LOOP * SR) + int(LOOP * SR)]
    # 아주 낮은 웅웅 소리와 날카로운 고음을 정리하고, 평균 크기를 맞춘다 (약 -18 dBFS RMS)
    for ch in range(2):
        x = loop[:, ch]
        X = np.fft.rfft(x)
        f = np.fft.rfftfreq(len(x), 1 / SR)
        X *= 1 / np.sqrt(1 + (35 / np.maximum(f, 1)) ** 4)
        loop[:, ch] = np.fft.irfft(X, len(x))
    rms = np.sqrt(np.mean(loop ** 2))
    loop *= 10 ** (-18 / 20) / rms
    peak = np.max(np.abs(loop))
    if peak > 0.89:
        loop = np.tanh(loop / 0.89) * 0.89
    pcm = (np.clip(loop, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, "camp.wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "5", dst], check=True)
        if len(sys.argv) > 1:
            subprocess.run(["cp", wav_path, sys.argv[1]], check=True)
    print(f"{dst}  {LOOP:.1f}s  rms -18 dBFS  peak {20 * np.log10(peak):.1f} dBFS")


if __name__ == "__main__":
    main()

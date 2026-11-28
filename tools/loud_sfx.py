"""효과음 WAV 를 리미터로 키우기 (2026-11-28, 지용 요청: 마왕 2페이즈 소리가 곡에 묻힘).

게임은 효과음 소리 크기를 1 보다 크게 못 틀어서, 파일 자체를 키운다.
gain_db 만큼 올린 뒤 꼭대기가 -1dB 를 넘는 곳만 미리 보고(5ms) 눌러서, 큰 '콰아앙'은 그대로 두고
작은 웃음·고동 소리가 올라오게 한다.

실행: python tools/loud_sfx.py 입력.wav 출력.wav 9      (numpy 필요)
bs_demonlord_phase2.wav 는 tools/make_combat_sfx.py 로 만든 뒤 이 도구로 +9dB 올렸다.
"""
import sys
import wave

import numpy as np
from numpy.lib.stride_tricks import sliding_window_view


def loud(x, sr, gain_db, ceil_db=-1.0, attack=0.005, release=0.12):
    y = x * 10 ** (gain_db / 20)
    thr = 10 ** (ceil_db / 20)
    need = np.minimum(1.0, thr / np.maximum(np.abs(y), 1e-9))
    n = 2 * int(attack * sr) + 1
    need = sliding_window_view(np.pad(need, n // 2, mode="edge"), n).min(axis=1)  # 미리 보고 줄이기
    g = np.empty_like(need)
    cur, r = 1.0, np.exp(-1 / (release * sr))
    for i, v in enumerate(need):
        cur = v if v < cur else v + (cur - v) * r  # 바로 줄이고 천천히 돌아온다
        g[i] = cur
    return np.clip(y * g, -thr, thr)


if __name__ == "__main__":
    src, dst, gain = sys.argv[1], sys.argv[2], float(sys.argv[3])
    with wave.open(src, "rb") as w:
        p = w.getparams()
        if p.sampwidth != 2 or p.nchannels != 1:
            sys.exit("16비트 모노 WAV 만 됩니다")
        x = np.frombuffer(w.readframes(p.nframes), dtype="<i2") / 32768.0
    y = loud(x, p.framerate, gain)
    with wave.open(dst, "wb") as o:
        o.setparams(p)
        o.writeframes(np.round(y * 32767).astype("<i2").tobytes())

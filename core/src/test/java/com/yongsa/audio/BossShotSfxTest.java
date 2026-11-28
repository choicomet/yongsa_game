package com.yongsa.audio;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/** 보스 쏘는 소리 (지용 2026-11-27): 혈월의 기사·탑의 수문장·마왕은 새 소리, 골렘과 몹은 예전 '삐융'. */
class BossShotSfxTest {

    @Test
    void knightAndDemonLordHaveTheirOwnShotSound() {
        assertEquals("shot_knight", SfxNames.bossShot("knight", "cast"));
        assertEquals("shot_demonlord", SfxNames.bossShot("demonlord", "cast"));
        assertEquals("orbBurst_demonlord", SfxNames.bossOrbBurst("demonlord", "cast"));
        assertEquals("shot_warden", SfxNames.bossShot("warden", "cast"));  // 수문장: 지용이 고른 구슬 소리
        for (String f : new String[]{"shot_knight", "shot_demonlord", "orbBurst_demonlord"}) {
            assertTrue(new File(assets(), f + ".wav").isFile(), f + " 파일");
        }
    }

    @Test
    void othersKeepTheOldSound() {
        assertEquals("cast", SfxNames.bossShot("golem", "cast"));
        assertEquals("cast", SfxNames.bossShot("imp", "cast"));
        assertEquals("cast", SfxNames.bossOrbBurst(null, "cast"));
    }

    @Test
    void shotIsPlayedAtItsTarget() {
        SfxMix mix = new SfxMix();
        mix.measureFile("shot_knight");
        SfxMix.Play p = mix.decide("shot_knight", 10);
        assertNotNull(p);
        assertEquals(Math.pow(10, (-9 + 8.5) / 20.0), p.gain, 0.01);
    }

    private static File assets() {
        File f = new File("assets/audio/sfx");
        return f.isDirectory() ? f : new File("../assets/audio/sfx");
    }
}

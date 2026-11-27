package com.yongsa.audio;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/** 보스 쏘는 소리 (지용 2026-11-27): 혈월의 기사·마왕만 새 소리, 나머지 보스와 몹은 예전 '삐융'. */
class BossShotSfxTest {

    @Test
    void knightAndDemonLordHaveTheirOwnShotSound() {
        assertEquals("shot_knight", SfxNames.bossShot("knight", "cast"));
        assertEquals("shot_demonlord", SfxNames.bossShot("demonlord", "cast"));
        assertEquals("orbBurst_demonlord", SfxNames.bossOrbBurst("demonlord", "cast"));
        for (String f : new String[]{"shot_knight", "shot_demonlord", "orbBurst_demonlord"}) {
            assertTrue(new File(assets(), f + ".wav").isFile(), f + " 파일");
        }
    }

    @Test
    void othersKeepTheOldSound() {
        assertEquals("cast", SfxNames.bossShot("golem", "cast"));
        assertEquals("cast", SfxNames.bossShot("warden", "cast"));
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

package com.yongsa.audio;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/** 보스 힘 모으기·쏘기 소리 (지용 2026-11-27): 보스마다 다른 파일, 없는 보스·몹은 예전 소리. */
class BossSfxTest {

    @Test
    void eachBossHasItsOwnChargeAndShotSound() {
        for (String b : new String[]{"golem", "knight", "warden", "demonlord"}) {
            assertEquals("charge_" + b, SfxNames.bossCharge(b, "roar"));
            assertEquals("shot_" + b, SfxNames.bossShot(b, "cast"));
            assertTrue(new File(assets(), "charge_" + b + ".wav").isFile(), b + " 힘 모으기 파일");
            assertTrue(new File(assets(), "shot_" + b + ".wav").isFile(), b + " 쏘기 파일");
        }
        assertEquals("orbBurst_demonlord", SfxNames.bossOrbBurst("demonlord", "cast"));
        assertTrue(new File(assets(), "orbBurst_demonlord.wav").isFile());
    }

    @Test
    void mobsAndUnknownKeepTheOldSound() {
        assertEquals("cast", SfxNames.bossShot("imp", "cast"));
        assertEquals("roar", SfxNames.bossCharge("bat", "roar"));
        assertEquals("cast", SfxNames.bossOrbBurst(null, "cast"));
    }

    @Test
    void chargeIsLouderThanBeforeAndDucksMusic() {
        SfxMix mix = new SfxMix();
        mix.measureFile("charge_golem");
        SfxMix.Play p = mix.decide("charge_golem", 10);
        assertNotNull(p);
        // 첫 버전은 -10dB 목표에 -12dB 기준이라 사실상 묻혔다. 이제 목표 -7dB, 곡을 줄인다
        assertEquals(-7f, SfxMix.CUES.get("charge_golem").targetDb, 0.01);
        assertTrue(p.duck > 0);
    }

    private static File assets() {
        File f = new File("assets/audio/sfx");
        return f.isDirectory() ? f : new File("../assets/audio/sfx");
    }
}

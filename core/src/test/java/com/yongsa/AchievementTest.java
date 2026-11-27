package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.Achievement;
import com.yongsa.model.Achievements;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.ForgeService;
import com.yongsa.model.GameState;
import com.yongsa.model.HeroRecord;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.model.Rules;
import com.yongsa.model.Stats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AchievementTest {
    private final Balance b = TestData.balance();

    @Test
    void duringRunAchievementsUnlockOnceAtThreshold() {
        List<String> saved = new ArrayList<>();
        Achievements a = new Achievements(saved);
        Stats s = new Stats();
        s.enhanceTries = 49;
        assertTrue(a.checkDuringRun(s).isEmpty());
        s.enhanceTries = 50;
        assertEquals(List.of(Achievement.HAMMER), a.checkDuringRun(s));
        assertTrue(a.checkDuringRun(s).isEmpty(), "이미 달성한 업적은 다시 알리지 않는다");
        assertEquals(List.of("HAMMER"), saved, "달성 목록은 설정에 저장되는 목록에 들어간다");
        s.crafted = 15;
        s.oreMined = 200;
        assertEquals(List.of(Achievement.COLLECTOR, Achievement.MINER), a.checkDuringRun(s));
    }

    @Test
    void clearAchievementsDependOnDeathsAndTime() {
        Stats s = new Stats();
        s.playSeconds = 29 * 60;
        Achievements a = Achievements.none();
        assertEquals(List.of(Achievement.FLAWLESS, Achievement.SWIFT), a.checkClear(s));

        Stats many = new Stats();
        many.deaths = 10;
        many.playSeconds = 31 * 60;
        Achievements b2 = Achievements.none();
        assertEquals(List.of(Achievement.UNYIELDING), b2.checkClear(many));
    }

    @Test
    void effectsAllApplyTogether() {
        GameState s = NewGame.create(b);
        Rules rules = new Rules(b);
        EnhanceService enhance = new EnhanceService(b);
        double before = enhance.odds(s, 0).success;
        int hpBefore = rules.maxHp(s);
        s.achievements = new Achievements(new ArrayList<>(List.of("HAMMER", "COLLECTOR", "FLAWLESS")));
        EnhanceService.Odds o = enhance.odds(s, 0);
        assertEquals(before + 2.0, o.success, 1e-9);
        assertEquals(100.0, o.total(), 1e-9);
        assertEquals(hpBefore + 5, rules.maxHp(s));
        assertEquals(b.weapon("blackiron").craftCost - 3, rules.craftCost(s, b.weapon("blackiron")));

        s.addOre(OreType.BLACKIRON, 5);
        new ForgeService(b).craft(s, b.weapon("blackiron"));
        assertEquals(0, s.ore(OreType.BLACKIRON));
    }

    @Test
    void heroRecordGrades() {
        Stats s = new Stats();
        s.oreMined = 134;
        s.enhanceSuccess = 21;
        s.deaths = 4;
        assertEquals("장인", HeroRecord.GRADES[HeroRecord.minerGrade(s)]);
        assertEquals("장인", HeroRecord.GRADES[HeroRecord.smithGrade(s)]);
        assertEquals("전설", HeroRecord.GRADES[HeroRecord.heroGrade(s)]);
        assertEquals(1f, HeroRecord.heroProgress(s));
        s.deaths = 20;
        assertEquals("견습", HeroRecord.GRADES[HeroRecord.heroGrade(s)]);
        s.oreMined = 30;
        assertEquals(0.5f, HeroRecord.minerProgress(s), 1e-6);
    }
}

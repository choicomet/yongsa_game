package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.yongsa.YongsaGame;
import com.yongsa.model.Achievement;
import com.yongsa.model.Achievements;
import com.yongsa.model.GameState;
import com.yongsa.model.HeroRecord;
import com.yongsa.model.Rules;
import com.yongsa.model.Stats;
import com.yongsa.screen.GameScreen;

/**
 * 엔딩 (UI-11): “기록” — 플레이 기록을 용사의 이력서처럼 보여 준다.
 * 위: 용사 그림과 이름·지원 분야·근무 기간(플레이 시간)·대표 무기·칭호. 가운데: 광부·대장장이·용사 경력 카드와 등급.
 * 아래: 보유 기술과 자기소개. 버튼은 마을로 가기(마을 가는 길에서 이어서), 타이틀로 돌아가기 (FR-던전-10).
 */
public class EndingScreen extends MenuScreen {
    private static final Color CARD_SUB = new Color(0x8f897dff);
    private static final Color GRADE = new Color(0xffcf7aff);

    private final GameState state;

    public EndingScreen(YongsaGame game, GameState state) {
        super(game);
        this.state = state;
        open(new End());
    }

    private class End extends Modal {
        private Drawable card, photo, tag, tagGold, gradeTag;

        End() {
            super(game.skin);
        }

        @Override
        protected void onCancel() {
        }

        @Override
        public void build() {
            if (card == null) {
                card = skin.getDrawable("record/career_card");
                photo = skin.getDrawable("record/photo_frame");
                tag = skin.getDrawable("record/skill_chip");
                tagGold = skin.getDrawable("record/skill_chip_gold");
                gradeTag = skin.getDrawable("record/grade_badge");
            }
            nav.style = "round";
            Stats s = state.stats;
            Rules rules = new Rules(game.balance);
            Table p = panel("record/paper");
            p.pad(18, 30, 20, 30);

            p.add(label("기록", "title", Ui.GOLD)).padBottom(2).row();
            p.add(label("깨어난 성검이 마왕을 꿰뚫었다. 캠프의 모닥불이 처음으로 밝게 타올랐다.", "small", Ui.DIM)).padBottom(8).row();
            p.add(new Image(skin.getDrawable("divider"))).width(830).height(1).padBottom(12).row();

            // ───── 위: 사진 + 인적 사항
            Table top = new Table();
            Stack pic = new Stack();
            pic.add(new Image(photo));
            Image hero = new Image(game.art.world("hero_down"));
            hero.setScaling(Scaling.fit);
            Table heroBox = new Table();
            heroBox.add(hero).size(112, 128);
            pic.add(heroBox);
            top.add(pic).size(140, 150).top().padRight(22);
            Table info = new Table();
            infoRow(info, "이름", "용사 (혼자서 다 함)", Ui.TEXT);
            infoRow(info, "지원 분야", "마왕 퇴치", Ui.TEXT);
            infoRow(info, "근무 기간", (int) (s.playSeconds / 60) + "분 " + (int) (s.playSeconds % 60) + "초", Ui.TEXT);
            infoRow(info, "대표 무기", state.equipped() != null ? rules.weaponName(state.equipped()) : "-", Ui.TEXT);
            infoRow(info, "얻은 칭호", title(), GRADE);
            top.add(info).growX().top();
            p.add(top).width(830).padBottom(10).row();

            // ───── 경력 사항
            p.add(label("경력 사항", "default", Ui.GOLD)).left().padBottom(6).row();
            Table jobs = new Table();
            jobs.add(job("광부", "무너진 갱도 외 2곳", HeroRecord.minerGrade(s), HeroRecord.minerProgress(s),
                    "캔 광물", s.oreMined + "개", "쓰러뜨린 몬스터", s.monstersKilled + "마리")).width(270).padRight(10);
            int rate = s.enhanceTries > 0 ? Math.round(100f * s.enhanceSuccess / s.enhanceTries) : 0;
            jobs.add(job("대장장이", "캠프 대장간", HeroRecord.smithGrade(s), HeroRecord.smithProgress(s),
                    "강화 성공", s.enhanceSuccess + " / " + s.enhanceTries + "번 (" + rate + "%)",
                    "만든 무기 / 부순 무기", s.crafted + " / " + s.destroyed + "자루")).width(270).padRight(10);
            int bosses = state.bossesDefeated.size() + (state.finalBossDefeated ? 1 : 0);
            jobs.add(job("용사", "마왕의 탑 최상층", HeroRecord.heroGrade(s), HeroRecord.heroProgress(s),
                    "쓰러뜨린 보스", bosses + "명", "쓰러진 횟수", s.deaths + "번")).width(270);
            p.add(jobs).padBottom(10).row();

            // ───── 보유 기술 · 자기소개
            p.add(label("보유 기술 · 자기소개", "default", Ui.GOLD)).left().padBottom(6).row();
            Table bottom = new Table();
            Table skills = new Table();
            skills.setBackground(card);
            skills.left();
            skills.add(chip("베기", false)).padRight(6);
            skills.add(chip("구르기", false)).padRight(6);
            skills.add(chip("강화 유지 " + s.enhanceKeep + "번", false)).padRight(6);
            skills.add(chip("마왕 퇴치", true));
            bottom.add(skills).width(400).height(70).padRight(10);
            Table intro = new Table();
            intro.setBackground(skin.getDrawable("record/quote"));
            Label quote = label("\"광물을 캐고, 칼을 두드리고, 마왕을 베었습니다.\n 도와줄 사람이 없어서 전부 제가 했습니다.\"", "small", Ui.TEXT);
            quote.setAlignment(Align.left);
            intro.add(quote).left().expandX();
            bottom.add(intro).width(420).height(70);
            p.add(bottom).padBottom(14).row();

            Table b = new Table();
            b.add(nav.add("마을로 가기", () -> {
                GameScreen screen = new GameScreen(game, state);
                screen.startOnVillageRoad();
                game.setScreen(screen);
                EndingScreen.this.dispose();
            })).width(240).height(48).padRight(14);
            b.add(nav.add("타이틀로 돌아가기", () -> {
                game.setScreen(new TitleScreen(game));
                EndingScreen.this.dispose();
            })).width(240).height(48);
            p.add(b);
        }

        /** 칭호: 가장 최근에 달성한 업적 이름. 없으면 “견습 용사”. */
        private String title() {
            java.util.List<String> list = game.settings.achievements;
            Achievements got = new Achievements(list);
            for (int i = list.size() - 1; i >= 0; i--) {
                for (Achievement a : Achievement.values()) if (a.name().equals(list.get(i)) && got.has(a)) return a.title;
            }
            return "견습 용사";
        }

        private void infoRow(Table t, String k, String v, Color color) {
            t.add(label(k, "default", Ui.DIM)).left().width(110).padBottom(3);
            t.add(label(v, "default", color)).left().expandX().padBottom(3).row();
            Image line = new Image(skin.getDrawable("record/row_line"));
            t.add(line).colspan(2).growX().height(1).padBottom(4).row();
        }

        private Table job(String name, String where, int grade, float progress, String k1, String v1, String k2, String v2) {
            Table c = new Table();
            c.setBackground(card);
            Table head = new Table();
            head.add(label(name, "large", Ui.TEXT)).left().expandX();
            Table g = new Table();
            g.setBackground(gradeTag);
            g.add(label(HeroRecord.GRADES[grade], "small", GRADE));
            head.add(g).right();
            c.add(head).growX().row();
            c.add(label(where, "small", CARD_SUB)).left().padBottom(6).row();
            c.add(statRow(k1, v1)).growX().row();
            c.add(statRow(k2, v2)).growX().padBottom(8).row();
            float w = 240;
            Table bar = new Table();
            bar.setBackground(skin.getDrawable("record/bar_back"));
            Image fill = new Image(skin.getDrawable("record/bar_fill"));
            bar.add(fill).width(Math.max(1, w * progress)).height(5).left();
            bar.add().expandX();
            c.add(bar).width(w).height(5).left();
            return c;
        }

        private Table statRow(String k, String v) {
            Table r = new Table();
            r.add(label(k, "small", Ui.TEXT)).left().expandX();
            r.add(label(v, "small", Ui.TEXT)).right();
            return r;
        }

        private Table chip(String text, boolean gold) {
            Table t = new Table();
            t.setBackground(gold ? tagGold : tag);
            t.add(label(text, "small", gold ? GRADE : Ui.TEXT));
            return t;
        }
    }
}

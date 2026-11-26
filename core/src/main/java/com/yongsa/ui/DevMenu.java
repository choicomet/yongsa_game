package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.model.AbsorbService;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.screen.GameScreen;

/**
 * 개발용 메뉴 (F1). 캠프로 돌아가고(싸우는 중에도), 보스를 다시 싸우게 되살리고, 보스방으로 바로 가고, 광물 무한·무적·가림 층 보기를 켜고 끈다.
 * 켜고 끈 값은 설정 파일에 저장되어 다음 실행에도 유지된다.
 */
public class DevMenu extends Modal {
    public static final int INFINITE_ORE = 999;

    private final GameScreen screen;

    public DevMenu(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
    }

    /** 버튼 높이와 줄 간격. 두 칸으로 나눠 720 화면에 위아래가 잘리지 않게 한다 (2026-11-26 지용). */
    private static final float ROW_H = 42, GAP = 8;

    @Override
    public void build() {
        GameSession g = screen.session;
        GameState s = g.state;
        nav.style = "round";
        nav.rows = true; // 위/아래 = 줄 이동, 왼쪽/오른쪽 = 같은 줄 안에서 이동 (왼쪽 도구 칸 ↔ 오른쪽 보스 칸)
        Table p = panel("panelRound");
        p.pad(24, 34, 26, 34);
        p.defaults().height(ROW_H).padBottom(GAP);
        p.add(label("개발용 메뉴", "large", Ui.GOLD)).colspan(6).height(36).padBottom(2).row();
        p.add(label("F1 · Esc 로 닫기   방향키 위/아래 줄 이동 · 왼쪽/오른쪽 같은 줄 안에서 이동", "small", Ui.DIM)).colspan(6).height(22).padBottom(8).row();
        p.add(new Image(skin.getDrawable("divider"))).colspan(6).width(720).height(1).padBottom(14).row();

        p.add(label("도구", "default", Ui.GOLD)).colspan(2).left().height(28).padBottom(4);
        p.add().width(36);
        p.add(label("보스", "default", Ui.GOLD)).colspan(3).left().height(28).padBottom(4).row();

        String[] bosses = {AbsorbService.BOSS_ORDER[0], AbsorbService.BOSS_ORDER[1], AbsorbService.BOSS_ORDER[2], "FINAL"};
        for (int row = 0; row < 6; row++) {
            nav.newRow();
            // 왼쪽: 도구 한 줄
            switch (row) {
                case 0:
                    toolRow(p, "캠프로 이동", g.fighting() ? "싸움 그만두고 캠프로" : "캠프로", () -> {
                        if (screen.devWarpToCamp()) close();
                    });
                    break;
                case 1:
                    toolRow(p, "광물 무한", screen.game.settings.devInfiniteOre ? "켜짐 (" + INFINITE_ORE + "개)" : "꺼짐", () -> {
                        screen.game.settings.devInfiniteOre = !screen.game.settings.devInfiniteOre;
                        screen.game.saveSettings();
                        if (screen.game.settings.devInfiniteOre) g.devFillOres(INFINITE_ORE);
                        rebuild();
                    });
                    break;
                case 2:
                    toolRow(p, "무적", screen.game.settings.devGodMode ? "켜짐" : "꺼짐", () -> {
                        screen.game.settings.devGodMode = !screen.game.settings.devGodMode;
                        screen.game.saveSettings();
                        g.devGodMode = screen.game.settings.devGodMode;
                        rebuild();
                    });
                    break;
                case 3:
                    toolRow(p, "이동 영역 보기", screen.game.settings.devShowWalkable ? "켜짐" : "꺼짐", () -> {
                        screen.game.settings.devShowWalkable = !screen.game.settings.devShowWalkable;
                        screen.game.saveSettings();
                        rebuild();
                    });
                    break;
                case 4:
                    toolRow(p, "가림 층 보기", screen.game.settings.devShowLayers ? "켜짐" : "꺼짐", () -> {
                        screen.game.settings.devShowLayers = !screen.game.settings.devShowLayers;
                        screen.game.saveSettings();
                        rebuild();
                    });
                    break;
                default:
                    masteryRow(p, g, s);
            }
            p.add().width(36);
            // 오른쪽: 보스 한 줄 (마지막 줄은 모두 부활)
            if (row < 4) {
                String id = bosses[row];
                boolean fin = "FINAL".equals(id);
                String name = fin ? g.balance.finalBoss.name : g.balance.dungeon(id).boss.name;
                p.add(label(name, "default", Ui.TEXT)).width(150).left().padRight(10);
                p.add(reviveButton(id, fin ? s.finalBossDefeated : s.bossDefeated(id), !fin && g.devRevived(id))).width(130).padRight(8);
                p.add(warpButton(id, name)).width(120).row();
            } else if (row == 4) {
                TextButton all = nav.add("모든 보스 부활", () -> {
                    int n = 0;
                    for (String id : AbsorbService.BOSS_ORDER) if (g.devReviveBoss(id)) n++;
                    if (g.devReviveBoss("FINAL")) n++;
                    screen.hud.toast(n > 0 ? "보스 " + n + "마리를 되살렸다" : "되살릴 보스가 없다");
                    rebuild();
                });
                all.setDisabled(g.fighting());
                p.add(all).colspan(3).fillX().row();
            } else {
                p.add().colspan(3).row();
            }
        }

        String note = g.fighting() ? "싸우는 중: 되살리기는 막힘 · 바로 가기와 캠프로는 싸움을 그만두고 간다 (다시 들어가면 처음부터)"
                : "되살려도 흡수한 힘과 열린 길은 그대로 · 보스방에 들어가면 처음부터 싸운다";
        p.add(label(note, "small", Ui.DIM)).colspan(6).height(22).padTop(4).padBottom(12).row();
        nav.newRow();
        p.add(nav.add("닫기", this::close)).colspan(6).width(200).height(46).padBottom(0);
    }

    private void toolRow(Table p, String name, String value, Runnable action) {
        p.add(label(name, "default", Ui.DIM)).width(130).left().padRight(10);
        p.add(nav.add(value, action)).width(250);
    }

    /** 강화 숙련도 (발표·테스트용): 한 단계씩 올리거나 마스터로, 0 으로 되돌린다. */
    private void masteryRow(Table p, GameSession g, GameState s) {
        com.yongsa.model.EnhanceService es = g.enhance;
        p.add(label("강화 숙련도", "default", Ui.DIM)).width(130).left().padRight(10);
        Table mastery = new Table();
        mastery.add(label("Lv " + s.masteryLevel + (es.masteryMaxed(s) ? " M" : ""), "default", Ui.GOLD)).width(64).left();
        TextButton up = nav.add("+1", () -> {
            es.addMasteryExp(s, es.masteryNeed(s.masteryLevel) - s.masteryExp);
            screen.save(false);
            rebuild();
        });
        up.setDisabled(es.masteryMaxed(s));
        mastery.add(up).width(52).height(ROW_H).padRight(6);
        mastery.add(nav.add("마스터", () -> {
            s.masteryLevel = es.masteryMaxLevel();
            s.masteryExp = 0;
            screen.save(false);
            rebuild();
        })).width(72).height(ROW_H).padRight(6);
        mastery.add(nav.add("0", () -> {
            s.masteryLevel = 0;
            s.masteryExp = 0;
            screen.save(false);
            rebuild();
        })).width(44).height(ROW_H);
        p.add(mastery).width(250).left();
    }

    private TextButton warpButton(String id, String name) {
        TextButton b = nav.add("바로 가기", () -> {
            if (screen.devWarpToBoss(id)) {
                close();
                screen.hud.toast(name + "의 방 입구로 간다");
            }
        });
        return b;
    }

    private TextButton reviveButton(String id, boolean defeated, boolean waiting) {
        GameSession g = screen.session;
        String text = waiting ? "기다리는 중" : defeated ? "되살리기" : "살아 있음";
        TextButton b = nav.add(text, () -> {
            if (g.devReviveBoss(id)) {
                String name = "FINAL".equals(id) ? g.balance.finalBoss.name : g.balance.dungeon(id).boss.name;
                screen.hud.toast(name + "이(가) 되살아났다");
            }
            rebuild();
        });
        b.setDisabled(!defeated || waiting || g.fighting());
        return b;
    }
}

package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Enemy;
import com.yongsa.model.CraftService;
import com.yongsa.model.GameState;
import com.yongsa.model.Progress;
import com.yongsa.screen.GameScreen;
import com.yongsa.world.GameWorld;
import com.yongsa.world.PlayerInput;
import com.yongsa.world.RoomDef;

import java.io.File;

/**
 * 개발용 자동 둘러보기: -Pyongsa.showcase=폴더 로 실행하면 주요 장면을 돌며 스크린샷을 남기고 종료한다.
 * 실제 플레이에는 쓰이지 않는다.
 */
class Showcase implements Runnable {
    private final YongsaGame g;
    private final File dir;
    private float t;
    private int step;
    private GameScreen gs;
    private final PlayerInput in = new PlayerInput();

    Showcase(YongsaGame g, String dir) {
        this.g = g;
        this.dir = new File(dir);
        this.dir.mkdirs();
    }

    @Override
    public void run() {
        t += Gdx.graphics.getDeltaTime();
        Screen s = g.getScreen();
        if (s instanceof GameScreen) gs = (GameScreen) s;
        switch (step) {
            case 0: at(1.5f, () -> shot("01_title")); break;
            case 1: at(2f, () -> {
                GameState st = GameState.newGame(42, g.balance.dataVersion, g.settings);
                g.switchTo(new GameScreen(g, st, null), true);
            }); break;
            case 2: at(4.5f, () -> shot("02_camp")); break;
            case 3: at(5f, () -> {
                gs.scriptedInput = in;
                put(world().map.entranceRoom("mine"), 0, -60);
            }); break;
            case 4: at(7f, () -> {
                GameWorld w = world();
                // 광맥 옆에서 채굴
                w.state.mineRoom(w.map.entranceRoom("mine").id).veins.forEach(v -> { });
                var v = w.state.mineRoom(w.map.entranceRoom("mine").id).veins.get(0);
                var o = w.map.vein(v.candidateId);
                w.hero.x = o.x();
                w.hero.y = o.y() + (o.y() > w.room.centerY() ? -26 : 26);
                in.interactHeld = true;
                aim(o.x(), o.y());
            }); break;
            case 5: at(7.6f, () -> shot("03_mining")); break;
            case 6: at(8.4f, () -> {
                in.interactHeld = false;
                GameState st = world().state;
                st.minerals += 20;
                CraftService.craft(g.balance, st, "sword");
                put(world().map.room("camp"), 160, 0);
                gs.debugOpenForge();
            }); break;
            case 7: at(9.2f, () -> shot("04_forge")); break;
            case 8: at(9.6f, () -> {
                gs.windows().closeAll();
                put(world().map.firstCombatRoom("mine"), 0, -40);
                in.attackHeld = false;
            }); break;
            case 9: at(11.0f, () -> {
                GameWorld w = world();
                if (w.combat != null && !w.combat.enemies.isEmpty()) {
                    Enemy e = w.combat.enemies.get(0);
                    aim(e.x, e.y);
                }
                in.attackHeld = true;
            }); break;
            case 10: when(() -> t > 13f || (world().hero.attack != null
                    && world().hero.attack.phase() == com.yongsa.combat.AttackInstance.Phase.ACTIVE), () -> shot("05_battle")); break;
            case 11: at(12.6f, () -> shot("06_battle2")); break;
            case 12: at(13f, () -> {
                in.attackHeld = false;
                GameWorld w = world();
                if (w.combat != null) w.combat.enemies.clear();
            }); break;
            case 13: at(14f, () -> {
                Progress.onBossDefeated(g.balance, world().state, "boss_mine");
                world().state.awakenGuideSeen = true;
                put(world().map.firstCombatRoom("fortress"), 0, -40);
            }); break;
            case 14: at(16.3f, () -> {
                GameWorld w = world();
                if (w.combat != null && !w.combat.enemies.isEmpty()) {
                    Enemy e = w.combat.enemies.get(0);
                    aim(e.x, e.y);
                    w.hero.shockCooldown = 0;
                }
                gs.debugShock();
            }); break;
            case 15: when(() -> t > 19.5f || (world().hero.attack != null && world().hero.attack.effectiveLength >= 0),
                    () -> shot("07_shockwave")); break;
            case 16: at(17.2f, () -> {
                world().combat = null;
                put(world().map.room("fortress_boss"), 0, -120);
            }); break;
            case 17: at(18.0f, () -> shot("08_boss_intro")); break;
            case 18: at(20.3f, () -> shot("09_boss_fight")); break;
            case 19: at(21.6f, () -> shot("10_boss_fight2")); break;
            case 20: at(22f, () -> {
                world().hero.hp = 100;
                gs.debugOpenPause();
            }); break;
            case 21: at(22.6f, () -> shot("11_pause")); break;
            case 22: at(23f, () -> {
                gs.windows().closeAll();
                world().combat = null;
                world().state.minerals = 30;
                gs.debugOpenBag();
            }); break;
            case 23: at(23.6f, () -> shot("12_bag")); break;
            case 24: at(24f, () -> {
                gs.windows().closeAll();
                world().state.defeatedBosses.add("boss_fortress");
                put(world().map.room("camp"), 0, 60);
            }); break;
            case 25: at(25.5f, () -> shot("13_camp_unlocked")); break;
            case 26: at(26f, () -> {
                world().state.defeatedBosses.add("boss_temple");
                put(world().map.room("mine_boss"), 0, -60);
            }); break;
            case 27: at(27.5f, () -> shot("14_arena_mine")); break;
            case 28: at(28f, () -> put(world().map.room("temple_boss"), 0, -60)); break;
            case 29: at(29.5f, () -> shot("15_arena_temple")); break;
            case 30: at(30f, () -> put(world().map.room("fortress_boss"), 0, -60)); break;
            case 31: at(31.5f, () -> shot("16_arena_fortress")); break;
            case 32: at(32f, () -> Gdx.app.exit()); break;
            default: break;
        }
    }

    private GameWorld world() {
        return gs.world();
    }

    private void put(RoomDef r, float dx, float dy) {
        GameWorld w = world();
        w.hero.x = r.centerX() + dx;
        w.hero.y = r.centerY() + dy;
        w.events.add(com.yongsa.world.WorldEvent.of(com.yongsa.world.WorldEvent.Type.TELEPORT));
        aim(w.hero.x, w.hero.y - 100);
    }

    private void aim(float x, float y) {
        in.aimX = x;
        in.aimY = y;
    }

    private void when(java.util.function.BooleanSupplier cond, Runnable r) {
        if (cond.getAsBoolean()) {
            r.run();
            step++;
            offset = t - lastAt;
        }
    }

    private float offset, lastAt;

    private void at(float time, Runnable r) {
        lastAt = time;
        if (t - offset >= time) {
            r.run();
            step++;
        }
    }

    private void shot(String name) {
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        byte[] px = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        for (int i = 4; i <= px.length; i += 4) px[i - 1] = (byte) 255;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        BufferUtils.copy(px, 0, pm.getPixels(), px.length);
        PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name + ".png").getAbsolutePath()), pm);
        pm.dispose();
    }
}

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
import com.yongsa.screen.GameScreen;
import com.yongsa.world.GameWorld;
import com.yongsa.world.MapObject;
import com.yongsa.world.PlayerInput;
import com.yongsa.world.RoomDef;
import com.yongsa.world.Waves;
import com.yongsa.world.WorldEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 개발용 자동 둘러보기: -Pyongsa.showcase=폴더 로 실행하면 주요 장면을 돌며 스크린샷을 남기고 종료한다.
 * 실제 플레이에는 쓰이지 않는다. title_bg.png 는 타이틀 배경용(화면 정보 없이 찍은 전투 장면)이며
 * assets/ui/title_bg.png 로 복사해 쓴다.
 */
class Showcase implements Runnable {
    private final YongsaGame g;
    private final File dir;
    private float t;
    private int step;
    private GameScreen gs;
    private final PlayerInput in = new PlayerInput();

    /** 한 장면: 앞 장면이 끝난 뒤 wait 초 기다렸다가 실행한다. */
    private static class Scene {
        final float wait;
        final Runnable run;

        Scene(float wait, Runnable run) {
            this.wait = wait;
            this.run = run;
        }
    }

    private final List<Scene> scenes = new ArrayList<>();

    Showcase(YongsaGame g, String dir) {
        this.g = g;
        this.dir = new File(dir);
        this.dir.mkdirs();
        script();
    }

    private void then(float wait, Runnable run) {
        scenes.add(new Scene(wait, run));
    }

    private void script() {
        then(1.5f, () -> shot("01_title"));
        then(0.3f, () -> {
            GameState st = GameState.newGame(42, g.balance.dataVersion, g.settings);
            st.awakenGuideSeen = true; // 안내 창이 장면을 가리지 않게
            g.switchTo(new GameScreen(g, st, null), true);
        });
        then(2.2f, () -> {
            gs.scriptedInput = in;
            shot("02_camp");
        });
        // 채굴
        then(0.3f, () -> put(world().map.entranceRoom("mine"), 0, -60));
        then(1.2f, () -> {
            GameWorld w = world();
            String room = w.map.entranceRoom("mine").id;
            MapObject o = w.map.vein(w.state.mineRoom(room).veins.get(0).candidateId);
            w.hero.x = o.x();
            w.hero.y = o.y() + (o.y() > w.room.centerY() ? -26 : 26);
            in.interactHeld = true;
            aim(o.x(), o.y());
        });
        then(0.4f, () -> shot("03_mining"));
        // 대장간
        then(0.6f, () -> {
            in.interactHeld = false;
            GameState st = world().state;
            for (int i = 0; i < st.ore.length; i++) st.ore[i] = 20;
            CraftService.craft(g.balance, st, "sword");
            put(world().map.room("camp"), 60, -70);
            gs.debugOpenForge();
        });
        then(0.7f, () -> shot("04_forge"));
        // 첫 전투방: 소환 표식 → 전투
        then(0.3f, () -> {
            gs.windows().closeAll();
            world().hero.invuln = 600;
            put(world().map.firstCombatRoom("mine"), 0, -40);
        });
        then(0.4f, () -> shot("05_spawn_marks"));
        then(1.2f, this::attackNearest);
        then(0.5f, () -> shot("06_battle"));
        then(0.3f, () -> {
            in.attackHeld = false;
            killAll();
            world().hero.x = world().room.centerX() - 70;
            world().hero.y = world().room.centerY() - 30;
        });
        then(2.6f, () -> shot("07_chest"));
        // 깊은 방: 원거리 적과 강화 개체
        then(0.2f, () -> {
            world().state.weapon("sword").level = 10;
            put(deepRoom("mine"), 0, -40);
        });
        then(2.6f, () -> shot("08_ranged_mine"));
        then(0.2f, () -> {
            killAll();
            world().state.defeatedBosses.add("boss_mine");
            put(deepRoom("fortress"), 0, -40);
        });
        then(2.4f, this::attackNearest);
        then(0.5f, () -> shot("09_fortress_fight"));
        // 충격파: 몸에서 퍼지는 파동
        then(0.1f, () -> {
            world().devUnlockAll = true;
            world().hero.shockCooldown = 0;
            world().hero.attack = null;
            in.attackHeld = false;
            in.shockwavePressed = true;
        });
        then(0.3f, () -> {
            shot("09b_shockwave");
            world().devUnlockAll = false;
        });
        then(0.2f, () -> {
            in.attackHeld = false;
            killAll();
            world().state.defeatedBosses.add("boss_fortress");
            put(deepRoom("temple"), 0, -40);
        });
        then(2.6f, () -> shot("10_temple_ranged"));
        // 타이틀 배경: 화면 정보 없이 찍는다
        then(0.2f, () -> {
            gs.hideHud = true;
            attackNearest();
        });
        then(0.45f, () -> shot("title_bg"));
        then(0.1f, () -> {
            gs.hideHud = false;
            in.attackHeld = false;
            killAll();
        });
        // 제단
        then(0.6f, () -> {
            world().state.gold = 300;
            standAtShrine("fortress");
        });
        then(1.0f, () -> shot("11_shrine"));
        then(0.2f, () -> in.interactPressed = true);
        then(0.8f, () -> {
            in.interactPressed = false;
            shot("12_blessing");
        });
        // 캠프와, 주머니에서 꺼내는 대장간
        then(0.5f, () -> put(world().map.room("camp"), 60, -70));
        then(1.2f, () -> shot("13_camp_after"));
        then(0.1f, () -> gs.keyForTest(com.badlogic.gdx.Input.Keys.I));
        then(0.22f, () -> shot("14_forge_taking_out"));
        then(0.6f, () -> shot("15_forge_open"));
        then(0.2f, () -> {
            gs.windows().closeAll();
            gs.keyForTest(com.badlogic.gdx.Input.Keys.F1);
        });
        then(0.5f, () -> shot("16_dev_menu"));
        then(0.3f, () -> gs.windows().closeAll());
        // 망치 2연타: 옆으로 쓸기 → 머리 위로 들어 올려 내려찍기
        then(0.2f, () -> {
            if (!world().state.owns("hammer")) world().state.weapons.add(new com.yongsa.model.WeaponState("hammer"));
            world().state.equipped = "hammer";
            world().hero.attack = null;
            world().hero.combo = 0;
            in.moveX = 1;
        });
        then(0.1f, () -> {
            in.moveX = 0;
            in.attackHeld = true;
        });
        float hc = 0.78f, hw = 0.21f; // 망치의 한 번 휘두르는 시간과 준비 시간 (balance.json)
        then(hw + 0.05f, () -> shot("18_hammer_sweep"));
        then(hc - 0.05f + 0.03f, () -> shot("19_hammer_raise"));
        then(hw + 0.05f, () -> shot("20_hammer_slam"));
        then(0.1f, () -> {
            in.attackHeld = false;
            world().state.equipped = "sword";
        });
        // 보스 3종: 등장, 공격 예고 자세, 타격 자세, 다음 공격, 사망
        for (String zone : new String[]{"mine", "fortress", "temple"}) {
            then(0.4f, () -> {
                world().combat = null;
                world().state.defeatedBosses.remove("boss_" + zone);
                put(world().map.room(zone + "_boss"), 0, -110);
            });
            then(1.0f, () -> shot("boss_" + zone + "_1_intro"));
            then(0.2f, () -> waitFor = Enemy.State.TELEGRAPH);
            then(0.3f, () -> shot("boss_" + zone + "_2_telegraph"));
            then(0f, () -> waitFor = Enemy.State.ATTACK);
            then(0.08f, () -> shot("boss_" + zone + "_3_attack"));
            then(0.3f, () -> waitFor = Enemy.State.TELEGRAPH);
            then(0.5f, () -> shot("boss_" + zone + "_4_next_telegraph"));
            then(0f, () -> waitFor = Enemy.State.ATTACK);
            then(0.1f, () -> shot("boss_" + zone + "_5_next_attack"));
            then(0.2f, () -> {
                Enemy boss = world().combat == null ? null : world().combat.bossEnemy();
                if (boss != null) boss.hp = 0;
            });
            then(0.6f, () -> shot("boss_" + zone + "_6_dying"));
            then(1.4f, () -> { });
        }
        then(4.5f, () -> shot("17_ending_story"));
        then(0.3f, () -> Gdx.app.exit());
    }

    /** 다음 장면으로 넘어가기 전에 보스가 이 상태가 되기를 기다린다 (최대 8초). */
    private Enemy.State waitFor;

    @Override
    public void run() {
        float dt = Gdx.graphics.getDeltaTime();
        Screen s = g.getScreen();
        if (s instanceof GameScreen) gs = (GameScreen) s;
        if (step >= scenes.size()) return;
        t += dt;
        if (waitFor != null) {
            Enemy boss = gs == null || world().combat == null ? null : world().combat.bossEnemy();
            if (boss != null && boss.state != waitFor && t < 8f) return;
            waitFor = null;
            t = 0;
        }
        Scene sc = scenes.get(step);
        if (t < sc.wait) return;
        t = 0;
        step++;
        sc.run.run();
    }

    private GameWorld world() {
        return gs.world();
    }

    /** 첫 웨이브에 원거리 적이 있는, 그 구역의 깊은 전투방. */
    private RoomDef deepRoom(String zone) {
        GameWorld w = world();
        RoomDef best = null;
        for (RoomDef r : w.map.zoneRooms(zone)) {
            if (r.kind != RoomDef.Kind.ROOM || !r.enemies || r.guard || w.state.isRoomCleared(r.id)) continue;
            boolean ranged = Waves.plan(g.balance, r, w.state.dungeonSeed).get(0).stream()
                    .anyMatch(sp -> "shoot".equals(sp.def.kind) || "mark".equals(sp.def.kind));
            if (ranged && (best == null || r.depth > best.depth)) best = r;
        }
        return best != null ? best : w.map.firstCombatRoom(zone);
    }

    private void attackNearest() {
        GameWorld w = world();
        if (w.combat != null && !w.combat.enemies.isEmpty()) {
            Enemy e = w.combat.enemies.get(0);
            w.hero.x = e.x - 60;
            w.hero.y = e.y;
            aim(e.x, e.y + 8);
        }
        in.attackHeld = true;
    }

    private void killAll() {
        GameWorld w = world();
        if (w.combat == null) return;
        // 남은 웨이브까지 차례로 끝낸다
        for (int i = 0; i < 400 && w.combat != null; i++) {
            for (Enemy e : w.combat.enemies) e.hp = 0;
            w.step(GameWorld.STEP, in);
        }
        w.events.add(WorldEvent.of(WorldEvent.Type.TELEPORT));
    }

    private void put(RoomDef r, float dx, float dy) {
        GameWorld w = world();
        w.hero.x = r.centerX() + dx;
        w.hero.y = r.centerY() + dy;
        w.events.add(WorldEvent.of(WorldEvent.Type.TELEPORT));
        aim(w.hero.x, w.hero.y - 100);
    }

    /** 그 구역의 제단 앞에 세운다 (지키는 방은 돌파한 것으로 둔다). */
    private void standAtShrine(String zone) {
        GameWorld w = world();
        for (MapObject o : w.map.interacts) {
            if (!o.type.equals("shrine") || !o.prop("zone").equals(zone)) continue;
            if (!w.state.clearedRooms.contains(o.prop("room"))) w.state.clearedRooms.add(o.prop("room"));
            w.state.openedChests.add(o.prop("room"));
            w.hero.x = o.x() - 44;
            w.hero.y = o.bounds.y - 10;
            w.events.add(WorldEvent.of(WorldEvent.Type.TELEPORT));
            aim(o.x(), o.y());
            return;
        }
    }

    private void aim(float x, float y) {
        in.aimX = x;
        in.aimY = y;
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

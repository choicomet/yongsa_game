package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.yongsa.KeyBindings;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.yongsa.YongsaGame;
import com.yongsa.model.AbsorbService;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.render.WorldRenderer;
import com.yongsa.save.SaveMapper;
import com.yongsa.ui.BagWindow;
import com.yongsa.ui.ConfirmDialog;
import com.yongsa.ui.ControlsWindow;
import com.yongsa.ui.DevToolsWindow;
import com.yongsa.ui.ForgeWindow;
import com.yongsa.ui.Hud;
import com.yongsa.ui.MapWindow;
import com.yongsa.ui.PauseMenu;
import com.yongsa.ui.ResultWindow;
import com.yongsa.ui.SettingsWindow;
import com.yongsa.world.Room;

import java.util.function.Consumer;

/**
 * 플레이 화면: 키 입력 → GameSession 요청 → 이벤트 → HUD·창·소리.
 * 창이 열리면 게임 시간(이동·전투·쿨타임)은 멈추고, UI 시간(버튼·망치질·각성 연출)은 계속 흐른다 (구현안 2장).
 */
public class GameScreen extends BaseScreen {

    private static final float MAX_DELTA = 1f / 20f;

    private final GameSession s;
    private final WorldRenderer renderer;
    private final Hud hud;
    private final Controls controls = new Controls();
    private final Consumer<GameEvent> listener = this::onEvent;

    private final ForgeWindow forge;
    private final BagWindow bag;
    private final MapWindow map;
    private final DevToolsWindow devTools;
    private final PauseMenu pause;
    private float endingTimer = -1;
    /** 마왕에게 쓰러졌을 때: 마왕의 말을 다 보여준 뒤 결과창. */
    private float deathTimer = -1;

    public GameScreen(YongsaGame game, GameSession session) {
        super(game);
        this.s = session;
        s.setEffectSpeed(game.settings.effectSpeed());
        s.addListener(listener);
        renderer = new WorldRenderer(s, game.assets, game.fonts.number);
        hud = new Hud(stage, game.skin, s, game.assets);
        forge = new ForgeWindow(game.skin, s, game.assets, windows::open, game.settings::effectSpeed, game.sounds::play);
        bag = new BagWindow(game.skin, s, game.assets);
        map = new MapWindow(game.skin, s);
        bag.closeKey = k -> game.settings.keys().is(KeyBindings.Action.BAG, k);
        applyKeyNames();
        map.closeKey = k -> game.settings.keys().is(KeyBindings.Action.MAP, k);
        devTools = new DevToolsWindow(game.skin, s, then -> {
            windows.closeAll();
            then.run();
        }, () -> {
            if (windows.any()) windows.top().refresh();
        });
        pause = new PauseMenu(game.skin, s, new PauseMenu.Actions() {
            @Override public void bag() { windows.open(bag); }

            @Override
            public void save() {
                if (saveNow()) hud.toast("저장했다.");
            }

            @Override public void settings() { windows.open(new SettingsWindow(game.skin, game.settings, game.sounds::applyVolume)); }

            @Override public void controls() { windows.open(new ControlsWindow(game.skin, game.settings, GameScreen.this::applyKeyNames)); }

            @Override
            public void title() {
                windows.open(new ConfirmDialog(game.skin, "타이틀로", "타이틀로 돌아갈까?\n마지막 저장 이후의 진행은 사라진다.",
                        "타이틀로", false, game::showTitle, "취소", () -> { }));
            }
        });
        if (s.playTime() < 1f) hud.toast("왼쪽 길로 가면 무너진 갱도가 있다. 광물방에서 흑철석을 모으자.", Color.WHITE, 5f);
        else hud.toast("이어서 시작한다.");
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(new GameInput(), stage));
    }

    private boolean saveNow() {
        if (!s.canSaveNow()) return false;
        game.saves.save(SaveMapper.capture(s));
        return true;
    }

    // ================================================================== 이벤트

    private void onEvent(GameEvent e) {
        switch (e.type()) {
            case TOAST, ROOM_CLEARED, GATE_SEALED -> hud.toast(e.text());
            case SOUND -> game.sounds.play(e.id());
            case CAMERA_SHAKE -> renderer.follow.shake(e.amount() / 10f, (Float) e.data());
            case OPEN_FORGE -> windows.open(forge);
            case SAVE_REQUEST -> saveNow();
            case TELEPORTED -> renderer.snapCamera();
            case ROOM_LOCKED -> {
                Room r = (Room) e.data();
                if (r.kind != com.yongsa.world.RoomKind.BOSS) hud.toast("문이 잠겼다! 몬스터를 모두 쓰러뜨리자.", Color.ORANGE, 1.6f);
            }
            case ORB_PICKED -> hud.toast("보스의 정수를 얻었다! (" + e.amount() + " / 3)"
                    + (e.amount() == 3 ? "\n캠프 바닥의 문양이 정수를 부르고 있다…" : ""), new Color(0.8f, 0.7f, 1f, 1f), 3.5f);
            case BOSS_CONFIRM -> {
                Room r = (Room) e.data();
                String boss = s.balance.boss(s.balance.dungeon(r.dungeon).boss).name;
                windows.open(new ConfirmDialog(game.skin, "보스방", "보스방입니다. 들어갈까요?\n" + boss + "\n권장 무기: " + s.recommendedFor(r.dungeon),
                        "들어간다", true, () -> s.answerBossConfirm(true), "돌아간다", () -> s.answerBossConfirm(false)));
            }
            case BOSS_DEFEATED -> {
                AbsorbService.BossAbsorb r = (AbsorbService.BossAbsorb) e.data();
                if (r != null) windows.open(ResultWindow.bossCleared(game.skin, s, game.assets, r, () -> { }));
            }
            case HERO_DIED -> {
                if (s.inArena()) deathTimer = 2.4f;
                else openDeathWindow();
            }
            case SPEECH -> hud.speech(e.id(), e.text(), (Float) e.data());
            case CAMP_BRIGHTER -> { }
            case CUTSCENE_STARTED -> windows.closeAll();
            case ARENA_ENTERED -> {
                renderer.startArenaFade();
                hud.toast("심연 깊은 곳… 마왕이 기다리고 있었다!", new Color(1f, 0.45f, 0.5f, 1f), 3.5f);
            }
            case FINAL_DEFEATED -> {
                hud.toast("마왕을 쓰러뜨렸다!", Color.GOLD, 3f);
                endingTimer = 3.5f;
            }
            default -> { }
        }
    }

    private void openDeathWindow() {
        windows.open(ResultWindow.died(game.skin, () -> {
            s.respawn();
            renderer.snapCamera();
        }));
    }

    // ================================================================== 한 프레임

    @Override
    public void render(float delta) {
        delta = Math.min(delta, MAX_DELTA);
        boolean paused = windows.any();
        if (!paused) {
            readMovement();
            s.update(delta, controls);
        }
        controls.clearPresses();
        updateMusic();
        renderer.render(paused ? 0 : delta);

        hud.update(delta);
        stage.act(delta);
        stage.getViewport().apply();
        stage.draw();

        if (deathTimer > 0) {
            deathTimer -= delta;
            if (deathTimer <= 0) openDeathWindow();
        }
        if (endingTimer > 0 && !windows.any()) {
            endingTimer -= delta;
            if (endingTimer <= 0) game.showEnding(s);
        }
    }

    private void readMovement() {
        if (s.cutscene() != null) {
            controls.moveX = controls.moveY = 0;
            controls.guard = false;
            return;
        }
        KeyBindings k = game.settings.keys();
        controls.guard = held(k, KeyBindings.Action.GUARD);
        float x = 0, y = 0;
        if (held(k, KeyBindings.Action.MOVE_LEFT)) x -= 1;
        if (held(k, KeyBindings.Action.MOVE_RIGHT)) x += 1;
        if (held(k, KeyBindings.Action.MOVE_DOWN)) y -= 1;
        if (held(k, KeyBindings.Action.MOVE_UP)) y += 1;
        controls.moveX = x;
        controls.moveY = y;
    }

    /** 이 동작의 키 중 하나라도 누르고 있나. */
    private static boolean held(KeyBindings k, KeyBindings.Action a) {
        for (int i = 0; i < KeyBindings.SLOTS; i++) {
            int key = k.key(a, i);
            if (key != KeyBindings.NONE && Gdx.input.isKeyPressed(key)) return true;
        }
        return false;
    }

    /** 바꾼 조작 키를 화면 안내(스킬 칸, 키 안내, E 안내)에 반영한다. */
    public void applyKeyNames() {
        KeyBindings k = game.settings.keys();
        s.interactKey = k.primaryName(KeyBindings.Action.INTERACT);
        hud.applyKeys(k);
    }

    private void updateMusic() {
        if (s.cutscene() != null) {
            game.sounds.playMusic("bgm_unseal");
            return;
        }
        if (s.inArena()) game.sounds.playMusic(s.progress.finalDefeated() ? "bgm_ending" : "bgm_final");
        else if (s.activeBoss() != null) game.sounds.playMusic("bgm_boss");
        else if (s.inCamp() || s.inVillage()) game.sounds.playMusic("bgm_camp");
        else if (s.overlapRoom() != null) game.sounds.playMusic("bgm_" + s.overlapRoom().dungeon);
    }

    @Override
    public void resize(int w, int h) {
        super.resize(w, h);
        renderer.resize(w, h);
        hud.resize(stage.getWidth(), stage.getHeight());
    }

    @Override
    public void pause() {
        if (!windows.any()) windows.open(pause);
    }

    @Override
    public void dispose() {
        s.removeListener(listener);
        super.dispose();
        renderer.dispose();
        hud.dispose();
        map.dispose();
    }

    // ================================================================== 키 입력 (게임이 UI보다 먼저 받는다)

    private class GameInput extends InputAdapter {
        @Override
        public boolean keyDown(int key) {
            // F1: 개발자 도구 (광물 무한·무적·바로 가기). 대장간 등 다른 창 위에서도 열린다.
            if (key == Input.Keys.F1) {
                if (windows.top() instanceof DevToolsWindow dev) dev.close();
                else if (s.cutscene() == null) windows.open(devTools);
                return true;
            }
            if (windows.any()) {
                windows.keyDown(key);
                return true;
            }
            if (s.cutscene() != null) return true;
            if (key == Input.Keys.ESCAPE) {
                windows.open(pause);
                return true;
            }
            // 조작 키는 사용자가 바꿀 수 있다 (Esc 메뉴 → 조작 키)
            KeyBindings.Action a = game.settings.keys().owner(key);
            if (a == null) return false;
            switch (a) {
                case MAP -> windows.open(map);
                case BAG -> windows.open(bag);
                case ATTACK -> controls.attack = true;
                case WHIRL -> controls.whirl = true;
                case DASH -> controls.dash = true;
                case DODGE -> controls.dodge = true;
                case INTERACT -> controls.interact = true;
                default -> {
                    return false; // 이동·막기는 누르고 있는지 readMovement 에서 본다
                }
            }
            return true;
        }
    }
}

package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.ui.BagWindow;
import com.yongsa.ui.ConfirmDialog;
import com.yongsa.ui.DevMenu;
import com.yongsa.ui.ForgeWindow;
import com.yongsa.ui.Hud;
import com.yongsa.ui.MessageWindow;
import com.yongsa.ui.ModalStack;
import com.yongsa.ui.PauseMenu;
import com.yongsa.ui.Ui;
import com.yongsa.ui.UnsealScene;
import com.yongsa.world.CameraFollow;

/**
 * 플레이 화면. 창이 열려 있으면 게임 시간(이동, 전투, 쿨타임)은 멈추고 UI 시간(버튼, 연출)만 흐른다:
 * Stage.act 는 항상 부르고 GameSession.update 는 창이 없을 때만 부른다.
 */
public class GameScreen extends ScreenAdapter {
    public final YongsaGame game;
    public final GameSession session;
    public final Hud hud;
    public final ModalStack modals;

    private final OrthographicCamera worldCam = new OrthographicCamera();
    private final FitViewport worldViewport = new FitViewport(YongsaGame.WIDTH, YongsaGame.HEIGHT, worldCam);
    private final Stage stage;
    private final WorldRenderer renderer;
    private final CameraFollow follow = new CameraFollow();
    private final Controls controls = new Controls();
    private boolean interactPressed, rollPressed, attackPressed;
    private float endingDelay = -1;
    /** 화면 흔들림 세기 (0~1), 타격 순간 잠깐 멈추는 시간, 화면 전환용 검은 막. */
    private float trauma, hitStop, shakeTime;
    private float fade = 1f, fadeTarget = 0f;
    private boolean warping;
    /** 한 칸의 그림 픽셀이 화면 3픽셀이 되도록 맵을 1.5배로 확대해서 본다. */
    public static final float ZOOM = 2f / 3f;

    public GameScreen(YongsaGame game, GameState state) {
        this.game = game;
        this.session = new GameSession(game.balance, game.newMap(), state, System.nanoTime());
        this.stage = new Stage(new FitViewport(YongsaGame.WIDTH, YongsaGame.HEIGHT), game.batch);
        this.modals = new ModalStack(stage);
        this.renderer = new WorldRenderer(session, game.art, game.fonts, game.batch, game.shapes);
        this.hud = new Hud(session, game.art, game.fonts, game.batch, game.shapes);
        worldCam.zoom = ZOOM;
        if (session.inCamp()) hud.banner("용사의 캠프");
    }

    @Override
    public void show() {
        InputAdapter keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.F1 && endingDelay < 0) {
                    toggleDevMenu();
                    return true;
                }
                if (!modals.isEmpty()) {
                    boolean handled = modals.keyDown(keycode);
                    if (handled) game.sfx.play("select");
                    return true;
                }
                if (endingDelay >= 0) return true;
                switch (keycode) {
                    case Input.Keys.ESCAPE:
                        openPause();
                        return true;
                    case Input.Keys.I:
                        openBag();
                        return true;
                    case Input.Keys.M:
                    case Input.Keys.TAB:
                        hud.toggleMap();
                        game.sfx.play("select");
                        return true;
                    case Input.Keys.E:
                        interactPressed = true;
                        return true;
                    case Input.Keys.SPACE:
                    case Input.Keys.SHIFT_LEFT:
                    case Input.Keys.K:
                        rollPressed = true;
                        return true;
                    case Input.Keys.J:
                        attackPressed = true;
                        return true;
                    default:
                        return false;
                }
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(keys, stage));
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 1 / 20f);
        boolean paused = !modals.isEmpty() || endingDelay >= 0 || warping;
        boolean frozen = hitStop > 0;
        hitStop -= dt;
        session.devGodMode = game.settings.devGodMode;
        if (game.settings.devInfiniteOre) session.devFillOres(DevMenu.INFINITE_ORE);
        if (!paused && !frozen) {
            readControls();
            session.update(dt, controls);
        }
        if (!frozen) interactPressed = rollPressed = attackPressed = false;
        handleEvents();
        if (!paused) renderer.update(frozen ? dt * 0.15f : dt);
        hud.update(dt);
        updateFade(delta);
        trauma = Math.max(0f, trauma - dt * 1.7f);
        shakeTime += dt;
        if (endingDelay >= 0) {
            endingDelay -= delta;
            renderer.update(dt);
            if (endingDelay < 0) {
                game.setScreen(new EndingScreen(game, session.state));
                dispose();
                return;
            }
        }

        int T = game.balance.tileSize;
        follow.update(session.hero.x, session.hero.y + 16, dt, worldCam.viewportWidth * ZOOM, worldCam.viewportHeight * ZOOM,
                session.map.width * T, session.map.height * T);
        float shake = trauma * trauma * 12f;
        float ox = shake * (MathUtils.sin(shakeTime * 47f) + MathUtils.sin(shakeTime * 83f) * 0.5f) / 1.5f;
        float oy = shake * (MathUtils.cos(shakeTime * 53f) + MathUtils.sin(shakeTime * 71f) * 0.5f) / 1.5f;
        // 화면 픽셀 단위로 맞춰 그림이 떨리지 않게 한다
        worldCam.position.set(Math.round((follow.x + ox) / ZOOM) * ZOOM, Math.round((follow.y + oy) / ZOOM) * ZOOM, 0);
        worldCam.update();

        renderer.renderLights(worldCam);
        Gdx.gl.glClearColor(0.02f, 0.018f, 0.03f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        worldViewport.apply();
        renderer.render(worldCam);
        stage.getViewport().apply();
        hud.render(stage.getCamera().combined, YongsaGame.WIDTH, YongsaGame.HEIGHT);
        hud.renderFade(fade);
        stage.act(delta);
        stage.draw();
    }

    private void updateFade(float delta) {
        float speed = warping ? 3f : 1.8f;
        if (fade < fadeTarget) fade = Math.min(fadeTarget, fade + delta * speed);
        else if (fade > fadeTarget) fade = Math.max(fadeTarget, fade - delta * speed);
        if (warping && fade >= 1f) {
            session.warpToCamp();
            follow.snap();
            hud.banner("용사의 캠프");
            warping = false;
            fadeTarget = 0f;
        }
    }

    private void shake(float amount) {
        if (!game.settings.screenShake) return;
        trauma = Math.min(1f, trauma + amount);
    }

    private void stop(float seconds) {
        hitStop = Math.max(hitStop, seconds);
    }

    private void readControls() {
        controls.clear();
        boolean l = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean r = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        boolean u = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean d = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        controls.moveX = (r ? 1 : 0) - (l ? 1 : 0);
        controls.moveY = (u ? 1 : 0) - (d ? 1 : 0);
        controls.attack = attackPressed || Gdx.input.isKeyPressed(Input.Keys.J);
        controls.roll = rollPressed;
        controls.interact = interactPressed;
    }

    private void handleEvents() {
        for (GameEvent e : session.pollEvents()) {
            renderer.onEvent(e);
            switch (e.type) {
                case HIT_FX:
                    if (e.tone == GameEvent.Tone.HERO_HIT) {
                        shake(0.45f);
                        stop(0.06f);
                        hud.hurt();
                    } else {
                        shake(e.boss ? 0.14f : 0.1f);
                        stop(0.035f);
                    }
                    break;
                case MONSTER_DIED:
                    game.sfx.play("kill");
                    shake(e.boss ? 0.9f : 0.2f);
                    stop(e.boss ? 0.3f : 0.07f);
                    break;
                case SLAM_FX:
                    shake(0.55f);
                    break;
                case ORE_GAINED:
                    shake(0.12f);
                    break;
                case BOSS_FIGHT_START:
                    game.sfx.play("roar");
                    shake(0.5f);
                    hud.bossIntro(e.text, e.boss);
                    break;
                case BOSS_ENRAGED:
                    shake(0.6f);
                    hud.banner(e.text + " · 분노");
                    break;
                case WARP:
                    warping = true;
                    fadeTarget = 1f;
                    break;
                case TOAST:
                    hud.toast(e.text);
                    break;
                case SOUND:
                    game.sfx.play(e.text);
                    break;
                case ROOM_LOCKED:
                    hud.toast("문이 잠겼다! 몬스터를 모두 쓰러뜨리자");
                    break;
                case OPEN_FORGE:
                    modals.push(new ForgeWindow(this));
                    break;
                case BED_USED:
                    hud.toast("푹 쉬었다. 체력이 가득 찼다");
                    hud.toast("보스의 사념이 던전에 몬스터와 광맥을 다시 채웠다");
                    break;
                case AUTOSAVE:
                    save(false);
                    break;
                case BOSS_DEFEATED:
                    showBossResult(e);
                    break;
                case FINAL_DEFEATED:
                    hud.banner("마왕을 쓰러뜨렸다!");
                    game.sfx.play("unseal");
                    endingDelay = 2.5f;
                    break;
                case HERO_DIED:
                    game.sfx.play("death");
                    follow.snap();
                    modals.push(new MessageWindow(game.skin, "쓰러졌다…", Ui.RED, new ConfirmDialog.Line[]{
                            new ConfirmDialog.Line("캠프로 돌아갑니다.", null),
                            new ConfirmDialog.Line("무기와 광물은 그대로다. 싸우던 방만 처음 상태로 돌아간다.", Ui.DIM)
                    }, "확인"));
                    break;
                case UNSEAL_READY:
                    modals.push(new UnsealScene(this));
                    break;
                case ENTERED_DUNGEON:
                    hud.banner(e.text);
                    break;
                default:
                    break;
            }
        }
    }

    private void showBossResult(GameEvent e) {
        game.sfx.play("awaken");
        int maxHp = session.rules.maxHp(session.state);
        ConfirmDialog.Line[] lines = {
                new ConfirmDialog.Line(e.text + "의 힘을 흡수했다.", Ui.GOLD),
                new ConfirmDialog.Line("최대 체력 +" + e.hpGain + "  (이제 " + maxHp + ")", Ui.GREEN),
                new ConfirmDialog.Line("보상: " + e.ore.displayName + " " + e.amount + "개", null),
                new ConfirmDialog.Line("열림: " + e.opened, Ui.BLUE),
                new ConfirmDialog.Line(nextWeaponHint(), Ui.DIM),
                new ConfirmDialog.Line("방 가운데 생긴 귀환 포탈(E)로 캠프에 바로 돌아갈 수 있다", Ui.PURPLE)
        };
        modals.push(new MessageWindow(game.skin, "던전 클리어!", Ui.GOLD, lines, "확인"));
    }

    private String nextWeaponHint() {
        for (String id : com.yongsa.model.AbsorbService.BOSS_ORDER) {
            if (session.state.bossDefeated(id)) continue;
            com.yongsa.config.Balance.DungeonDef d = game.balance.dungeon(id);
            com.yongsa.config.Balance.WeaponDef w = game.balance.weaponForOre(d.ore);
            return "다음에 만들 무기: " + w.name + " (" + com.yongsa.model.OreType.valueOf(d.ore).displayName + " " + w.craftCost + "개)";
        }
        return "이제 캠프로 돌아가면 성검의 봉인을 풀 수 있다";
    }

    /** 개발용 메뉴 (F1): 보스 부활, 광물 무한, 무적. */
    public void toggleDevMenu() {
        if (modals.top() instanceof DevMenu) modals.top().close();
        else modals.push(new DevMenu(this));
        game.sfx.play("select");
    }

    public void openPause() {
        modals.push(new PauseMenu(this));
    }

    public void openBag() {
        modals.push(new BagWindow(this));
    }

    /** 이어하기 위치를 정하고 저장한다. */
    public void save(boolean announce) {
        session.prepareSave();
        boolean ok = game.save(session.state);
        if (!ok) hud.toast("저장하지 못했습니다");
        else if (announce) hud.toast("저장했다");
    }

    public void toTitle() {
        game.setScreen(new TitleScreen(game));
        dispose();
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        renderer.dispose();
    }
}

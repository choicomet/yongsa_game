package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.ui.BagWindow;
import com.yongsa.ui.ConfirmDialog;
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
    private boolean interactPressed, spinPressed, dashPressed, attackPressed;
    private float endingDelay = -1;

    public GameScreen(YongsaGame game, GameState state) {
        this.game = game;
        this.session = new GameSession(game.balance, game.newMap(), state, System.nanoTime());
        this.stage = new Stage(new FitViewport(YongsaGame.WIDTH, YongsaGame.HEIGHT), game.batch);
        this.modals = new ModalStack(stage);
        this.renderer = new WorldRenderer(session, game.art, game.fonts, game.batch, game.shapes);
        this.hud = new Hud(session, game.art, game.fonts, game.batch, game.shapes);
        if (session.inCamp()) hud.banner("용사의 캠프");
    }

    @Override
    public void show() {
        InputAdapter keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
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
                    case Input.Keys.E:
                        interactPressed = true;
                        return true;
                    case Input.Keys.K:
                        spinPressed = true;
                        return true;
                    case Input.Keys.L:
                        dashPressed = true;
                        return true;
                    case Input.Keys.J:
                    case Input.Keys.SPACE:
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
        boolean paused = !modals.isEmpty() || endingDelay >= 0;
        if (!paused) {
            readControls();
            session.update(dt, controls);
        }
        interactPressed = spinPressed = dashPressed = attackPressed = false;
        handleEvents();
        if (!paused) renderer.update(dt);
        hud.update(dt);
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
        follow.update(session.hero.x, session.hero.y + 16, dt, worldCam.viewportWidth, worldCam.viewportHeight,
                session.map.width * T, session.map.height * T);
        worldCam.position.set(Math.round(follow.x), Math.round(follow.y), 0);
        worldCam.update();

        Gdx.gl.glClearColor(0.04f, 0.035f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        worldViewport.apply();
        renderer.render(worldCam);
        stage.getViewport().apply();
        hud.render(stage.getCamera().combined, YongsaGame.WIDTH, YongsaGame.HEIGHT);
        stage.act(delta);
        stage.draw();
    }

    private void readControls() {
        controls.clear();
        boolean l = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean r = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);
        boolean u = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
        boolean d = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
        controls.moveX = (r ? 1 : 0) - (l ? 1 : 0);
        controls.moveY = (u ? 1 : 0) - (d ? 1 : 0);
        controls.attack = attackPressed || Gdx.input.isKeyPressed(Input.Keys.J) || Gdx.input.isKeyPressed(Input.Keys.SPACE);
        controls.spin = spinPressed;
        controls.dash = dashPressed;
        controls.interact = interactPressed;
    }

    private void handleEvents() {
        for (GameEvent e : session.pollEvents()) {
            switch (e.type) {
                case TOAST:
                    hud.toast(e.text);
                    break;
                case FLOAT_TEXT:
                    renderer.addFloat(e);
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
                new ConfirmDialog.Line(nextWeaponHint(), Ui.DIM)
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
    }
}

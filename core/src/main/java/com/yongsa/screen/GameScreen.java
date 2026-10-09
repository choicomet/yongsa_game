package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.config.Balance;
import com.yongsa.gfx.Effects;
import com.yongsa.gfx.WorldRenderer;
import com.yongsa.model.GameState;
import com.yongsa.model.Progress;
import com.yongsa.ui.BagWindow;
import com.yongsa.ui.ForgeWindow;
import com.yongsa.ui.GameActions;
import com.yongsa.ui.Hud;
import com.yongsa.ui.SettingsWindow;
import com.yongsa.ui.Windows;
import com.yongsa.world.DungeonGenerator;
import com.yongsa.world.DungeonMap;
import com.yongsa.world.FixedStepper;
import com.yongsa.world.GameWorld;
import com.yongsa.world.PlayerInput;
import com.yongsa.world.WorldEvent;

/**
 * 게임 화면. 시뮬레이션은 고정 간격으로만 진행하고, 창이 하나라도 열려 있으면 게임 시간이 멈춘다.
 * 창을 닫으면 공격·상호작용 입력을 비워, 닫기 클릭이 공격으로 이어지지 않는다.
 */
public class GameScreen extends ScreenAdapter implements GameActions {
    private final YongsaGame g;
    private final GameState state;
    private final GameWorld world;
    private final WorldRenderer renderer;
    private final Effects fx = new Effects();
    private final Hud hud;
    private final Stage stage;
    private final Windows windows;
    private final FixedStepper stepper = new FixedStepper(GameWorld.STEP, 0.25f);
    private final PlayerInput input = new PlayerInput();
    private final Vector2 mouse = new Vector2();
    private String startMessage;

    private boolean attackLatch = true;
    private boolean interactLatch = true;
    private boolean pendingInteract, pendingShock;
    private boolean lastSaveOk = true;
    private float endingTimer = -1;
    private boolean disposed;
    /** 개발용: 설정되면 키보드/마우스 대신 이 입력을 쓴다. */
    public PlayerInput scriptedInput;

    public GameScreen(YongsaGame g, GameState state, String startMessage) {
        this.g = g;
        this.state = state;
        this.startMessage = startMessage;
        DungeonMap map = DungeonGenerator.generate(state.dungeonSeed, g.balance);
        this.world = new GameWorld(g.balance, map, state);
        this.renderer = new WorldRenderer(g.batch, g.shapes, map, g.sprites, g.textures, g.fonts);
        this.hud = new Hud(g.fonts, g.textures, g.sprites);
        this.stage = new Stage(new FitViewport(960, 540), g.batch);
        this.windows = new Windows(stage, g.skin);
        windows.onAnyClosed = this::clearActionInput;
        windows.onClick = () -> g.audio.play("ui_click");
    }

    @Override
    public void show() {
        InputMultiplexer mux = new InputMultiplexer(stage, new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return onKey(keycode);
            }
        });
        Gdx.input.setInputProcessor(mux);
        renderer.snap();
        clearActionInput();
        g.audio.music("bgm_dungeon");
        if (startMessage != null) {
            windows.message("알림", startMessage);
            startMessage = null;
        }
        // 각성 안내를 확인하기 전에 종료했다면 캠프에서 다시 보여준다
        if (Progress.isAwakeningUnlocked(g.balance, state) && !state.awakenGuideSeen) openAwakeningGuide();
    }

    // =====================================================================
    // 입력
    // =====================================================================

    private boolean onKey(int key) {
        switch (key) {
            case Input.Keys.ESCAPE:
                if (!windows.escape()) openPause();
                return true;
            case Input.Keys.I:
                if (windows.anyOpen()) {
                    if ("bag".equals(windows.top().tag)) windows.close(windows.top());
                } else {
                    new BagWindow(windows, this).open();
                    g.audio.play("ui_open");
                }
                return true;
            case Input.Keys.E:
                if (!windows.anyOpen()) pendingInteract = true;
                return true;
            case Input.Keys.Q:
                if (!windows.anyOpen()) pendingShock = true;
                return true;
            case Input.Keys.F3:
                renderer.debug = !renderer.debug;
                return true;
            default:
                return false;
        }
    }

    /** 창을 닫을 때 공격·상호작용 입력을 비운다. 다시 누르기 전까지 공격을 시작하지 않는다. */
    private void clearActionInput() {
        attackLatch = true;
        interactLatch = true;
        pendingInteract = false;
        pendingShock = false;
    }

    private void fillInput() {
        if (scriptedInput != null) {
            input.moveX = scriptedInput.moveX;
            input.moveY = scriptedInput.moveY;
            input.aimX = scriptedInput.aimX;
            input.aimY = scriptedInput.aimY;
            input.attackHeld = scriptedInput.attackHeld;
            input.interactHeld = scriptedInput.interactHeld;
            return;
        }
        float mx = 0, my = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) mx -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) mx += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) my -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) my += 1;
        input.moveX = mx;
        input.moveY = my;
        renderer.unproject(Gdx.input.getX(), Gdx.input.getY(), mouse);
        input.aimX = mouse.x;
        input.aimY = mouse.y;
        boolean lmb = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (!lmb) attackLatch = false;
        input.attackHeld = lmb && !attackLatch;
        boolean e = Gdx.input.isKeyPressed(Input.Keys.E);
        if (!e) interactLatch = false;
        input.interactHeld = e && !interactLatch;
    }

    // =====================================================================
    // 갱신과 그리기
    // =====================================================================

    @Override
    public void render(float delta) {
        if (disposed) return;
        boolean paused = windows.anyOpen();
        float simDelta = 0;
        if (!paused) {
            fillInput();
            int steps = stepper.advance(delta);
            for (int i = 0; i < steps; i++) {
                if (pendingInteract) {
                    input.interactPressed = true;
                    pendingInteract = false;
                }
                if (pendingShock) {
                    input.shockwavePressed = true;
                    pendingShock = false;
                }
                world.step(GameWorld.STEP, input);
                input.clearEdges();
                simDelta += GameWorld.STEP;
                // 저장 요청은 상태 변경이 끝난 뒤 수행한다
                if (world.saveRequested) {
                    world.saveRequested = false;
                    save();
                }
                handleEvents();
                if (windows.anyOpen()) break; // 창이 열리면 즉시 정지
            }
        } else {
            stepper.reset();
        }

        fx.update(simDelta);
        fx.updateDust(renderer.cam.position.x, renderer.cam.position.y, paused ? 0 : delta);
        hud.update(paused ? 0 : delta);
        renderer.updateCamera(world, paused ? 0 : delta);
        g.audio.music(world.combat != null && world.combat.boss ? "bgm_boss" : "bgm_dungeon");

        if (endingTimer >= 0 && !paused) {
            endingTimer -= delta;
            if (endingTimer < 0) goToEnding();
        }

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float sx = 0, sy = 0;
        if (fx.shake > 0 && !paused) {
            sx = MathUtils.random(-2f, 2f) * fx.shake * 10;
            sy = MathUtils.random(-2f, 2f) * fx.shake * 10;
        }
        renderer.render(world, fx, sx, sy);

        stage.getViewport().apply();
        g.batch.setProjectionMatrix(stage.getCamera().combined);
        g.batch.begin();
        g.batch.setColor(Color.WHITE);
        g.batch.draw(g.textures.vignette, 0, 0, 960, 540);
        hud.draw(g.batch, world, g.balance);
        g.batch.end();

        stage.act(delta); // 창 애니메이션은 일시정지 중에도 동작
        stage.draw();
    }

    private void handleEvents() {
        Balance b = g.balance;
        for (WorldEvent e : world.events) {
            switch (e.type) {
                case TOAST:
                    hud.toast(e.text);
                    break;
                case SFX:
                    g.audio.play(e.text);
                    break;
                case DAMAGE_NUMBER:
                    fx.text(e.x, e.y, e.text, e.amount >= 30 ? new Color(1f, 0.8f, 0.3f, 1) : Color.WHITE, 1f);
                    fx.burst(e.x, e.y - 10, new Color(1f, 0.9f, 0.7f, 1), 5, 90);
                    break;
                case HERO_HURT:
                    fx.text(e.x, e.y, "-" + e.text, new Color(1f, 0.35f, 0.3f, 1), 1.1f);
                    fx.shake = 0.18f;
                    break;
                case ENEMY_DIED:
                    fx.burst(e.x, e.y + 12, enemyColor(e.text), 16, 160);
                    break;
                case ROOM_CLEARED:
                    hud.banner("전투방 돌파!  광물 +" + e.amount, 2.2f);
                    break;
                case BOSS_INTRO:
                    hud.banner(e.text, 1.6f);
                    break;
                case BOSS_DEFEATED: {
                    hud.banner(e.text + " 처치!  광물 +" + e.amount, 3f);
                    fx.shake = 0.4f;
                    int n = Progress.bossCount(state);
                    if (n == 1) {
                        hud.toast("망치 제작과 각성 충격파가 해금되었습니다");
                        hud.toast("오른쪽 지하 요새의 봉인이 풀렸습니다 · 강화 상한 +" + Progress.enhanceCap(b, state));
                    } else if (n == 2) {
                        hud.toast("위쪽 봉인의 신전이 열렸습니다 · 강화 상한 +" + Progress.enhanceCap(b, state));
                    }
                    break;
                }
                case AWAKEN_GUIDE:
                    openAwakeningGuide();
                    break;
                case ENDING:
                    // 보상과 완료 상태는 이미 저장 요청됨. 저장이 끝난 뒤 엔딩을 재생한다.
                    endingTimer = 3f;
                    break;
                case OPEN_FORGE:
                    g.audio.play("ui_open");
                    new ForgeWindow(windows, this).open();
                    break;
                case RESPAWNED:
                    hud.toast("중앙 캠프에서 다시 일어났습니다 · 장비와 진행은 그대로입니다");
                    break;
                case MINED:
                    fx.text(e.x, e.y, "광물 " + e.text, new Color(1f, 0.85f, 0.4f, 1), 1.1f);
                    fx.burst(e.x, e.y - 14, new Color(0.7f, 0.68f, 0.65f, 1), 12, 140);
                    break;
                case SHORTCUT_UNLOCKED:
                    hud.banner(e.text + " 지름길 개방", 2f);
                    break;
                case TELEPORT:
                    renderer.snap();
                    break;
                case COMBAT_STARTED:
                    hud.toast("문이 잠겼습니다 · 적을 모두 쓰러뜨리세요");
                    break;
                case DIED:
                    fx.shake = 0.3f;
                    break;
                default:
                    break;
            }
        }
        world.events.clear();
    }

    private static Color enemyColor(String id) {
        switch (id) {
            case "slime": return new Color(0.5f, 0.85f, 0.35f, 1);
            case "charger": return new Color(0.7f, 0.7f, 0.75f, 1);
            case "watcher": return new Color(0.7f, 0.45f, 0.95f, 1);
            default: return new Color(0.9f, 0.7f, 0.4f, 1);
        }
    }

    // =====================================================================
    // 창
    // =====================================================================

    /** 포커스를 잃으면 자동 일시정지. */
    public void autoPause() {
        if (!windows.anyOpen() && endingTimer < 0) openPause();
    }

    private void openPause() {
        Table p = windows.panel();
        p.add(windows.label("일시정지", "large")).left().padBottom(10).row();
        Windows.Modal[] holder = new Windows.Modal[1];
        p.add(windows.button("계속하기", () -> windows.close(holder[0]))).width(260).padBottom(6).row();
        p.add(windows.button("가방 (I)", () -> new BagWindow(windows, this).open())).width(260).padBottom(6).row();
        p.add(windows.button("설정", () -> SettingsWindow.open(windows, g.settings, s -> {
            g.applySettings(s);
            save(); // 설정 적용 직후 저장
        }))).width(260).padBottom(6).row();
        TextButton camp = windows.button("중앙 캠프로 귀환", () -> {
            if (world.returnToCamp()) {
                renderer.snap();
                windows.close(holder[0]);
            }
        });
        camp.setDisabled(!world.canReturnToCamp());
        p.add(camp).width(260).padBottom(2).row();
        p.add(windows.label(world.inCombat() ? "전투 중에는 귀환할 수 없습니다" : "위치만 옮깁니다 · 회복은 침대에서", "dim"))
                .padBottom(8).row();
        if (Progress.isComplete(g.balance, state)) {
            p.add(windows.button("엔딩 다시 보기", this::replayEnding)).width(260).padBottom(6).row();
        }
        if (!lastSaveOk) {
            p.add(windows.button("저장 다시 시도", () -> {
                if (save()) hud.toast("저장했습니다");
            })).width(260).padBottom(6).row();
        }
        p.add(windows.button("저장 후 타이틀로", () -> saveAndLeave(false))).width(260).padBottom(6).row();
        p.add(windows.button("저장 후 종료", () -> saveAndLeave(true))).width(260).row();
        if (world.inCombat()) {
            p.add(windows.label("진행 중인 전투는 초기화되며, 캠프에서 이어집니다", "warn")).padTop(8).row();
        }
        holder[0] = windows.push(p, "pause");
    }

    private void saveAndLeave(boolean exit) {
        if (save()) {
            leave(exit);
            return;
        }
        // 저장에 실패하면 자동으로 닫지 않고 사용자가 고른다
        Table p = windows.panel();
        p.add(windows.label("저장하지 못했습니다", "large")).left().row();
        p.add(windows.wrap(String.valueOf(g.saves.lastError()), "small", 380)).width(380).padTop(8).padBottom(12).row();
        Windows.Modal[] holder = new Windows.Modal[1];
        Table bt = new Table();
        bt.add(windows.button("다시 시도", () -> {
            windows.close(holder[0]);
            saveAndLeave(exit);
        })).padRight(8);
        bt.add(windows.button("저장하지 않고 " + (exit ? "종료" : "나가기"), () -> leave(exit))).padRight(8);
        bt.add(windows.button("취소", () -> windows.close(holder[0])));
        p.add(bt).right();
        holder[0] = windows.push(p, "save-error");
    }

    private void leave(boolean exit) {
        if (exit) {
            Gdx.app.exit();
        } else {
            g.switchTo(new TitleScreen(g), true);
        }
    }

    private void openAwakeningGuide() {
        if (windows.isOpen("awaken")) return;
        Balance b = g.balance;
        Table p = windows.panel();
        p.add(windows.label("각성 · 충격파", "large")).left().row();
        String text = "첫 수호자를 쓰러뜨려 각성했습니다.\n\n"
                + "Q 키: 마우스가 가리키는 방향(8방향)으로 직선 충격파를 날립니다.\n"
                + "범위 정면 " + (int) b.shockwave.length + "px · 폭 " + (int) b.shockwave.width + "px · 여러 적을 관통\n"
                + "피해 장착 무기 피해 × " + b.shockwave.damageMultiplier + " · 재사용 " + (int) b.shockwave.cooldown + "초\n"
                + "벽과 닫힌 문은 통과하지 못합니다. 보스방에 들어가면 즉시 다시 사용할 수 있습니다.\n\n"
                + "망치 제작이 해금되었습니다. 오른쪽 지하 요새의 봉인이 풀렸습니다.";
        p.add(windows.wrap(text, "default", 440)).width(440).padTop(10).padBottom(12).row();
        Windows.Modal[] holder = new Windows.Modal[1];
        p.add(windows.button("확인", () -> windows.close(holder[0]))).right();
        holder[0] = windows.push(p, "awaken");
        holder[0].onClose = () -> {
            if (!state.awakenGuideSeen) {
                state.awakenGuideSeen = true;
                save(); // 각성 안내 확인 직후 저장
            }
        };
    }

    private void goToEnding() {
        endingTimer = -1;
        if (!lastSaveOk && !save()) {
            // 완료된 메모리 상태는 유지하고 재시도 안내를 남긴다
            Table p = windows.panel();
            p.add(windows.label("저장하지 못했습니다", "large")).left().row();
            p.add(windows.wrap("완료 상태를 저장하지 못했습니다. 다시 시도하거나 저장 없이 엔딩을 볼 수 있습니다.", "default", 380))
                    .width(380).padTop(8).padBottom(12).row();
            Windows.Modal[] holder = new Windows.Modal[1];
            Table bt = new Table();
            bt.add(windows.button("다시 시도", () -> {
                windows.close(holder[0]);
                lastSaveOk = false;
                goToEnding();
            })).padRight(8);
            bt.add(windows.button("엔딩 보기", () -> {
                windows.close(holder[0]);
                showEnding(false);
            }));
            p.add(bt).right();
            holder[0] = windows.push(p, "save-error");
            holder[0].closable = false;
            return;
        }
        showEnding(false);
    }

    private void replayEnding() {
        windows.closeAll();
        showEnding(true);
    }

    private void showEnding(boolean replay) {
        if (replay) {
            // 엔딩 다시 보기 후에는 같은 진행으로 캠프에 돌아온다
            GameScreen self = this;
            g.switchTo(new StoryScreen(g, StoryScreen.Kind.ENDING, () -> {
                world.returnToCamp();
                g.switchTo(self, true);
            }), false);
        } else {
            // 엔딩 후 타이틀로 돌아간다. 이어하기는 캠프에서 시작한다.
            g.switchTo(new StoryScreen(g, StoryScreen.Kind.ENDING, () -> g.switchTo(new TitleScreen(g), true)), true);
        }
    }

    // ---- 개발용 접근 ----

    public GameWorld world() {
        return world;
    }

    public Windows windows() {
        return windows;
    }

    public void debugOpenForge() {
        new ForgeWindow(windows, this).open();
    }

    public void debugOpenPause() {
        openPause();
    }

    public void debugOpenBag() {
        new BagWindow(windows, this).open();
    }

    public void debugShock() {
        pendingShock = true;
    }

    // =====================================================================
    // GameActions
    // =====================================================================

    @Override
    public Balance balance() {
        return g.balance;
    }

    @Override
    public GameState state() {
        return state;
    }

    @Override
    public boolean inCombat() {
        return world.inCombat();
    }

    @Override
    public boolean save() {
        state.settings = g.settings.copy();
        boolean ok = g.saves.save(state);
        lastSaveOk = ok;
        hud.setSaveError(ok ? null : g.saves.lastError());
        if (!ok) Gdx.app.error("Save", String.valueOf(g.saves.lastError()));
        return ok;
    }

    @Override
    public void sfx(String id) {
        g.audio.play(id);
    }

    @Override
    public void resize(int width, int height) {
        renderer.resize(width, height);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
        autoPause();
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        renderer.dispose();
        stage.dispose();
    }
}

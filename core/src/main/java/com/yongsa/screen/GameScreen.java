package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.MathUtils;
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
import com.yongsa.ui.ForgeWindow;
import com.yongsa.ui.GameActions;
import com.yongsa.ui.Hud;
import com.yongsa.ui.SettingsWindow;
import com.yongsa.ui.Windows;
import com.yongsa.ui.KeyboardInput;
import com.yongsa.model.CraftService;
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
    private String startMessage;

    private boolean attackLatch = true;
    private boolean interactLatch = true;
    private boolean pendingInteract, pendingShock, pendingAttack, pendingDodge;
    private final KeyboardInput keyboard = new KeyboardInput(this::onKey);
    private boolean lastSaveOk = true;
    private float endingTimer = -1;
    private boolean disposed;
    /** 개발용: 타이틀 배경 등을 찍을 때 화면 정보를 숨긴다. */
    public boolean hideHud;
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
        this.hud.noticeTime = g.balance.feel.noticeTime;
        this.stage = new Stage(new FitViewport(960, 540), g.batch);
        this.windows = new Windows(stage, g.skin);
        windows.onAnyClosed = this::clearActionInput;
        windows.onClick = () -> g.audio.play("ui_click");
        this.dev = new DevMenu(this, windows);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(keyboard);
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
        if (windows.anyOpen()) {
            if (key == Input.Keys.I && "forge".equals(windows.top().tag)) windows.close(windows.top());
            else if (key == Input.Keys.F1 && "dev".equals(windows.top().tag)) windows.close(windows.top());
            else windows.keyDown(key);
            return true;
        }
        switch (key) {
            case Input.Keys.ESCAPE:
                if (!windows.escape()) openPause();
                return true;
            case Input.Keys.I:
                takeOutForge();
                return true;
            case Input.Keys.F1:
                dev.open();
                return true;
            case Input.Keys.J:
                pendingAttack = true;
                return true;
            case Input.Keys.K:
                pendingDodge = true;
                return true;
            case Input.Keys.L:
                if (!windows.anyOpen()) pendingInteract = true;
                return true;
            case Input.Keys.U:
                if (!windows.anyOpen()) pendingShock = true;
                return true;
            case Input.Keys.O:
                if (windows.anyOpen() || world.hero.dead) return true;
                if (state.weapons.size() > 1) {
                    // 가진 무기를 차례로 바꿔 든다
                    int at = 0;
                    for (int i = 0; i < state.weapons.size(); i++) if (state.weapons.get(i).type.equals(state.equipped)) at = i;
                    String next = state.weapons.get((at + 1) % state.weapons.size()).type;
                    CraftService.equip(state, next, false);
                    world.hero.attack = null;
                    world.hero.combo = 0;
                    if (!world.inCombat()) save(); // 전투 중에는 저장하지 않는다 (방을 돌파하면 저장된다)
                    hud.toast(g.balance.weapon(next).name + " 장착");
                } else hud.toast("바꿔 들 무기가 없습니다 · I 로 대장간을 꺼내 제작");
                return true;
            case Input.Keys.F3:
                renderer.debug = !renderer.debug;
                return true;
            default:
                return false;
        }
    }

    /** 주머니에서 대장간을 꺼내는 중이면 0 이상 (다 꺼내면 창이 열린다). */
    private float forgeTimer = -1;
    private final DevMenu dev;

    /** I: 주머니에서 휴대용 대장간을 꺼낸다. 전투 중에는 꺼낼 수 없다. */
    private void takeOutForge() {
        if (world.hero.dead || forgeTimer >= 0) return;
        if (world.inCombat()) {
            hud.toast("전투 중에는 대장간을 꺼낼 수 없습니다");
            return;
        }
        forgeTimer = 0.36f;
        world.hero.attack = null;
        clearActionInput();
        g.audio.play("ui_open");
        fx.burst(world.hero.x, world.hero.y + 14, new Color(0.85f, 0.8f, 0.7f, 1), 8, 70);
    }

    @Override
    public com.badlogic.gdx.graphics.g2d.TextureRegion weaponIcon(String type) {
        return Hud.icon(g.sprites, type);
    }

    /** 창을 닫을 때 공격·상호작용 입력을 비운다. 다시 누르기 전까지 공격을 시작하지 않는다. */
    private void clearActionInput() {
        attackLatch = true;
        interactLatch = true;
        pendingInteract = false;
        pendingShock = false;
        pendingAttack = pendingDodge = false;
        input.clearEdges();
        input.attackHeld = input.interactHeld = false;
        world.clearBufferedActions();
    }

    private void fillInput() {
        if (forgeTimer >= 0) {
            // 대장간을 꺼내는 짧은 동안은 가만히 선다
            input.moveX = input.moveY = 0;
            input.attackHeld = input.interactHeld = false;
            return;
        }
        if (scriptedInput != null) {
            input.moveX = scriptedInput.moveX;
            input.moveY = scriptedInput.moveY;
            input.aimX = scriptedInput.aimX;
            input.aimY = scriptedInput.aimY;
            input.attackHeld = scriptedInput.attackHeld;
            input.interactHeld = scriptedInput.interactHeld;
            input.keyboardAim = scriptedInput.keyboardAim;
            if (scriptedInput.attackPressed) pendingAttack = true;
            if (scriptedInput.dodgePressed) pendingDodge = true;
            if (scriptedInput.interactPressed) pendingInteract = true;
            if (scriptedInput.shockwavePressed) pendingShock = true;
            scriptedInput.clearEdges();
            return;
        }
        float mx = 0, my = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) mx -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) mx += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) my -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) my += 1;
        input.moveX = mx;
        input.moveY = my;
        input.keyboardAim = true;
        boolean lmb = Gdx.input.isKeyPressed(Input.Keys.J);
        if (!lmb) attackLatch = false;
        input.attackHeld = lmb && !attackLatch;
        boolean e = Gdx.input.isKeyPressed(Input.Keys.L);
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
        dev.apply();
        hud.devOn = dev.anyOn();
        if (forgeTimer >= 0 && !paused) {
            forgeTimer -= delta;
            if (forgeTimer < 0) {
                forgeTimer = -1;
                if (!world.inCombat() && !world.hero.dead) new ForgeWindow(windows, this).open();
            }
        }
        renderer.forgeShown = forgeTimer >= 0 || windows.isOpen("forge");
        if (!paused) {
            fillInput();
            int steps = stepper.advance(delta);
            for (int i = 0; i < steps; i++) {
                input.attackPressed = pendingAttack;
                input.dodgePressed = pendingDodge;
                pendingAttack = pendingDodge = false;
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

        // 보스 사망 연출: 쓰러지는 동안 몸에서 파편이 계속 튄다
        if (!paused && world.combat != null && world.combat.dying && world.combat.outro > 0) {
            com.yongsa.combat.Enemy boss = world.combat.bossEnemy();
            if (boss != null && MathUtils.random() < delta * 22f) {
                fx.burst(boss.x + MathUtils.random(-26f, 26f), boss.y + MathUtils.random(4f, 60f), new Color(1f, 0.95f, 0.8f, 1), 6, 170);
            }
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
        if (fx.flash > 0) {
            g.batch.setColor(1, 1, 1, Math.min(1f, fx.flash / Math.max(0.001f, g.balance.feel.bossDeathFlash)));
            g.batch.draw(g.textures.white, 0, 0, 960, 540);
        }
        g.batch.setColor(Color.WHITE);
        g.batch.draw(g.textures.vignette, 0, 0, 960, 540);
        if (!hideHud) hud.draw(g.batch, world, g.balance);
        g.batch.end();

        stage.act(delta); // 창 애니메이션은 일시정지 중에도 동작
        stage.draw();
        windows.drawKeyboardFocus(g.batch, g.textures, g.fonts);
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
                    shake(e.amount >= b.feel.heavyHitDamage ? b.feel.shakeMedium : b.feel.shakeLight);
                    break;
                case HERO_HURT:
                    fx.text(e.x, e.y, "-" + e.text, new Color(1f, 0.35f, 0.3f, 1), 1.1f);
                    shake(b.feel.shakeMedium);
                    break;
                case ENEMY_DIED: {
                    boolean elite = e.amount > 0;
                    fx.burst(e.x, e.y + 12, enemyColor(b, e.text), elite ? 30 : 16, elite ? 210 : 160);
                    if (elite) shake(b.feel.shakeMedium);
                    break;
                }
                case ENEMY_SPAWNED:
                    fx.burst(e.x, e.y + 10, new Color(1f, 0.6f, 0.55f, 1), e.amount > 0 ? 14 : 8, 110);
                    break;
                case WAVE_STARTED:
                    hud.toast(e.amount > 0 ? "적이 더 몰려옵니다 · 남은 무리 " + e.amount : "마지막 무리가 몰려옵니다");
                    break;
                case STATUS_APPLIED: {
                    Balance.StatusDef sd = b.statusOrNull(e.text);
                    if (sd == null) break;
                    Color c = new Color(sd.color[0], sd.color[1], sd.color[2], 1);
                    fx.text(e.x, e.y, sd.name, c, 1f);
                    fx.burst(e.x, e.y - 14, c, 10, 120);
                    break;
                }
                case PROJECTILE_BROKEN:
                    fx.burst(e.x, e.y, new Color(1f, 0.5f, 0.65f, 1), 6, 90);
                    break;
                case DODGE:
                    fx.burst(e.x, e.y + 3, new Color(0.78f, 0.74f, 0.68f, 1), 5, 55);
                    break;
                case BOSS_DYING:
                    shake(b.feel.shakeHeavy);
                    fx.burst(e.x, e.y + 30, new Color(1f, 0.95f, 0.8f, 1), 30, 240);
                    g.audio.play("enemy_die");
                    break;
                case CHEST_OPENED:
                    fx.text(e.x, e.y, "골드 " + e.text, new Color(1f, 0.85f, 0.4f, 1), 1.2f);
                    fx.burst(e.x, e.y - 10, new Color(1f, 0.8f, 0.3f, 1), 22, 190);
                    break;
                case BLESSING: {
                    Balance.BlessingDef bd = b.blessingOrNull(e.text);
                    if (bd != null) hud.notice("icon_heart", "축복 · " + bd.name, bd.desc + " (쓰러지면 사라집니다)");
                    break;
                }
                case ROOM_CLEARED:
                    hud.banner("전투방 돌파!  " + b.zone(e.text).oreName + " +" + e.amount, 2.2f);
                    break;
                case CHEST_APPEARED:
                    fx.burst(e.x, e.y + 8, new Color(1f, 0.85f, 0.4f, 1), 14, 150);
                    break;
                case BOSS_INTRO:
                    hud.banner(e.text, 1.6f);
                    break;
                case BOSS_DEFEATED: {
                    hud.banner(e.text + " 처치!  " + b.zone(world.lastZone).oreName + " +" + e.amount, 3f);
                    shake(b.feel.shakeHeavy);
                    fx.flash = b.feel.bossDeathFlash;
                    int n = Progress.bossCount(state);
                    if (n == 1) {
                        hud.notice("icon_shock", "각성 충격파 해금", "U 로 정면에 충격파를 쏜다");
                        hud.notice("hammer", "지하 요새의 봉인이 풀렸습니다", "청광석으로 망치 제작 · 강화 상한 +" + Progress.enhanceCap(b, state));
                    } else if (n == 2) {
                        hud.notice("daggers", "봉인의 신전이 열렸습니다", "자광석으로 쌍단검 제작 · 강화 상한 +" + Progress.enhanceCap(b, state));
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
                case CHEST_HEAL:
                    for (Balance.ChestHealDef heal : b.chestHeals) {
                        if (!heal.id.equals(e.text) || e.amount <= 0) continue;
                        fx.icon(Hud.icon(g.sprites, heal.sprite), e.x, e.y + 6, 30);
                        fx.text(e.x, e.y + 40, "체력 +" + e.amount, new Color(0.5f, 1f, 0.6f, 1), 1.1f);
                        hud.notice(heal.sprite, heal.name, "체력 +" + e.amount);
                    }
                    break;
                case RESPAWNED:
                    hud.toast("중앙 캠프에서 다시 일어났습니다 · 장비와 진행은 그대로, 광맥은 새로 자랐습니다");
                    break;
                case MINED:
                    {
                        Balance.ZoneDef mz = b.zone(e.text);
                        Color oc = new Color(mz.oreColor[0], mz.oreColor[1], mz.oreColor[2], 1);
                        fx.text(e.x, e.y, mz.oreName + " +" + e.amount, oc, 1.1f);
                        fx.burst(e.x, e.y - 14, oc, 12, 140);
                    }
                    break;
                case SHORTCUT_UNLOCKED:
                    hud.banner(e.text + " 지름길 개방", 2f);
                    hud.notice("icon_shock", e.text + " 지름길 개방", "중앙 캠프에서 보스 앞으로 바로 이동");
                    break;
                case TELEPORT:
                    renderer.snap();
                    break;
                case COMBAT_STARTED:
                    hud.toast("문이 잠겼습니다 · 적을 모두 쓰러뜨리세요");
                    break;
                case DIED:
                    shake(b.feel.shakeHeavy);
                    break;
                default:
                    break;
            }
        }
        world.events.clear();
    }

    /** 화면 흔들림은 balance.json 의 약·중·강 세 값만 쓴다. 더 센 흔들림이 진행 중이면 덮어쓰지 않는다. */
    private void shake(float amount) {
        fx.shake = Math.max(fx.shake, amount);
    }

    /** 처치 파편 색은 적 정의에 적혀 있다. */
    private static Color enemyColor(Balance b, String id) {
        Balance.EnemyDef d = b.enemyOrNull(id);
        float[] c = d != null ? d.color : new float[]{0.9f, 0.7f, 0.4f};
        return new Color(c[0], c[1], c[2], 1);
    }

    // =====================================================================
    // 창
    // =====================================================================

    /** 포커스를 잃으면 자동 일시정지. */
    public void autoPause() {
        keyboard.clear();
        clearActionInput();
        stepper.reset();
        if (!windows.anyOpen() && endingTimer < 0) openPause();
    }

    private void openPause() {
        Table p = windows.panel();
        p.add(windows.label("일시정지", "large")).left().padBottom(10).row();
        Windows.Modal[] holder = new Windows.Modal[1];
        p.add(windows.button("계속하기", () -> windows.close(holder[0]))).width(260).padBottom(6).row();
        p.add(windows.button("대장간 꺼내기 (I)", () -> {
            windows.close(holder[0]);
            takeOutForge();
        })).width(260).padBottom(6).row();
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
        p.add(windows.label(world.inCombat() ? "전투 중에는 귀환할 수 없습니다" : "위치만 옮깁니다 · 캠프에서는 회복되지 않습니다", "dim"))
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
                + "U 키로 몸에서 충격파를 터뜨립니다.\n"
                + "방 안의 모든 적이 무기 피해의 " + b.shockwave.damageMultiplier + "배를 한 번씩 맞고 밀려나며, 날아오던 탄이 사라집니다. (재사용 " + (int) b.shockwave.cooldown + "초)\n\n"
                + "오른쪽 지하 요새로 가는 길이 열렸습니다.";
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

    public WorldRenderer view() {
        return renderer;
    }

    public GameWorld world() {
        return world;
    }

    public Windows windows() {
        return windows;
    }

    /** 개발용: 키 입력을 흉내 낸다. */
    public void keyForTest(int key) {
        onKey(key);
    }

    public void debugOpenForge() {
        new ForgeWindow(windows, this).open();
    }

    public void debugOpenPause() {
        openPause();
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

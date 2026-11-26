package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.ui.ForgeMotion;
import com.yongsa.ui.ForgeScene;

/** Actual skill input/update/render and all forge poses, with isolated QA saves. */
public final class SkillDesignLauncher {
    public static void main(String[] args) {
        if (args.length != 1 && args.length != 3)
            throw new IllegalArgumentException("Output directory required; optional first/end-exclusive scenarios (0..70)");
        int first = args.length == 3 ? Integer.parseInt(args[1]) : 0;
        int end = args.length == 3 ? Integer.parseInt(args[2]) : 70;
        if (first < 0 || first >= end || end > 70) throw new IllegalArgumentException("Invalid scenario range");
        System.setProperty("yongsa.saveDir", new java.io.File(args[0], "qa-save").getAbsolutePath());
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(960, 640);
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.useVsync(false);
        new Lwjgl3Application(new Capture(args[0], first, end), config);
    }

    private static final class Capture extends YongsaGame {
        private final String[] weapons = {"blackiron", "bloodmoon", "skyfall", "holy_awakened"};
        private final FileHandle output;
        private final int endScenario;
        private GameSession session;
        private WorldRenderer renderer;
        private OrthographicCamera camera;
        private int scenario, frame, captures;
        private float centerX, centerY;
        Capture(String path, int first, int end) {
            output = new FileHandle(path); output.mkdirs(); scenario = first; endScenario = end;
        }
        @Override public void create() {
            super.create();
            camera = new OrthographicCamera(640, 426.6667f);
            if (scenario < 64) setup();
        }
        private void setup() {
            if (renderer != null) renderer.dispose();
            var state = NewGame.create(balance);
            var weapon = state.newWeapon(weapons[scenario / 16]);
            weapon.level = 10;
            weapon.awakened = scenario % 16 >= 8;
            state.unsealed = true;
            state.equippedUid = weapon.uid;
            var map = newMap();
            for (var room : map.rooms) state.room(room.id).cleared = true;
            session = new GameSession(balance, map, state, 42);
            session.monsters.clear();
            session.devGodMode = true;
            var room = map.room("L6");
            centerX = (room.centerTileX() + .5f) * balance.tileSize;
            centerY = (room.centerTileY() + .5f) * balance.tileSize;
            session.hero.x = centerX;
            session.hero.y = centerY;
            float angle = (float) Math.toRadians(scenario % 8 * 45);
            session.hero.aimX = (float) Math.cos(angle);
            session.hero.aimY = (float) Math.sin(angle);
            session.hero.facing = com.yongsa.combat.Facing.of(session.hero.aimX, session.hero.aimY);
            renderer = new WorldRenderer(session, art, fonts, batch, shapes);
            session.pollEvents();
            frame = 0;
        }
        @Override public void render() {
            if (scenario >= 64) { captureForge(); return; }
            Controls input = new Controls();
            if (frame == 0) input.skill = scenario / 16 == 3 ? Rules.HOLY_SLOT : 0;
            session.update(1 / 60f, input);
            boolean cast = false;
            for (var event : session.pollEvents()) {
                cast |= event.type == GameEvent.Type.SKILL_CAST;
                renderer.onEvent(event);
            }
            if (frame == 0 && !cast) throw new AssertionError("Skill did not cast: " + scenario);
            renderer.update(1 / 60f);
            camera.position.set(centerX, centerY + 45, 0);
            camera.update();
            renderer.renderLights(camera);
            ScreenUtils.clear(.02f, .018f, .03f, 1);
            renderer.render(camera);
            frame++;
            if (frame == 6 || frame == 12 || frame == 21 || frame == 32)
                save(weapons[scenario / 16] + "-" + (scenario % 16 >= 8 ? "aw" : "base")
                        + "-" + scenario % 8 * 45 + "-f" + frame);
            if (frame == 40) { if (advanceScenario()) return; if (scenario < 64) setup(); }
        }
        private void captureForge() {
            int pose = scenario - 64;
            ForgeMotion motion = new ForgeMotion();
            if (pose > 0 && pose < 4) {
                motion.start(3);
                motion.advance(pose == 1 ? .4f : pose == 2 ? .78f : .93f);
            } else if (pose >= 4) {
                motion.start(1);
                motion.advance(1);
                motion.result(pose == 4 ? EnhanceService.Outcome.SUCCESS : EnhanceService.Outcome.DOWN, false, 1);
            }
            if (motion.pose() != pose) throw new AssertionError("Forge pose " + pose);
            camera.setToOrtho(false, 960, 640);
            batch.setProjectionMatrix(camera.combined);
            ScreenUtils.clear(.04f, .03f, .025f, 1);
            var scene = new ForgeScene(art, motion, "blackiron", false);
            scene.setBounds(48, 32, 864, 576);
            batch.begin(); scene.draw(batch, 1); batch.end();
            save("forge-" + pose);
            advanceScenario();
        }
        private boolean advanceScenario() {
            if (++scenario == endScenario) {
                System.out.println("SKILL_CAPTURE_COMPLETE " + captures);
                Gdx.app.exit();
                return true;
            }
            return false;
        }
        private void save(String name) {
            int error = Gdx.gl.glGetError();
            if (error != GL20.GL_NO_ERROR) throw new IllegalStateException("GL error " + error);
            var pixels = ScreenUtils.getFrameBufferPixmap(0, 0, 960, 640);
            var png = new PixmapIO.PNG(); png.setFlipY(true);
            try { png.write(output.child(name + ".png"), pixels); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            finally { png.dispose(); pixels.dispose(); }
            captures++;
        }
        @Override public void dispose() { if (renderer != null) renderer.dispose(); super.dispose(); }
    }
}

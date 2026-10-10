package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.model.Weapon;
import com.yongsa.screen.GameScreen;
import com.yongsa.ui.BagWindow;
import com.yongsa.ui.ForgeWindow;
import com.yongsa.world.Room;

/** Explicit developer-only entry point. Saves real GL frames using an isolated save directory. */
public final class VisualSmokeLauncher {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Screenshot output directory required");
        System.setProperty("yongsa.saveDir", new java.io.File(args[0], "qa-save").getAbsolutePath());
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Yongsa visual verification");
        config.setWindowedMode(1280, 720);
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.setForegroundFPS(60);
        config.setIdleFPS(60);
        config.useVsync(false);
        new Lwjgl3Application(new Capture(args[0]), config);
    }

    private static final class Capture extends YongsaGame {
        private final FileHandle output;
        private final String[] names = {"01-title", "02-camp", "03-forge-craft", "04-forge-enhance",
                "05-bag", "06-map", "07-mine", "08-castle", "09-tower-boss", "10-final-boss", "11-small-window", "12-weapon-gallery", "13-awakened-forge", "14-full-inventory"};
        private int scenario, frame;
        Capture(String directory) { output = new FileHandle(directory); output.mkdirs(); }

        @Override public void render() {
            super.render();
            exerciseInput();
            if (++frame < 100) return;
            Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            PixmapIO.PNG writer = new PixmapIO.PNG();
            writer.setFlipY(true);
            try { writer.write(output.child(names[scenario] + ".png"), pixels); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            finally { writer.dispose(); pixels.dispose(); }
            System.out.println("CAPTURE_OK " + names[scenario]);
            frame = 0;
            if (++scenario == names.length) { Gdx.app.exit(); return; }
            setup();
        }

        private void exerciseInput() {
            if (!(getScreen() instanceof GameScreen gameScreen)) return;
            if (scenario == 2 && frame == 10) Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
            if (scenario == 2 && frame == 95) {
                if (gameScreen.session.state.weaponByDef("blackiron") == null || gameScreen.session.state.ore(OreType.BLACKIRON) != 172)
                    throw new AssertionError("Craft input did not update inventory and ore cost");
                System.out.println("UI_ACTION_OK craft");
            }
            if (scenario == 3 && frame == 10) {
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                if (!(gameScreen.modals.top() instanceof com.yongsa.ui.ConfirmDialog)) throw new AssertionError("Missing destruction confirmation");
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                if (gameScreen.session.state.equipped().level != 6 || gameScreen.session.state.ore(OreType.BLACKIRON) != 180)
                    throw new AssertionError("Cancel changed weapon or ore");
                System.out.println("UI_ACTION_OK enhance-risk-cancel");
            }
            if (scenario == 4 && frame == 10) {
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.DOWN);
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                if (!"bloodmoon".equals(gameScreen.session.state.equipped().defId)) throw new AssertionError("Equip did not select bloodmoon");
                System.out.println("UI_ACTION_OK equip");
            }
            if (scenario == 13 && frame == 10) {
                int expectedUid = gameScreen.session.state.weapons.get(14).uid;
                for (int i = 0; i < 13; i++) Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.DOWN);
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                if (gameScreen.session.state.equippedUid != expectedUid) throw new AssertionError("Inventory scrolling lost keyboard selection");
                System.out.println("UI_ACTION_OK scroll-inventory");
            }
            if (scenario == 13 && frame == 20) Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.PAGE_DOWN);
        }

        private void setup() {
            if (scenario == 11) {
                Gdx.graphics.setWindowedMode(1280, 720);
                Screen previous = getScreen();
                setScreen(new com.badlogic.gdx.ScreenAdapter() {
                    @Override public void render(float delta) {
                        Gdx.gl.glClearColor(0.04f, 0.06f, 0.08f, 1);
                        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
                        batch.setProjectionMatrix(new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0, 0, 1280, 720));
                        batch.begin();
                        fonts.large.setColor(com.yongsa.ui.Ui.GOLD);
                        fonts.large.draw(batch, "무기 · 기본형과 각성형", 40, 684);
                        String[] ids = {"holy_sealed", "blackiron", "bloodmoon", "skyfall", "holy_awakened", "blackiron", "bloodmoon", "skyfall"};
                        for (int i = 0; i < 8; i++) {
                            float x = 48 + i % 4 * 310, y = i < 4 ? 354 : 42;
                            batch.setColor(0.08f, 0.11f, 0.14f, 1);
                            batch.draw(art.white(), x - 8, y - 10, 284, 290);
                            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
                            batch.draw(art.sword(ids[i], i >= 4), x + 15, y + 24, 236, 236);
                            fonts.normal.setColor(i < 4 ? com.yongsa.ui.Ui.TEXT : com.yongsa.ui.Ui.GOLD);
                            fonts.normal.draw(batch, i < 4 || i == 4 ? balance.weapon(ids[i]).name : balance.weapon(ids[i]).awakenedName, x + 12, y + 14);
                        }
                        batch.end();
                    }
                });
                previous.dispose();
                return;
            }
            GameState state = NewGame.create(balance);
            for (OreType ore : OreType.values()) state.addOre(ore, 180);
            if (scenario >= 3) {
                Weapon iron = state.newWeapon("blackiron"); iron.level = 6; state.equippedUid = iron.uid;
                Weapon blood = state.newWeapon("bloodmoon"); blood.level = 10; blood.awakened = true;
                Weapon sky = state.newWeapon("skyfall"); sky.level = 3;
                if (scenario >= 12) { iron.level = 10; iron.awakened = true; }
                if (scenario == 13) {
                    state.bossesDefeated.addAll(java.util.List.of("LEFT", "RIGHT", "TOP"));
                    state.imprints.addAll(java.util.List.of("blackiron", "bloodmoon", "skyfall"));
                    for (int i = 0; i < 12; i++) state.newWeapon("blackiron");
                }
            }
            String roomId = scenario == 6 ? "L2" : scenario == 7 ? "R1" : scenario == 8 ? "T7" : scenario == 9 ? "F" : "CAMP";
            Room target = newMap().rooms.stream().filter(r -> r.id.equals(roomId)).findFirst().orElseThrow();
            if (scenario == 9) { state.unsealed = true; state.bossesDefeated.addAll(java.util.List.of("LEFT", "RIGHT", "TOP")); }
            GameScreen next = new GameScreen(this, state);
            if (!"CAMP".equals(roomId)) {
                next.session.hero.x = (target.centerTileX() + 0.5f) * balance.tileSize;
                next.session.hero.y = (target.centerTileY() - 2) * balance.tileSize;
            }
            Screen previous = getScreen();
            setScreen(next);
            previous.dispose();
            if (scenario == 2 || scenario == 3 || scenario == 12) next.modals.push(new ForgeWindow(next));
            if (scenario == 4 || scenario == 13) next.modals.push(new BagWindow(next));
            if (scenario == 5) next.hud.toggleMap();
            if (scenario == 10) { Gdx.graphics.setWindowedMode(960, 540); next.modals.push(new BagWindow(next)); }
        }
    }
}

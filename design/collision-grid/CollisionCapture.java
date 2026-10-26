package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import com.yongsa.screen.GameScreen;

/** Developer-only: saves frames with the F2 collision overlay at given world points. */
public final class CollisionCapture {
    public static void main(String[] args) {
        System.setProperty("yongsa.saveDir", new java.io.File(args[0], "qa-save").getAbsolutePath());
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280, 720);
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.useVsync(false);
        new Lwjgl3Application(new Capture(args), config);
    }

    private static final class Capture extends YongsaGame {
        private final FileHandle output;
        private final String[] spots;
        private int index = -1, frame;

        Capture(String[] args) {
            output = new FileHandle(args[0]);
            output.mkdirs();
            spots = java.util.Arrays.copyOfRange(args, 1, args.length);
        }

        @Override public void render() {
            super.render();
            if (index >= 0 && frame == 5) Gdx.input.getInputProcessor().keyDown(Input.Keys.F2);
            if (index >= 0 && ++frame < 60) return;
            if (index >= 0) capture(spots[index].split(":")[0]);
            if (++index == spots.length) { Gdx.app.exit(); return; }
            frame = 0;
            GameState state = NewGame.create(balance);
            state.bossesDefeated.addAll(java.util.List.of("LEFT", "RIGHT", "TOP"));
            state.unsealed = true;
            String[] p = spots[index].split(":")[1].split(",");
            state.resumeX = Float.parseFloat(p[0]);
            state.resumeY = Float.parseFloat(p[1]);
            state.mapVersion = com.yongsa.world.WorldMap.VERSION;
            GameScreen next = new GameScreen(this, state);
            next.session.hero.x = state.resumeX;
            next.session.hero.y = state.resumeY;
            Screen previous = getScreen();
            setScreen(next);
            if (previous != null) previous.dispose();
        }

        private void capture(String name) {
            Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            PixmapIO.PNG writer = new PixmapIO.PNG();
            writer.setFlipY(true);
            try { writer.write(output.child(name + ".png"), pixels); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            finally { writer.dispose(); pixels.dispose(); }
            System.out.println("CAPTURE_OK " + name);
        }
    }
}

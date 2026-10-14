package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Enemy;
import com.yongsa.model.GameState;
import com.yongsa.screen.GameScreen;
import com.yongsa.screen.StoryScreen;
import com.yongsa.world.PlayerInput;
import com.yongsa.world.RoomDef;

import java.nio.file.*;
import java.util.*;

/** Opt-in real-OpenGL smoke test. Uses isolated saves; never enabled in a normal launch. */
final class KeyboardPlaytest implements Runnable {
    private final YongsaGame game;
    private final Path output;
    private final PlayerInput input = new PlayerInput();
    private final List<Float> frameTimes = new ArrayList<>();
    private GameScreen screen;
    private int step, frame, shot;
    private float elapsed, startX, startTime;
    private boolean measured, bagFrozen;
    private float bagTime, bagX;

    KeyboardPlaytest(YongsaGame game, String output) {
        this.game = game; this.output = Paths.get(output);
        try { Files.createDirectories(this.output); } catch(Exception e) { throw new RuntimeException(e); }
        input.keyboardAim = true;
    }

    private void press(int key) {
        Gdx.input.getInputProcessor().keyDown(key);
        Gdx.input.getInputProcessor().keyUp(key);
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void next() { step++; elapsed=0; }
    @Override public void run() {
        try { tick(); } catch(Throwable e) {
            try { Files.writeString(output.resolve("FAILED.txt"), e.toString()); } catch(Exception ignored) {}
            e.printStackTrace(); Gdx.app.exit();
        }
    }
    private void tick() throws Exception {
        elapsed += Gdx.graphics.getDeltaTime(); frame++;
        if(screen!=null&&step>=8&&frame%6!=1)frameTimes.add(Gdx.graphics.getDeltaTime());
        switch(step) {
            case 0:
                if(elapsed<0.4f) return;
                // New game is the default keyboard selection; no pointer events.
                press(Input.Keys.J); next(); break;
            case 1:
                if(!(game.getScreen() instanceof StoryScreen)) return;
                press(Input.Keys.K); next(); break;
            case 2:
                if(!(game.getScreen() instanceof GameScreen)) return;
                screen=(GameScreen)game.getScreen();screen.scriptedInput=input;
                screen.windows().closeAll();
                startX=screen.world().hero.x;startTime=screen.world().time;
                input.moveX=1;next();break;
            case 3:
                if(elapsed<0.45f) return;
                float moved=screen.world().hero.x-startX;
                float expected=(screen.world().time-startTime)*game.balance.hero.moveSpeed;
                check(Math.abs(moved-expected)<1f,"held movement distance: "+moved+" / "+expected);
                measured=true;input.moveX=0;
                screen.state().ore[0]=20;screen.debugOpenForge();next();break;
            case 4:
                if(elapsed<0.2f) return;
                // The forge opens with the first craftable weapon selected.
                press(Input.Keys.J);
                check(screen.state().hasAnyWeapon(),"keyboard craft failed");
                check(screen.state().ore[0]==14,"craft transaction duplicated");
                shot("01_keyboard_craft");next();break;
            case 5:
                if(elapsed<0.2f) return;
                press(Input.Keys.J);press(Input.Keys.K);screen.debugOpenForge();
                // Tab row: right to the enhance tab, confirm, then DOWN goes into the tab's content (not sideways).
                press(Input.Keys.RIGHT);press(Input.Keys.J);
                press(Input.Keys.DOWN);press(Input.Keys.DOWN);press(Input.Keys.J);
                check(screen.state().weapon("sword").level==1,"keyboard enhance failed");
                check(screen.state().ore[0]==12,"enhance transaction duplicated");
                next();break;
            case 6:
                if(elapsed<0.3f) return;
                shot("02_keyboard_enhance");press(Input.Keys.J);press(Input.Keys.K);
                screen.debugOpenForge();bagTime=screen.world().time;bagX=screen.world().hero.x;
                input.moveX=-1;next();break;
            case 7:
                if(elapsed<0.35f) return;
                check(screen.world().time==bagTime&&screen.world().hero.x==bagX,"bag did not pause");
                bagFrozen=true;press(Input.Keys.K);input.moveX=0;
                screen.world().returnToCamp();next();break;
            case 8:
                // Record walk, alternating cuts and a roll in a safe lane below the fire.
                input.moveX=elapsed<0.6f?1:elapsed<1.8f?-1:elapsed<2.4f?1:0;
                input.attackHeld=elapsed>2.6f&&elapsed<4.2f;
                if(elapsed>4.3f&&elapsed<4.4f&&screen.world().hero.dodgeCooldown==0){input.moveX=1;input.dodgePressed=true;}
                if(elapsed>=4.4f)input.moveX=0;
                if(frame%6==0) shot(String.format("motion_%03d",shot++));
                if(elapsed<5.1f)return;
                input.attackHeld=false;
                RoomDef r=screen.world().map.firstCombatRoom("mine");
                screen.world().hero.x=r.centerX();screen.world().hero.y=r.centerY();next();break;
            case 9:
                // Fight using the same keyboard aim, attack and movement inputs as gameplay.
                if(screen.world().combat!=null) {
                    Enemy target=screen.world().combat.enemies.stream().min(Comparator.comparingDouble(e->Math.hypot(e.x-screen.world().hero.x,e.y-screen.world().hero.y))).orElse(null);
                    if(target!=null) {
                        float dx=target.x-screen.world().hero.x,dy=target.y-screen.world().hero.y;
                        float distance=(float)Math.hypot(dx,dy);
                        input.moveX=distance>55?dx:0;input.moveY=distance>55?dy:0;
                        input.attackHeld=true;
                    }
                }
                if(elapsed>1&&frame%30==0)shot(String.format("combat_%03d",shot++));
                if(elapsed<7&&screen.world().combat!=null)return;
                check(!screen.world().hero.dead,"hero died in first battle");
                check(screen.world().combat==null,"first room did not clear with keyboard actions");
                input.attackHeld=false;input.moveX=input.moveY=0;next();break;
            case 10:
                if(elapsed<0.4f)return;
                shot("03_room_cleared");
                check(measured&&bagFrozen,"smoke coverage missing");
                Collections.sort(frameTimes);
                float median=frameTimes.get(frameTimes.size()/2),p95=frameTimes.get((int)(frameTimes.size()*0.95));
                String report="PASS\nkeyboard: new game, story skip, craft, enhance, close result, bag, resume\n"
                    +"held movement: expected distance within 1 px; bag pauses simulation\n"
                    +"combat: first room cleared using normal attack and automatic aim\n"
                    +"render frames sampled="+frameTimes.size()+", median_ms="+median*1000+", p95_ms="+p95*1000
                    +"\nGPU="+Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_RENDERER)+"\n";
                Files.writeString(output.resolve("report.txt"),report);
                System.out.println(report);Gdx.app.exit();next();break;
            default: break;
        }
    }
    private void shot(String name) {
        int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
        byte[] pixels=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
        for(int i=3;i<pixels.length;i+=4)pixels[i]=(byte)255;
        Pixmap pixmap=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        BufferUtils.copy(pixels,0,pixmap.getPixels(),pixels.length);
        PixmapIO.writePNG(Gdx.files.absolute(output.resolve(name+".png").toString()),pixmap);pixmap.dispose();
    }
}

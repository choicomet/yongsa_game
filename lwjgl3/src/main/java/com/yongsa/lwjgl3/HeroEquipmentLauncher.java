package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Facing;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.world.WorldMap;

/** Fixed-pose visual checks of the shipped renderer. Separate QA saves, hidden window, no player input. */
public final class HeroEquipmentLauncher {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Output directory required");
        System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(640,360); config.setInitialVisible(false);
        config.disableAudio(true); config.useVsync(false);
        new Lwjgl3Application(new Capture(args[0]),config);
    }

    private static final class Capture extends YongsaGame {
        private final String[] rooms = {"CAMP","L3","R1","T3"};
        private final Facing[] directions = {Facing.DOWN,Facing.UP,Facing.RIGHT,Facing.LEFT,
                Facing.DOWN_RIGHT,Facing.DOWN_LEFT,Facing.UP_RIGHT,Facing.UP_LEFT};
        private final String[] actions = {"walk0","walk3","idle","attack","skill","mine","roll","landing"};
        private final String[] weaponIds = {"blackiron","blackiron","bloodmoon","bloodmoon",
                "skyfall","skyfall","holy_sealed","holy_awakened"};
        private final FileHandle output;
        private OrthographicCamera camera;
        private GameSession session;
        private WorldRenderer renderer;
        private int index;
        Capture(String path) { output=new FileHandle(path); output.mkdirs(); }

        @Override public void create() {
            super.create(); MathUtils.random.setSeed(42);
            camera=new OrthographicCamera(640,360);
            var state=NewGame.create(balance); state.mapVersion=WorldMap.VERSION;
            session=new GameSession(balance,newMap(),state,42); session.monsters.clear();
            session.devGodMode=true;
            renderer=new WorldRenderer(session,art,fonts,batch,shapes); camera.zoom=.65f;
        }

        @Override public void render() {
            int regular=rooms.length*directions.length*actions.length;
            int weapons=weaponIds.length*2;
            String room="CAMP",name;
            Facing facing; int action;
            var weapon=session.state.equipped();
            weapon.defId="holy_sealed"; weapon.awakened=false;
            if(index<regular) {
                room=rooms[index/(directions.length*actions.length)];
                facing=directions[index/actions.length%directions.length]; action=index%actions.length;
                name=room+"/"+facing+"/"+actions[action];
            } else if(index<regular+weapons) {
                int n=(index-regular)/2;
                weapon.defId=weaponIds[n]; weapon.awakened=n%2==1;
                facing=(index-regular)%2==0?Facing.DOWN:Facing.DOWN_RIGHT; action=2;
                name="weapons/"+n+"-"+weapon.defId+"-"+facing;
            } else {
                int n=index-regular-weapons;
                facing=n<30?Facing.DOWN:Facing.DOWN_RIGHT; action=0;
                name="walk-cycle/"+facing+"/"+String.format("%03d",n%30);
            }
            var h=session.hero; var r=session.map.room(room);
            h.x=r.spawnX(balance.tileSize); h.y=r.spawnY(balance.tileSize);
            h.facing=facing; h.rollFacing=facing; h.aimX=facing.sx; h.aimY=facing.sy;
            h.moving=action<2; h.walkTime=action==1?.25f:0;
            h.swingT=0; h.mineSwingT=0; h.rollT=0; h.invuln=0; h.flash=0; h.stunT=0;
            if(action==3 || action==4) {h.swingKind=action==3?0:1; h.swingT=action==3?.09f:.125f;}
            if(action==5) h.mineSwingT=.11f;
            if(action==6 || action==7) h.rollT=action==6?h.rollTotal*.5f:.01f;
            if(index>=regular+weapons) h.walkTime=(index-regular-weapons)%30/60f;
            renderer.update(1/60f);
            camera.position.set(h.x,h.y+22,0); camera.update();
            renderer.renderLights(camera);
            Gdx.gl.glClearColor(.02f,.018f,.03f,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.render(camera);
            int error=Gdx.gl.glGetError();
            if(error!=GL20.GL_NO_ERROR) throw new IllegalStateException("GL error "+error+" in "+name);
            var pixels=ScreenUtils.getFrameBufferPixmap(0,0,640,360);
            var png=new PixmapIO.PNG(); png.setFlipY(true);
            FileHandle file=output.child(name+".png"); file.parent().mkdirs();
            try {png.write(file,pixels);} catch(java.io.IOException e) {throw new RuntimeException(e);}
            finally {png.dispose(); pixels.dispose();}
            if(index<regular && action==actions.length-1) System.out.println("EQUIPMENT_CAPTURE_OK "+room+" "+facing);
            if(++index==regular+weapons+60) {
                System.out.println("EQUIPMENT_CAPTURE_COMPLETE "+index); Gdx.app.exit();
            }
        }

        @Override public void dispose() {if(renderer!=null) renderer.dispose(); super.dispose();}
    }
}

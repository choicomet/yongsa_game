package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import java.util.*;

/** Hidden-window audit of object silhouettes, including forced front/back depth probes.
 * Some probes intentionally stand outside the walk mask to isolate sorting; the log states free/blocked.
 * Usage: LayerObjectSmokeLauncher OUTPUT [name-filter]. Never loads or writes the player's save. */
public final class LayerObjectSmokeLauncher {
    public static void main(String[] args) {
        if(args.length<1 || args.length>2)throw new IllegalArgumentException("Screenshot directory required; optional name filter");
        System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
        var config=new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);
        config.useVsync(false);config.setForegroundFPS(60);config.setIdleFPS(60);
        new Lwjgl3Application(new Audit(args[0],args.length==2?args[1]:""),config);
    }
    private record View(String name,float x,float y,float width,float height) {}
    private static final class Audit extends YongsaGame {
        final FileHandle output;
        final List<View> views=new ArrayList<>();
        final OrthographicCamera camera=new OrthographicCamera();
        GameSession session;
        WorldRenderer renderer;
        int index,frame;
        final String filter;
        Audit(String directory,String filter){output=new FileHandle(directory);output.mkdirs();this.filter=filter;}
        public void create() {
            super.create();
            var map=newMap();var state=NewGame.create(balance);
            state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
            for(var r:map.rooms)state.room(r.id).cleared=true;
            session=new GameSession(balance,map,state,42);
            for(var link:map.links)link.gateClosed=false;
            renderer=new WorldRenderer(session,art,fonts,batch,shapes);
            var joint=map.paintedJoints.stream().filter(j->j.id.equals("mine-side")).findFirst().orElseThrow();
            for(float[] uv:new float[][]{{.15f,.56f},{.26f,.61f},{.78f,.9f},{.78f,.83f}})
                views.add(new View("mine-side-"+uv[0]+"-"+uv[1],joint.x(uv[0]),joint.y(uv[1]),640,360));
            String[] ids={"mine-traverse","tower-bridge","tower-stair-bend","citadel-crossing"};
            float[][] pairs={{.5f,.76f,.5f,.68f},{.376f,.69f,.376f,.60f},{.335f,.53f,.335f,.46f},{.17f,.45f,.17f,.38f}};
            for(int i=0;i<ids.length;i++) {
                final String id=ids[i];
                var c=map.corridors.stream().filter(t->t.scene!=null&&t.scene.id.equals(id)).findFirst().orElseThrow();
                for(int side=0;side<2;side++) {
                    float u=pairs[i][side*2],v=pairs[i][side*2+1];
                    views.add(new View(id+"-"+(side==0?"front":"behind"),c.sceneX(u),c.sceneY(v),640,360));
                }
            }
            var lane=map.corridors.stream().filter(t->t.scene!=null&&t.scene.id.equals("mine-traverse")).findFirst().orElseThrow();
            views.add(new View("mine-lane-bare-floor",lane.sceneX(.38f),lane.sceneY(.55f),640,360));
            views.add(new View("mine-lane-post-approach",lane.sceneX(.571f),lane.sceneY(.676f),640,360));
            views.add(new View("mine-side-overlay",joint.x(.15f),joint.y(.56f),640,360));
            views.removeIf(v->!v.name.contains(filter));
            if(views.isEmpty())throw new IllegalArgumentException("No capture matches "+filter);
        }
        public void render() {
            var view=views.get(index);
            renderer.showWalkable=view.name.endsWith("overlay");
            camera.setToOrtho(false,Math.max(view.width,view.height*1280/720)*1.06f,Math.max(view.height,view.width*720/1280)*1.06f);
            camera.position.set(view.x,view.y,0);camera.update();
            session.hero.x=view.x;session.hero.y=view.y;
            renderer.update(frame==0?21f:1/60f);renderer.renderLights(camera);
            Gdx.gl.glClearColor(.02f,.02f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.render(camera);
            if(++frame<3)return;
            Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);
            var writer=new PixmapIO.PNG();writer.setFlipY(true);
            try{writer.write(output.child(view.name+".png"),pixels);}
            catch(java.io.IOException e){throw new RuntimeException(e);}
            finally{writer.dispose();pixels.dispose();}
            System.out.println("LAYER_CAPTURE_OK "+view.name+" walkable="+session.map.free(view.x,view.y,9,6));
            frame=0;if(++index==views.size())Gdx.app.exit();
        }
        public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
    }
}

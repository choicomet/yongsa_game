package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.world.ReviewedOcclusion;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import java.util.*;

/** Hidden-window GL audit of every placed painting, including mirrored rooms and joint edges.
 * Usage: LayerReviewSmokeLauncher OUTPUT [name-filter]. Never loads or writes the player's save. */
public final class LayerReviewSmokeLauncher {
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
            for(var r:map.rooms)if(r.scene!=null)
                views.add(new View("room-"+r.id+"-"+r.scene.id+(r.flip?"-flip":""),r.originX+r.widthPx/2,r.originY+r.heightPx/2,r.widthPx,r.heightPx));
            for(var c:map.corridors)if(c.scene!=null)
                views.add(new View("passage-"+c.link.a.id+"-"+c.link.b.id+"-"+c.scene.id,c.originX+c.widthPx/2,c.originY+c.heightPx/2,c.widthPx,c.heightPx));
            for(var j:map.paintedJoints)
                views.add(new View("joint-"+j.id,j.x(.5f),j.y(.5f),j.rect[2],j.rect[3]));
            // Close views at the reported object footings, from both sides of the depth threshold.
            var data=ReviewedOcclusion.parse(Gdx.files.internal("data/reviewed-occlusion.json").readString("UTF-8")).scenes;
            for(var r:map.rooms)if(r.scene!=null && data.containsKey("rooms/"+r.scene.id)) {
                var s=data.get("rooms/"+r.scene.id);
                for(var p:s.pieces) {
                    if(p.always())continue;
                    var rect=p.rect;float u=(rect[0]+rect[2]/2f)/s.size[0];
                    float v=p.depth;
                    for(int side:new int[]{-1,1})views.add(new View("depth-"+views.size()+"-"+r.id,r.sceneX(u),r.sceneY(v)+side*14,500,281.25f));
                }
            }
            views.removeIf(v->!v.name.contains(filter));
            if(views.isEmpty())throw new IllegalArgumentException("No capture matches "+filter);
        }
        public void render() {
            var view=views.get(index);
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
            System.out.println("LAYER_CAPTURE_OK "+view.name);
            frame=0;if(++index==views.size())Gdx.app.exit();
        }
        public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
    }
}

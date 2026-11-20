package com.yongsa.lwjgl3;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.map.*;
import com.yongsa.enemy.Monster;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.*;
import com.yongsa.screen.WorldRenderer;
import java.util.*;

/** Hidden-window audit of object silhouettes, including forced front/back depth probes.
 * Some probes intentionally stand outside the walk mask to isolate sorting; the log states free/blocked.
 * Usage: SceneLayerSmokeLauncher OUTPUT [name-filter]. Never loads or writes the player's save. */
public final class SceneLayerSmokeLauncher {
    public static void main(String[] args) {
        if(args.length<1 || args.length>2)throw new IllegalArgumentException("Screenshot directory required; optional name filter");
        System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
        var config=new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);
        config.useVsync(false);config.setForegroundFPS(60);config.setIdleFPS(60);
        new Lwjgl3Application(new Audit(args[0],args.length==2?args[1]:""),config);
    }
    private record View(String name,float x,float y,float width,float height,boolean monster,boolean debug) {}
    private static final class Audit extends YongsaGame {
        final FileHandle output;
        final List<View> views=new ArrayList<>();
        final OrthographicCamera camera=new OrthographicCamera();
        GameSession session;
        WorldRenderer renderer;
        int index,frame;
        Monster monster;
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
            var camp=map.rooms.stream().filter(r->r.scene!=null&&r.scene.id.equals("central-camp")).findFirst().orElseThrow();
            float[][] uv={{.202f,.627f},{.257f,.686f},{.044f,.512f}};
            for(int i=0;i<uv.length;i++) views.add(new View("camp-"+(i+1),camp.originX+uv[i][0]*camp.widthPx,camp.originY+(1-uv[i][1])*camp.heightPx,600,337.5f,false,false));
            var cave=map.rooms.stream().filter(r->r.scene!=null&&r.scene.id.equals("cave-mine")).findFirst().orElseThrow();
            views.add(new View("cave-rear",cave.originX+cave.widthPx*.45f,cave.originY+cave.heightPx*.565f,720,405,false,false));
            monster=new Monster(balance.dungeons.get(1).mob,false,false,balance.tileSize);
            monster.state=Monster.State.CHASE;session.monsters.clear();
            JsonValue inventory;
            try{inventory=new JsonReader().parse(Files.readString(Path.of("docs/scene-layers/inventory.json")));}
            catch(Exception e){throw new RuntimeException(e);}
            for(var raw:inventory){var item=raw;String key=item.getString("scene"),file=item.getString("file");
                var bounds=item.get("bounds");var canvas=item.get("canvas");
                float u0=bounds.getFloat(0)/canvas.getFloat(0),u1=bounds.getFloat(2)/canvas.getFloat(0);
                float v0=bounds.getFloat(1)/canvas.getFloat(1),v1=bounds.getFloat(3)/canvas.getFloat(1);
                if(file.endsWith("/front.png"))continue;
                var definition=map.sceneLayers.data.scenes.get(key).objects.stream().filter(o->o.image.equals(file)).findFirst().orElseThrow();
                SceneLayerPlacements.Placement best=null;int bestScore=-1;
                for(var p:map.sceneLayers.all)if(p.key.equals(key)){
                    int score=0;for(int i=0;i<5;i++)for(int j=0;j<5;j++)if(map.sceneLayers.owns(p,p.worldX(u0+(u1-u0)*(i+.5f)/5),p.worldY(v0+(v1-v0)*(j+.5f)/5),false))score++;
                    if(score>bestScore){best=p;bestScore=score;}
                }
                if(best==null)throw new IllegalStateException("Unplaced scene "+key);
                if (bestScore == 0) System.out.println("COVERED_BY_JOINT " + key + "/" + definition.id);
                if (filter.equals("covered") && bestScore != 0) continue;
                float u=(u0+u1)*.5f,foot=definition.footAt(u),wx=best.worldX(u),wy=best.worldY(foot);
                float x0=best.worldX(u0),x1=best.worldX(u1);
                float[][] points={{wx,wy-12},{wx,wy+12},{Math.min(x0,x1)-13,wy},{Math.max(x0,x1)+13,wy}};
                String[] sides={"front","behind","left","right"};
                for(int actor=0;actor<2;actor++)for(int side=0;side<4;side++){
                    float[] xy=points[side];String name=key.replace('/','_')+"__"+definition.id+"__"+(actor==0?"hero":"monster")+"_"+sides[side];
                    views.add(new View(name,xy[0],xy[1],300,168.75f,actor==1,false));
                }
            }
            views.add(new View("camp-layer-debug",camp.sceneX(.20f),camp.sceneY(.64f),600,337.5f,false,true));
            if (filter.equals("covered")) views.removeIf(v -> !v.name.contains("__"));
            else views.removeIf(v -> !v.name.contains(filter));
            if(views.isEmpty())throw new IllegalArgumentException("No capture matches "+filter);
        }
        public void render() {
            var view=views.get(index);
            renderer.showWalkable=view.name.endsWith("overlay");renderer.showLayers=view.debug;
            camera.setToOrtho(false,Math.max(view.width,view.height*1280/720)*1.06f,Math.max(view.height,view.width*720/1280)*1.06f);
            camera.position.set(view.x,view.y,0);camera.update();
            session.hero.x=view.monster?view.x-2000:view.x;session.hero.y=view.y;
            session.monsters.clear();if(view.monster){monster.x=view.x;monster.y=view.y;session.monsters.add(monster);}
            renderer.update(frame==0?21f:1/60f);renderer.renderLights(camera);
            Gdx.gl.glClearColor(.02f,.02f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.render(camera);
            if(++frame<3)return;
            Pixmap pixels=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);
            var writer=new PixmapIO.PNG();writer.setFlipY(true);
            try{writer.write(output.child(view.name+".png"),pixels);}
            catch(java.io.IOException e){throw new RuntimeException(e);}
            finally{writer.dispose();pixels.dispose();}
            System.out.println("SCENE_LAYER_CAPTURE "+view.name+" walkable="+session.map.free(view.x,view.y,9,6));
            frame=0;if(++index==views.size())Gdx.app.exit();
        }
        public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
    }
}

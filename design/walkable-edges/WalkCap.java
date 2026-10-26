import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.world.*;
import java.util.*;

public final class WalkCap {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File("/tmp/walkcap-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(1600,1000);cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Cap(args[0],args.length>1?args[1]:null),cfg);
 }
 record Shot(String name,float x,float y,float zoom,boolean overlay){}
 static class Cap extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;String only;
  List<Shot> shots=new ArrayList<>();
  Cap(String dir,String only){out=new FileHandle(dir);out.mkdirs();this.only=only;}
  public void create(){
   super.create();MathUtils.random.setSeed(99);var state=NewGame.create(balance);
   state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   var map=newMap();session=new GameSession(balance,map,state,42);session.monsters.clear();
   for(var l:map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(1600,1000);
   for(var r:map.rooms){
    if(only!=null&&!only.equals(r.id))continue;
    float z=Math.max(r.widthPx/1600f,r.heightPx/1000f)*1.15f;
    float cx=r.originX+r.widthPx/2,cy=r.originY+r.heightPx/2;
    shots.add(new Shot("room-"+r.id,cx,cy,z,false));
    shots.add(new Shot("room-"+r.id+"-walk",cx,cy,z,true));
   }
   if(only==null)for(var c:map.corridors){
    String key=c.link.a.id+"-"+c.link.b.id;
    float z=Math.max(c.widthPx/1600f,c.heightPx/1000f)*1.3f;z=Math.max(z,1.2f);
    shots.add(new Shot("pass-"+key,c.originX+c.widthPx/2,c.originY+c.heightPx/2,z,false));
    shots.add(new Shot("pass-"+key+"-walk",c.originX+c.widthPx/2,c.originY+c.heightPx/2,z,true));
   }
  }
  public void render(){
   var s=shots.get(shot);session.hero.x=-99999;session.hero.y=-99999;
   renderer.update(21f);cam.zoom=s.zoom;cam.position.set(s.x,s.y,0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   if(s.overlay){
    Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);shapes.setProjectionMatrix(cam.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
    float step=8*s.zoom, hw=cam.viewportWidth*s.zoom/2, hh=cam.viewportHeight*s.zoom/2;
    for(float y=s.y-hh;y<=s.y+hh;y+=step)for(float x=s.x-hw;x<=s.x+hw;x+=step){
      if(session.map.free(x,y,session.hero.hw,session.hero.hh)){shapes.setColor(.1f,1f,.3f,.38f);shapes.rect(x-step/2,y-step/2,step,step);}
    }
    shapes.end();
   }
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1600,1000);var w=new PixmapIO.PNG();w.setFlipY(true);
   try{w.write(out.child(s.name+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{w.dispose();p.dispose();}
   if(++shot==shots.size()){System.out.println("DONE "+shot);Gdx.app.exit();}
  }
 }
}

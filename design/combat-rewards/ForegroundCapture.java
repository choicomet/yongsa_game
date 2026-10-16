import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.world.*;
import java.util.*;

/** Compare source-pixel occlusion with identical lighting and valid player foot positions. */
public final class ForegroundCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File("build/foreground-qa-save").getAbsolutePath());
  var c=new Lwjgl3ApplicationConfiguration();c.setWindowedMode(1280,720);c.setInitialVisible(false);c.disableAudio(true);c.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),c);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<Shot> shots=new ArrayList<>();List<Object> report=new ArrayList<>();
  record Shot(String name,String room,float u,float v,float zoom,boolean before){}
  Capture(String dir){out=new FileHandle(dir);out.mkdirs();}
  void pair(String name,String room,float u,float v){for(boolean before:List.of(true,false))shots.add(new Shot(name+(before?"-before":"-after"),room,u,v,.45f,before));}
  public void create(){
   super.create();cam=new OrthographicCamera(1280,720);
   for(String id:List.of("CAMP","L8","L7","R2","R7","R3","R4","R5","T1","T2","T4","T5","T7","V"))shots.add(new Shot("room-"+id,id,.5f,.5f,1.4f,false));
   pair("anvil-behind","CAMP",.303f,.333f);pair("anvil-front","CAMP",.305f,.438f);
   pair("pillar-behind","R3",.305f,.350f);pair("mirrored-stairs","R2",.70f,.70f);
   pair("lamp-behind","V",.374f,.345f);pair("lamp-front","V",.374f,.405f);
   pair("tree-edge","V",.21f,.92f);pair("market-behind","V",.18f,.548f);
  }
  void setup(Shot q){
   if(renderer!=null)renderer.dispose();System.setProperty("yongsa.disableSceneOcclusion",Boolean.toString(q.before));MathUtils.random.setSeed(99);
   var s=NewGame.create(balance);s.mapVersion=WorldMap.VERSION;s.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));s.unsealed=true;
   var map=newMap();var r=map.room(q.room);s.resumeX=r.spawnX(balance.tileSize);s.resumeY=r.spawnY(balance.tileSize);
   session=new GameSession(balance,map,s,42);session.monsters.clear();session.devGodMode=true;
   for(var l:map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   float targetX=r.sceneX(q.u),targetY=r.sceneY(q.v),px=session.hero.x,py=session.hero.y,best=Float.MAX_VALUE;
   for(int dy=-70;dy<=70;dy+=2)for(int dx=-70;dx<=70;dx+=2){
    float cost=dx*dx+dy*dy;if(cost>=best)continue;
    if(map.free(targetX+dx,targetY+dy,session.hero.hw,session.hero.hh)){best=cost;px=targetX+dx;py=targetY+dy;}
   }
   if(best==Float.MAX_VALUE && !q.name.startsWith("room-"))throw new AssertionError("No valid probe: "+q.name);
   session.hero.x=px;session.hero.y=py;
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);renderer.update(.1f);
   cam.zoom=q.zoom;cam.position.set(q.name.startsWith("room-")?r.originX+r.widthPx/2:targetX,q.name.startsWith("room-")?r.originY+r.heightPx/2:targetY+22,0);cam.update();
   report.add(Map.of("name",q.name,"room",q.room,"sourcePoint",new float[]{q.u,q.v},"hero",new float[]{px,py},"footFree",map.free(px,py,session.hero.hw,session.hero.hh),"adjustment",best==Float.MAX_VALUE?-1:Math.sqrt(best)));
  }
  public void render(){
   Shot q=shots.get(shot);setup(q);renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var w=new PixmapIO.PNG();w.setFlipY(true);
   try{w.write(out.child(q.name+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{w.dispose();p.dispose();}
   System.out.println("FOREGROUND_CAPTURE_OK "+q.name);if(++shot==shots.size()){
    out.child("report.json").writeString(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report),false,"UTF-8");Gdx.app.exit();
   }
  }
  public void dispose(){System.clearProperty("yongsa.disableSceneOcclusion");if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

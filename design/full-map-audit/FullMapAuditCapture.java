import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.google.gson.GsonBuilder;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.world.*;
import java.util.*;

/** Exhaustive current-map inventory, rendered normally and with actual foot-clearance probes. */
public final class FullMapAuditCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File("build/full-map-audit-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(1280,720);cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),cfg);
 }
 record Shot(String name,String category,String location,float x,float y,float zoom,float footX,float footY,boolean overlay){}
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<Shot> shots=new ArrayList<>();List<Object> report=new ArrayList<>();
  Capture(String dir){out=new FileHandle(dir);out.mkdirs();}
  void endpoint(String name,String location,Door door){
   shots.add(new Shot(name,"endpoint",location,door.footX,door.footY,.85f,door.footX,door.footY,false));
   shots.add(new Shot(name+"-floor","floor",location,door.footX,door.footY,.85f,door.footX,door.footY,true));
  }
  public void create(){
   super.create();MathUtils.random.setSeed(99);var state=NewGame.create(balance);
   state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   var map=newMap();session=new GameSession(balance,map,state,42);session.monsters.clear();
   for(var l:map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(1280,720);
   for(var r:map.rooms)shots.add(new Shot("room-"+r.id,"room",r.id,r.originX+r.widthPx/2,r.originY+r.heightPx/2,
      Math.max(r.widthPx/1280,r.heightPx/720)*1.12f,r.spawnX(balance.tileSize),r.spawnY(balance.tileSize),false));
   for(var c:map.corridors){
    String key=c.link.a.id+"-"+c.link.b.id;
    var p=c.centerline.get(c.centerline.size()/2);
    shots.add(new Shot("link-"+key,"passage",key,p[0],p[1],1.05f,p[0],p[1],false));
    endpoint("entry-"+key,key+" / "+c.link.a.id,c.link.doorA);
    endpoint("exit-"+key,key+" / "+c.link.b.id,c.link.doorB);
   }
   if(map.rooms.size()!=25 || map.corridors.size()!=23 || shots.size()!=140)
      throw new IllegalStateException("Map inventory changed: "+map.rooms.size()+" / "+map.corridors.size()+" / "+shots.size());
  }
  public void render(){
   var s=shots.get(shot);session.hero.x=s.footX;session.hero.y=s.footY;
   renderer.update(21f);cam.zoom=s.zoom;cam.position.set(s.x,s.y,0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   if(s.overlay){
    shapes.setProjectionMatrix(cam.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(.2f,1f,.35f,1);
    for(float y=s.y-210;y<=s.y+210;y+=12)for(float x=s.x-350;x<=s.x+350;x+=12)
      if(session.map.free(x,y,session.hero.hw,session.hero.hh))shapes.circle(x,y,1.5f,6);
    shapes.end();
   }
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var writer=new PixmapIO.PNG();writer.setFlipY(true);
   try{writer.write(out.child(s.name+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{writer.dispose();p.dispose();}
   report.add(Map.of("name",s.name,"category",s.category,"location",s.location,"camera",new float[]{s.x,s.y,s.zoom},
      "hero",new float[]{s.footX,s.footY},"footFree",session.map.free(s.footX,s.footY,session.hero.hw,session.hero.hh)));
   if(++shot%20==0)System.out.println("AUDIT_CAPTURE "+shot+"/"+shots.size());
   if(shot==shots.size()){
    out.child("inventory.json").writeString(new GsonBuilder().setPrettyPrinting().create().toJson(report),false,"UTF-8");
    System.out.println("AUDIT_COMPLETE "+shots.size());Gdx.app.exit();
   }
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

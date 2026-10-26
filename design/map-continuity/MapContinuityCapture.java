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

/** Deterministic, hidden-window screenshots; never touches the player's save. */
public final class MapContinuityCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File("build/map-qa-save").getAbsolutePath());
  var c=new Lwjgl3ApplicationConfiguration();c.setWindowedMode(1280,720);c.setInitialVisible(false);c.disableAudio(true);c.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),c);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<String> names=new ArrayList<>();List<float[]> views=new ArrayList<>();
  Capture(String dir){out=new FileHandle(dir);out.mkdirs();}
  void view(String name,float x,float y,float zoom){names.add(name);views.add(new float[]{x,y,zoom});}
  public void create(){
   super.create();MathUtils.random.setSeed(99);
   var state=NewGame.create(balance);state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   var map=newMap();session=new GameSession(balance,map,state,42);session.monsters.clear();
   for(var l:map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(1280,720);
   var camp=map.room("CAMP");view("00-camp",camp.originX+camp.widthPx/2,camp.originY+camp.heightPx/2,1.15f);
   for(var c:map.corridors){
    var p=c.path.get(c.path.size()/2);
    view("link-"+c.link.a.id+"-"+c.link.b.id,(p[0]+.5f)*32,(p[1]+.5f)*32,1.05f);
   }
   for(String id:List.of("L1","L3","R2","R3","T3","T5","V")){
    var r=map.room(id);view("room-"+id,r.originX+r.widthPx/2,r.originY+r.heightPx/2,1.4f);
   }
   for(String id:List.of("L3","R3","T3")){
    var r=map.room(id);view("overview-"+id,r.originX+r.widthPx/2,r.originY+r.heightPx/2,3.5f);
   }
   var survey=new java.util.LinkedHashMap<String,Object>();
   var rs=new ArrayList<Object>();var cs=new ArrayList<Object>();
   for(var r:map.rooms)rs.add(Map.of("id",r.id,"image",r.scene.image,"rect",new float[]{r.originX,r.originY,r.widthPx,r.heightPx}));
   for(var c:map.corridors)cs.add(Map.of("id",c.key(),"theme",c.theme,"path",c.path));
   survey.put("rooms",rs);survey.put("corridors",cs);
   out.child("layout.json").writeString(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(survey),false,"UTF-8");
  }
  public void render(){
   float[] v=views.get(shot);session.hero.x=v[0];session.hero.y=v[1];
   renderer.update(21f);cam.zoom=v[2];cam.position.set(v[0],v[1],0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var w=new PixmapIO.PNG();w.setFlipY(true);
   try{w.write(out.child(names.get(shot)+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{w.dispose();p.dispose();}
   System.out.println("MAP_CAPTURE_OK "+names.get(shot));if(++shot==names.size())Gdx.app.exit();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

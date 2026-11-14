import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.google.gson.*;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.map.*;
import java.lang.reflect.*;
import java.util.*;

/** Exports exact terrain and existing floor samples for the 39 audited repair sites. */
public final class RepairArtCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File("build/map-repair-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(1024,1024);cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),cfg);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<Map<String,Object>> entries=new ArrayList<>();
  Capture(String dir){out=new FileHandle(dir);out.mkdirs();}
  public void create(){
   super.create();var state=NewGame.create(balance);state.unsealed=true;state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));
   var map=newMap();session=new GameSession(balance,map,state,42);session.monsters.clear();
   for(var l:map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(512,512);
   var findings=JsonParser.parseString(Gdx.files.local("design/full-map-audit/findings.json").readString("UTF-8")).getAsJsonObject().getAsJsonArray("endpointReviews");
   for(var f:findings){var item=f.getAsJsonObject();if(item.get("status").getAsString().equals("유지"))continue;
    String id=item.get("id").getAsString();String[] key=id.split("-");
    var link=map.links.stream().filter(l->l.a.id.equals(key[1])&&l.b.id.equals(key[2])).findFirst().orElseThrow();
    Door d=key[0].equals("entry")?link.doorA:link.doorB;
    var entry=new LinkedHashMap<String,Object>();entry.put("id",id);entry.put("room",d.room.id);
    entry.put("image","scenes/joints-v2/"+id+".png");
    boolean approach=Boolean.getBoolean("yongsa.captureApproaches") && (id.equals("entry-CAMP-L1")||id.equals("entry-CAMP-R1"));
    if(Boolean.getBoolean("yongsa.captureApproaches")&&!approach)continue;
    entry.put("rect",approach?new float[]{d.footX-(id.endsWith("L1")?640:128),d.footY-256,768,768}:new float[]{d.footX-256,d.footY-256,512,512});
    entry.put("door",new float[]{d.footX,d.footY});entry.put("direction",new float[]{d.dirX,d.dirY});entry.put("feather",32);
    entry.put("notes",item.get("notes").getAsString());entry.put("status",item.get("status").getAsString());
    List<String> floor=new ArrayList<>();
    for(int v=0;v<=128;v++){var row=new StringBuilder();for(int u=0;u<=128;u++)row.append(map.free(d.footX-256+u*4,d.footY+256-v*4,0,0)?'1':'0');floor.add(row.toString());}
    entry.put("floorSamples",floor);entries.add(entry);
   }
   if(entries.size()!=(Boolean.getBoolean("yongsa.captureApproaches")?2:39))throw new IllegalStateException("Audit inventory changed");
   out.child("sites.json").writeString(new GsonBuilder().setPrettyPrinting().create().toJson(entries),false,"UTF-8");
  }
  Object call(Object instance,String name,Class<?>[] types,Object...args)throws Exception{var m=instance.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(instance,args);}
  public void render(){try{
   var e=entries.get(shot);float[] r=(float[])e.get("rect");cam.viewportWidth=r[2];cam.viewportHeight=r[3];cam.position.set(r[0]+r[2]/2,r[1]+r[3]/2,0);cam.update();
   call(renderer,"computeView",new Class<?>[]{OrthographicCamera.class},cam);
   var f=WorldRenderer.class.getDeclaredField("passages");f.setAccessible(true);var passages=f.get(renderer);
   var sig=new Class<?>[]{com.badlogic.gdx.graphics.g2d.SpriteBatch.class,float.class,float.class,float.class,float.class};
   Gdx.gl.glViewport(0,0,1024,1024);Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
   batch.setProjectionMatrix(cam.combined);batch.begin();batch.setColor(Color.WHITE);
   call(passages,"drawGround",sig,batch,r[0],r[1],r[0]+r[2],r[1]+r[3]);call(passages,"drawWalls",sig,batch,r[0],r[1],r[0]+r[2],r[1]+r[3]);
   call(renderer,"drawScenes",new Class<?>[]{});var jf=passages.getClass().getDeclaredField("joints");jf.setAccessible(true);
   call(jf.get(passages),"draw",sig,batch,r[0],r[1],r[0]+r[2],r[1]+r[3]);batch.end();
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1024,1024);var w=new PixmapIO.PNG();w.setFlipY(true);
   try{w.write(out.child(e.get("id")+".png"),p);}finally{w.dispose();p.dispose();}
   System.out.println("REPAIR_SOURCE "+e.get("id"));if(++shot==entries.size())Gdx.app.exit();
  }catch(Exception e){throw new RuntimeException(e);}}
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

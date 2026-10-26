import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Hazard;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import java.util.*;

/** Real runtime renderer, hidden GL window, isolated save directory. */
public final class CombatRewardCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File("build/rewards-qa-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(1280,720);cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),cfg);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<String> names=new ArrayList<>();
  Capture(String dir){out=new FileHandle(dir);out.mkdirs();}
  public void create(){
   super.create();MathUtils.random.setSeed(99);cam=new OrthographicCamera(1280,720);
   for(String r:List.of("L6","R6","T6"))for(String state:List.of("closed","opening","open","fade"))names.add(r+"-"+state);
   names.addAll(List.of("eye-closed","eye-slit","eye-half","eye-trigger"));
  }
  void setup(String name){
   if(renderer!=null)renderer.dispose();
   var s=NewGame.create(balance);s.mapVersion=com.yongsa.world.WorldMap.VERSION;s.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));s.unsealed=true;
   var map=newMap();boolean eye=name.startsWith("eye-");var room=map.room(eye?"F":name.substring(0,2));
   s.resumeX=room.spawnX(balance.tileSize);s.resumeY=room.spawnY(balance.tileSize);
   session=new GameSession(balance,map,s,42);session.monsters.clear();session.devGodMode=true;
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);
   float x=eye?room.sceneX(.5f):session.chestX(room),y=eye?room.sceneY(.5f):session.chestY(room);
   session.hero.x=x-75;session.hero.y=y-12;
   if(!eye){
    s.chests.add(room.id);
    if(!name.endsWith("closed")){
     session.openChest(room);for(var e:session.pollEvents())renderer.onEvent(e);
     renderer.update(name.endsWith("opening")?.08f:name.endsWith("fade")?2.55f:.95f);
    }
   }else{
    Hazard h=new Hazard(Hazard.Kind.EYE,"eye");h.x=x;h.y=y;h.radius=balance.tileSize*.65f;h.warning=1f;
    h.delay=name.endsWith("closed")?1f:name.endsWith("slit")?.5f:.15f;
    if(name.endsWith("trigger")){
     var e=GameEvent.fx(GameEvent.Type.HAZARD_FX,x,y);e.text="eye";e.size=h.radius;renderer.onEvent(e);renderer.update(.15f);
    }else session.hazards.add(h);
   }
   cam.zoom=.45f;cam.position.set(x-18,y+18,0);cam.update();
  }
  public void render(){
   String name=names.get(shot);setup(name);renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var w=new PixmapIO.PNG();w.setFlipY(true);
   try{w.write(out.child(name+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{w.dispose();p.dispose();}
   System.out.println("REWARD_CAPTURE_OK "+name);if(++shot==names.size())Gdx.app.exit();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.map.*;
import java.util.*;

/** Actual-renderer evidence of all 46 closed room boundaries; never uses the player's save. */
public final class GateCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File("build/gate-review-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(960,720);cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),cfg);
 }
 record Shot(String id,Door door){}
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<Shot> shots=new ArrayList<>();
  Capture(String path){out=new FileHandle(path);out.mkdirs();}
  public void create(){
   super.create();MathUtils.random.setSeed(99);var state=NewGame.create(balance);
   state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   var map=newMap();session=new GameSession(balance,map,state,42);session.monsters.clear();
   for(var c:map.corridors){
    String key=c.link.a.id+"-"+c.link.b.id;
    shots.add(new Shot("entry-"+key,c.link.doorA));shots.add(new Shot("exit-"+key,c.link.doorB));
   }
   for(var l:map.links)if(l.transition)for(var d:List.of(l.doorA,l.doorB))
    if(d.room.type!=RoomType.TUNNEL)shots.add(new Shot("transition-"+d.room.id+"-"+d.name,d));
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(720,540);
  }
  public void render(){
   var s=shots.get(shot);var d=s.door;
   for(var l:session.map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   d.lockedByFight=true;
   session.hero.x=d.footX-d.dirX*72;session.hero.y=d.footY-d.dirY*72;
   renderer.update(21);cam.position.set(d.footX,d.footY,0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var pixels=ScreenUtils.getFrameBufferPixmap(0,0,960,720);var png=new PixmapIO.PNG();png.setFlipY(true);
   try{png.write(out.child(s.id+".png"),pixels);}catch(Exception e){throw new RuntimeException(e);}finally{png.dispose();pixels.dispose();}
   System.out.println("GATE_CAPTURE "+s.id);
   if(++shot==shots.size())Gdx.app.exit();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

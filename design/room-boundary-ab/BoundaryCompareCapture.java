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

/** Preview-only image substitution; production assets, code and player saves are untouched. */
public final class BoundaryCompareCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File("build/boundary-ab-save").getAbsolutePath());
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);
  config.setInitialVisible(false);config.disableAudio(true);config.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),config);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<PaintedJointLayout.Joint> joints;String variant;
  Capture(String path){out=new FileHandle(path);out.mkdirs();}
  public void create(){super.create();cam=new OrthographicCamera(1280,720);setup("A");}
  void setup(String value){
   if(renderer!=null)renderer.dispose();variant=value;MathUtils.random.setSeed(99);
   for(var j:mapDef.joints)j.image="design/room-boundary-ab/"+variant+"/"+j.id+".png";
   var state=NewGame.create(balance);state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   session=new GameSession(balance,newMap(),state,42);session.monsters.clear();
   for(var l:session.map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);joints=session.map.paintedJoints;
  }
  public void render(){
   if(shot==2)setup("B");
   var j=joints.get(shot%2);boolean north=j.id.equals("mine-north");
   session.hero.x=j.door[0]+(north?0:32);session.hero.y=j.door[1]-(north?32:20);
   if(!session.map.free(session.hero.x,session.hero.y,9,6))throw new IllegalStateException("Preview foot outside floor");
   renderer.update(21);cam.zoom=.85f;cam.position.set(j.door[0],j.door[1],0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var writer=new PixmapIO.PNG();writer.setFlipY(true);
   try{writer.write(out.child(variant+"-"+j.id+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{writer.dispose();p.dispose();}
   System.out.println("BOUNDARY_PREVIEW_OK "+variant+" "+j.id);if(++shot==4)Gdx.app.exit();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

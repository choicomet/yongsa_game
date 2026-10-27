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

/** Ground and occlusion checks on both sides of the repaint, with closed gates too. */
public final class JointWalkCapture {
 public static void main(String[] args) {
  System.setProperty("yongsa.saveDir",new java.io.File("build/joint-walk-qa-save").getAbsolutePath());
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1280,720);
  config.setInitialVisible(false);config.disableAudio(true);config.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),config);
 }
 record Shot(String name,PaintedJointLayout.Joint joint,float x,float y,boolean locked){}
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<Shot> shots=new ArrayList<>();List<String> report=new ArrayList<>();
  Capture(String path){out=new FileHandle(path);out.mkdirs();}
  public void create(){
   super.create();MathUtils.random.setSeed(99);var state=NewGame.create(balance);
   state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   session=new GameSession(balance,newMap(),state,42);session.monsters.clear();
   for(var l:session.map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(1280,720);
   for(var j:session.map.paintedJoints){
    float[][] feet=j.id.equals("mine-north")?new float[][]{{.50f,.35f},{.50f,.45f},{.50f,.53f},{.50f,.66f}}:
      new float[][]{{.35f,.58f},{.50f,.53f},{.63f,.54f},{.76f,.60f}};
    for(int i=0;i<feet.length;i++)shots.add(new Shot(j.id+"-"+i,j,j.x(feet[i][0]),j.y(feet[i][1]),false));
    shots.add(new Shot(j.id+"-locked",j,j.door[0],j.door[1],true));
   }
  }
  public void render(){
   var s=shots.get(shot);var j=s.joint;
   var door=session.map.room(j.room).doors.stream().filter(d->Math.abs(d.footX-j.door[0])<.02f && Math.abs(d.footY-j.door[1])<.02f).findFirst().orElseThrow();
   door.lockedByFight=s.locked;
   boolean free=session.map.free(s.x,s.y,9,6);
   if(free==s.locked)throw new IllegalStateException("Wrong floor/gate collision: "+s.name);
   report.add(s.name+": "+(free?"walkable":"gate blocks movement"));
   session.hero.x=s.x;session.hero.y=s.y;renderer.update(21);
   cam.zoom=.85f;cam.position.set(j.door[0],j.door[1],0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var writer=new PixmapIO.PNG();writer.setFlipY(true);
   try{writer.write(out.child(s.name+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{writer.dispose();p.dispose();}
   System.out.println("JOINT_WALK_CAPTURE_OK "+s.name);door.lockedByFight=false;
   if(++shot==shots.size()){out.child("report.txt").writeString(String.join("\n",report),false,"UTF-8");Gdx.app.exit();}
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

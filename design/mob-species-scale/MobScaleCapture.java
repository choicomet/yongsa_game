import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.enemy.Monster;
import com.yongsa.screen.WorldRenderer;

/** External deterministic visual QA; isolated save directory, unchanged game sources. */
public final class MobScaleCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
  var c=new Lwjgl3ApplicationConfiguration();c.setWindowedMode(1280,720);c.setInitialVisible(false);c.disableAudio(true);c.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),c);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;int shot;OrthographicCamera cam;
  String[] names={"idle","walk","warning","stunned","death","spawn","left"};
  Capture(String dir){out=new FileHandle(dir);out.mkdirs();}
  public void create(){super.create();cam=new OrthographicCamera(1280,720);}
  public void render(){
   if(renderer!=null)renderer.dispose();MathUtils.random.setSeed(99);
   var state=NewGame.create(balance);var map=newMap();var room=map.room("CAMP");
   float cx=room.sceneX(.50f),cy=room.sceneY(.67f);
   state.resumeX=cx-170;state.resumeY=cy;
   var s=new GameSession(balance,map,state,42);s.monsters.clear();
   for(int i=0;i<3;i++){
    var m=new Monster(balance.dungeons.get(i).mob,false,false,balance.tileSize);
    m.x=cx-50+i*130;m.y=cy;m.hp=m.maxHp*2/3;
    if(!map.free(m.x,m.y,9,6))throw new AssertionError("Invalid staging ground");
    m.enter(Monster.State.CHASE,1);m.walkTime=0;
    if(shot==1)m.walkTime=.13f;
    if(shot==2){m.enter(i==0?Monster.State.SWOOP_TELEGRAPH:i==1?Monster.State.TELEGRAPH:Monster.State.CAST_TELEGRAPH,1);m.stateT=.35f;m.lockX=-1;}
    if(shot==3)m.enter(Monster.State.STUNNED,1);
    if(shot==5){m.enter(Monster.State.SPAWN,1);m.stateT=.8f;}
    if(shot==6)m.dirX=-1;
    s.monsters.add(m);
   }
   renderer=new WorldRenderer(s,art,fonts,batch,shapes);renderer.update(0);
   if(shot==4)for(var m:s.monsters){var e=GameEvent.fx(GameEvent.Type.MONSTER_DIED,m.x,m.y);e.sprite=m.def.sprite;e.size=1;e.flip=false;renderer.onEvent(e);m.dead=true;}
   cam.zoom=2f/3f*100f/115f;cam.position.set(cx,cy+16,0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var writer=new PixmapIO.PNG();writer.setFlipY(true);
   try{writer.write(out.child(names[shot]+".png"),p);}catch(Exception ex){throw new RuntimeException(ex);}finally{writer.dispose();p.dispose();}
   System.out.println("MOB_CAPTURE_OK "+names[shot]);if(++shot==names.length)Gdx.app.exit();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

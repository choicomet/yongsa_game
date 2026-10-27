import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.world.*;
import java.util.*;

/** Capture the actual movement -> lock -> final wave -> unlock flow, with isolated saves. */
public final class GateEntryCapture {
 public static void main(String[] args) {
  System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(960,720);
  cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),cfg);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int theme,phase;
  Room room;Door door;String[] rooms={"L1","R1","T1"};
  Capture(String path){out=new FileHandle(path);out.mkdirs();}
  public void create(){super.create();cam=new OrthographicCamera(640,480);setup();}
  void setup() {
   if(renderer!=null)renderer.dispose();
   var state=NewGame.create(balance);state.mapVersion=WorldMap.VERSION;
   state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   session=new GameSession(balance,newMap(),state,42);session.devGodMode=true;
   room=session.map.room(rooms[theme]);
   door=room.doors.stream().filter(d->!d.dead()&&d.partner().room.type==RoomType.TUNNEL).findFirst().orElseThrow();
   session.hero.x=door.arriveX;session.hero.y=door.arriveY;
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);
   step(new Controls());renderer.update(0);
   if(session.fighting())throw new AssertionError("Unsafe arrival");
  }
  void step(Controls controls){session.update(1/60f,controls);for(var event:session.pollEvents())renderer.onEvent(event);}
  public void render() {
   String label="arrival";
   if(phase==1) {
    var input=new Controls();
    for(int i=0;i<300&&!session.fighting();i++) {
     float dx=(room.centerTileX()+.5f)*balance.tileSize-session.hero.x,dy=(room.centerTileY()+.5f)*balance.tileSize-session.hero.y;
     float len=(float)Math.hypot(dx,dy);input.moveX=dx/Math.max(1,len);input.moveY=dy/Math.max(1,len);step(input);
    }
    if(!session.fighting())throw new AssertionError("No lock: "+room.id);
    label="closing-0";
   } else if(phase==2||phase==3){renderer.update(.07f);label="closing-"+(phase==2?"70":"140");}
   else if(phase==4){renderer.update(.14f);label="closed-280";}
   else if(phase==5) {
    for(int i=0;i<room.waves&&session.fighting();i++){session.debugKillAll();step(new Controls());}
    if(session.fighting())throw new AssertionError("Gate stayed locked");
    label="opening-0";
   } else if(phase==6||phase==7){renderer.update(.18f);label=phase==6?"opening-180":"open-360";}
   cam.position.set(door.gateX(),door.gateY()+60,0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var p=ScreenUtils.getFrameBufferPixmap(0,0,960,720);var png=new PixmapIO.PNG();png.setFlipY(true);
   try{png.write(out.child(room.id+"-"+phase+"-"+label+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{png.dispose();p.dispose();}
   if(++phase==8){System.out.println("GATE_ENTRY "+room.id+" passed");phase=0;if(++theme==rooms.length)Gdx.app.exit();else setup();}
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

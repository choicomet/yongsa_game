import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.*;
import com.yongsa.world.*;
import java.util.*;

/** Actual GameScreen/HUD and WorldRenderer captures; isolated from player saves. */
public final class CaveDirectionCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(1280,720);cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),cfg);
 }
 static class Capture extends YongsaGame {
  FileHandle out;int shot,frame;WorldRenderer renderer;OrthographicCamera cam;GameScreen screen;
  String[] names={"camp-overview","camp-left","camp-right","camp-top","cave-left","cave-right","cave-top","cave-left-hud","cave-right-hud","cave-top-hud"};
  Capture(String path){out=new FileHandle(path);out.mkdirs();}
  public void create(){super.create();setup();}
  void setup(){
   if(renderer!=null){renderer.dispose();renderer=null;}
   var state=NewGame.create(balance);state.mapVersion=WorldMap.VERSION;state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;state.hp=700;
   var map=newMap();var room=map.room(shot<4?"CAMP":new String[]{"CAVE_LEFT","CAVE_RIGHT","CAVE_TOP"}[(shot-4)%3]);
   float hx=room.spawnX(balance.tileSize),hy=room.spawnY(balance.tileSize);
   if(shot==0){hx=room.sceneX(.515f);hy=room.sceneY(.43f);}
   if(shot>0&&shot<4){String name=new String[]{"LEFT","RIGHT","UP"}[shot-1];var door=room.doors.stream().filter(d->d.name.equals(name)).findFirst().orElseThrow();hx=door.arriveX;hy=door.arriveY;}
   state.resumeX=hx;state.resumeY=hy;
   if(shot<4||shot>=7){
    settings.zoomNormal=shot==0?80:115;var previous=getScreen();screen=new GameScreen(this,state);setScreen(screen);if(previous!=null)previous.dispose();
   }else{
    var session=new GameSession(balance,map,state,42);renderer=new WorldRenderer(session,art,fonts,batch,shapes);renderer.update(0);
    cam=new OrthographicCamera(1280,720);cam.zoom=Math.max(room.widthPx/1280,room.heightPx/720)*1.08f;
    cam.position.set(room.originX+room.widthPx/2,room.originY+room.heightPx/2,0);cam.update();
   }
  }
  public void render(){
   if(renderer==null)screen.render(1/60f);
   else{renderer.update(1/60f);renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);}
   if(++frame<90)return;
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var png=new PixmapIO.PNG();png.setFlipY(true);
   try{png.write(out.child(names[shot]+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{png.dispose();p.dispose();}
   System.out.println("CAVE_CAPTURE "+names[shot]);frame=0;if(++shot==names.length)Gdx.app.exit();else setup();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

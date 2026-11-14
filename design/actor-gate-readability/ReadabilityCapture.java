import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.combat.*;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.map.*;
import java.util.*;

/** Hidden real renderer, fixed seed/camera; never reads or writes the player's save. */
public final class ReadabilityCapture {
 public static void main(String[] args) {
  System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(1280,720);
  cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),cfg);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  String[] rooms={"L1","R1","T1","L6","R6","T6","CAMP","CAMP","CAMP","CAMP"};
  Capture(String path){out=new FileHandle(path);out.mkdirs();}
  public void create() {
   super.create();var state=NewGame.create(balance);state.mapVersion=WorldMap.VERSION;
   state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   session=new GameSession(balance,newMap(),state,42);
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(1008,567);
  }
  public void render() {
   var room=session.map.room(rooms[shot]);
   float x=room.spawnX(balance.tileSize),y=room.spawnY(balance.tileSize);
   session.hero.x=x;session.hero.y=y;session.hero.facing=Facing.DOWN;
   session.hero.rollT=0;session.monsters.clear();
   if(shot<6) {
    var d=balance.dungeon(room.dungeonId);boolean boss=shot>=3;
    var mob=new Monster(boss?d.boss:d.mob,boss,false,balance.tileSize);
    mob.x=x+120;mob.y=y+16;session.monsters.add(mob);
    if(!boss){var other=new Monster(d.mob,false,false,balance.tileSize);other.x=x-120;other.y=y+65;other.dirX=-1;session.monsters.add(other);}
   } else if(shot>=7) {
    session.hero.rollFacing=shot==8?Facing.LEFT:Facing.RIGHT;
    session.hero.rollT=session.hero.rollTotal*(shot==7?.94f:shot==8?.50f:.08f);
   }
   renderer.update(1/60f);cam.position.set(x+40,y+65,0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var png=new PixmapIO.PNG();png.setFlipY(true);
   try{png.write(out.child(String.format("%02d-%s.png",shot,room.id)),p);}catch(Exception e){throw new RuntimeException(e);}finally{png.dispose();p.dispose();}
   System.out.println("READABILITY "+shot+" "+room.id);
   if(++shot==rooms.length)Gdx.app.exit();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

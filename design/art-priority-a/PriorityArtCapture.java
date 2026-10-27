import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.ui.Hud;

/** Stages real doors, drops and HUD without changing any production code or save. */
public final class PriorityArtCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
  var c=new Lwjgl3ApplicationConfiguration();c.setWindowedMode(1280,720);c.setInitialVisible(false);c.disableAudio(true);c.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),c);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;int shot;OrthographicCamera cam;
  String[] ids={"L7","L7","R7","R7","T7","T7","CAMP","CAMP"};
  String[] names={"mine-front","mine-side","citadel-front","citadel-side","tower-front","tower-side","potion-and-skills","skills-unavailable"};
  Capture(String dir){out=new FileHandle(dir);out.mkdirs();}
  public void create(){super.create();cam=new OrthographicCamera(1280,720);}
  public void render(){
   if(renderer!=null)renderer.dispose();
   var state=NewGame.create(balance);var map=newMap();var room=map.room(ids[shot]);
   float cx,cy;
   if(shot<6){var d=room.doors.stream().filter(x->shot%2==0?x.horizontal():!x.horizontal()).findFirst().orElseThrow();
    state.resumeX=d.footX-d.dirX*80;state.resumeY=d.footY-d.dirY*80;cx=d.footX-d.dirX*35;cy=d.footY-d.dirY*35;
   }else{state.resumeX=room.sceneX(.5f);state.resumeY=room.sceneY(.65f);cx=state.resumeX;cy=state.resumeY;}
   if(shot>=6){state.unsealed=true;
    for(var def:balance.weapons){if(def.skill!=null&&!def.id.equals(Rules.HOLY_SEALED)){
      var w=state.newWeapon(def.id);w.level=10;
      if(!def.id.equals(Rules.HOLY_AWAKENED)){state.skillOrder.add(def.id);if(shot==6)state.learnedSkills.add(def.id);}
      else if(shot==6)state.equippedUid=w.uid;
    }}
    state.potionDrops.add(new float[]{cx+65,cy});state.potionDrops.add(new float[]{cx-65,cy+18});
   }
   var session=new GameSession(balance,map,state,42);session.monsters.clear();
   if(shot<6)for(var d:room.doors)d.lockedByFight=true;
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);renderer.update(0);
   cam.zoom=2f/3f*100f/115f;cam.position.set(cx,cy+35,0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   if(shot>=6){var hud=new Hud(session,art,fonts,batch,shapes);hud.update(.016f);hud.render(new Matrix4().setToOrtho2D(0,0,1280,720),1280,720);}
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var writer=new PixmapIO.PNG();writer.setFlipY(true);
   try{writer.write(out.child(names[shot]+".png"),p);}catch(Exception ex){throw new RuntimeException(ex);}finally{writer.dispose();p.dispose();}
   System.out.println("PRIORITY_CAPTURE_OK "+names[shot]);if(++shot==names.length)Gdx.app.exit();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

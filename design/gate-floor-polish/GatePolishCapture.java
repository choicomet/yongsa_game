import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.world.*;
import java.util.*;

/** Real renderer: every open/closed boundary plus the reported tower floor, separate save. */
public final class GatePolishCapture {
 public static void main(String[] args){
  System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
  var cfg=new Lwjgl3ApplicationConfiguration();cfg.setWindowedMode(960,720);
  cfg.setInitialVisible(false);cfg.disableAudio(true);cfg.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),cfg);
 }
 record Shot(String id,Door door,boolean closed,float x,float y,boolean probes){}
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  List<Shot> shots=new ArrayList<>();
  Capture(String path){out=new FileHandle(path);out.mkdirs();}
  public void create(){
   super.create();var state=NewGame.create(balance);state.mapVersion=WorldMap.VERSION;
   state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));state.unsealed=true;
   var map=newMap();session=new GameSession(balance,map,state,42);session.monsters.clear();
   Set<Door> seen=Collections.newSetFromMap(new IdentityHashMap<>());
   for(var l:map.links)for(var d:List.of(l.doorA,l.doorB)) {
    if(d.room.type==RoomType.TUNNEL || !seen.add(d))continue;
    String id=d.room.id+"-"+d.name;
    for(boolean closed:new boolean[]{false,true})
     shots.add(new Shot(id+(closed?"-closed":"-open"),d,closed,d.gateX(),d.gateY(),false));
   }
   var j=map.paintedJoints.stream().filter(p->p.id.equals("exit-T1-T2")).findFirst().orElseThrow();
   for(boolean probes:new boolean[]{false,true})shots.add(new Shot("tower-floor"+(probes?"-probes":""),null,false,j.x(.05f),j.y(.80f),probes));
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(720,540);
  }
  public void render(){
   var s=shots.get(shot);
   for(var l:session.map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
   if(s.door!=null)s.door.lockedByFight=s.closed;
   session.hero.x=s.door==null?s.x:s.door.arriveX;session.hero.y=s.door==null?s.y:s.door.arriveY;
   renderer.update(1);cam.position.set(s.x,s.y+55,0);cam.update();
   renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
   if(s.probes){
    shapes.setProjectionMatrix(cam.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.setColor(.2f,1,.35f,1);
    for(float y=s.y-180;y<s.y+200;y+=8)for(float x=s.x-310;x<s.x+310;x+=8)
     if(session.map.free(x,y,9,6))shapes.circle(x,y,1.2f,6);
    shapes.end();
   }
   var p=ScreenUtils.getFrameBufferPixmap(0,0,960,720);var png=new PixmapIO.PNG();png.setFlipY(true);
   try{png.write(out.child(s.id+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{png.dispose();p.dispose();}
   if(++shot%20==0)System.out.println("GATE_POLISH "+shot+"/"+shots.size());
   if(shot==shots.size()){System.out.println("GATE_POLISH_COMPLETE "+shot);Gdx.app.exit();}
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

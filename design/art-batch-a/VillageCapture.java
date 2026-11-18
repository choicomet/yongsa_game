import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.*;
import com.yongsa.map.*;
import java.util.*;

/** External QA helper; does not modify the game or production save files. */
public final class VillageCapture {
 public static void main(String[] args) {
  System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
  var config=new Lwjgl3ApplicationConfiguration();
  config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),config);
 }
 static class Capture extends YongsaGame {
  FileHandle output; int shot,frame; WorldRenderer renderer; GameSession session;
  OrthographicCamera camera;
  String[] names={"village-trapped-overview","village-free-overview","village-plaza-hud","village-bakery-hud","village-north-hud"};
  Capture(String dir){output=new FileHandle(dir);output.mkdirs();}
  public void create(){super.create();camera=new OrthographicCamera(1280,720);checkPaths();setup();}
  void checkPaths(){
   var map=newMap(); var room=map.rooms.stream().filter(r->r.type==RoomType.VILLAGE).findFirst().orElseThrow();
   int step=4,w=(int)Math.ceil(room.widthPx/step),h=(int)Math.ceil(room.heightPx/step);
   boolean[] free=new boolean[w*h],seen=new boolean[w*h];
   for(int y=0;y<h;y++)for(int x=0;x<w;x++)free[y*w+x]=room.allowsFoot(room.originX+(x+.5f)*step,room.originY+(y+.5f)*step,10);
   int start=nearest(room,w,h,step,free,room.spawnX(balance.tileSize),room.spawnY(balance.tileSize));
   var queue=new ArrayDeque<Integer>();queue.add(start);seen[start]=true;
   while(!queue.isEmpty()){int i=queue.remove(),x=i%w,y=i/w;
    for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int nx=x+d[0],ny=y+d[1];
     if(nx<0||ny<0||nx>=w||ny>=h)continue;int n=ny*w+nx;if(free[n]&&!seen[n]){seen[n]=true;queue.add(n);}}
   }
   for(var v:balance.village.villagers){float x=room.sceneX(v.at[0]),y=room.sceneY(v.at[1]);
    if(!room.allowsFoot(x,y,10))throw new AssertionError("Blocked NPC: "+v.kind);
    int n=nearest(room,w,h,step,free,x,y);if(!seen[n])throw new AssertionError("Unreachable NPC: "+v.kind);
    System.out.println("VILLAGE_PATH_OK "+v.kind);
   }
   for(var p:room.scene.portals){float x=room.sceneX(p.foot[0]),y=room.sceneY(p.foot[1]);
    if(!room.allowsFoot(x,y,10)||!seen[nearest(room,w,h,step,free,x,y)])throw new AssertionError("Blocked portal: "+p.id);
    System.out.println("VILLAGE_PATH_OK portal "+p.id);
   }
   if(room.allowsFoot(room.sceneX(.50f),room.sceneY(.56f),0))throw new AssertionError("Well is walkable");
  }
  int nearest(Room r,int w,int h,int step,boolean[] free,float wx,float wy){
   float best=Float.MAX_VALUE;int result=-1;
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){if(!free[y*w+x])continue;
    float dx=r.originX+(x+.5f)*step-wx,dy=r.originY+(y+.5f)*step-wy,d=dx*dx+dy*dy;
    if(d<best){best=d;result=y*w+x;}}
   if(result<0||best>step*step*2)throw new AssertionError("Anchor not near valid ground");return result;
  }
  void setup(){
   if(renderer!=null){renderer.dispose();renderer=null;}
   var state=NewGame.create(balance);state.exitRefusals=6;state.finalBossDefeated=shot!=0;
   var map=newMap();var room=map.rooms.stream().filter(r->r.type==RoomType.VILLAGE).findFirst().orElseThrow();
   float u=shot==3?.29f:shot==4?.50f:.59f,v=shot==3?.72f:shot==4?.21f:.52f;
   state.resumeX=room.sceneX(u);state.resumeY=room.sceneY(v);
   if(shot<2){
    session=new GameSession(balance,map,state,42);session.monsters.clear();
    renderer=new WorldRenderer(session,art,fonts,batch,shapes);renderer.update(0);
    camera.zoom=1;camera.position.set(room.originX+room.widthPx/2,room.originY+room.heightPx/2,0);camera.update();
   }else{var previous=getScreen();setScreen(new GameScreen(this,state));if(previous!=null)previous.dispose();}
  }
  public void render(){
   if(shot<2){renderer.renderLights(camera);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(camera);}
   else super.render();
   if(++frame<100)return;
   Pixmap p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var writer=new PixmapIO.PNG();writer.setFlipY(true);
   try{writer.write(output.child(names[shot]+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{writer.dispose();p.dispose();}
   System.out.println("VILLAGE_CAPTURE_OK "+names[shot]);frame=0;
   if(++shot==names.length){Gdx.app.exit();return;}setup();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Facing;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.world.*;
import java.util.*;

/** Real-renderer captures at fixed 60Hz; separate QA saves, no player save access. */
public final class CharacterMotionCapture {
 public static void main(String[] args) {
  System.setProperty("yongsa.saveDir",new java.io.File("build/character-motion-qa-save").getAbsolutePath());
  var c=new Lwjgl3ApplicationConfiguration();c.setWindowedMode(640,360);c.setInitialVisible(false);c.disableAudio(true);c.useVsync(false);
  new Lwjgl3Application(new Capture(args[0],args.length>1?args[1]:".*"),c);
 }
 static class Capture extends YongsaGame {
  FileHandle out; GameSession session; WorldRenderer renderer; OrthographicCamera cam;
  List<String> names=new ArrayList<>();int scene,frame;float dx,dy;boolean still;final String filter;
  Capture(String path,String filter){out=new FileHandle(path);out.mkdirs();this.filter=filter;}
  public void create() {
   super.create();cam=new OrthographicCamera(640,360);
   for(var v:balance.village.villagers){names.add("free-"+v.kind);names.add("trapped-"+v.kind);}
   for(String act:List.of("walk","roll"))for(String dir:List.of("down","up","right","left"))names.add(act+"-"+dir);
   names.addAll(List.of("attack","skill","mining","cheer-baker"));
   names.removeIf(n->!n.matches(filter));setup();
  }
  void setup() {
   if(renderer!=null)renderer.dispose();MathUtils.random.setSeed(99);frame=0;
   String n=names.get(scene);still=n.startsWith("free-")||n.startsWith("trapped-");
   boolean village=still||n.startsWith("cheer-");
   var s=NewGame.create(balance);s.mapVersion=WorldMap.VERSION;s.finalBossDefeated=n.startsWith("free-")||n.startsWith("cheer-");
   var map=newMap();var room=map.room(village?"V":"CAMP");
   s.resumeX=room.spawnX(balance.tileSize);s.resumeY=room.spawnY(balance.tileSize);
   session=new GameSession(balance,map,s,42);session.monsters.clear();session.devGodMode=true;
   dx=n.endsWith("left")?-1:n.endsWith("right")?1:0;dy=n.endsWith("up")?1:n.endsWith("down")?-1:0;
   cam.zoom=.45f;
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);
   if(village){
    String kind=n.substring(n.indexOf('-')+1);var npc=balance.village.villagers.stream().filter(v->v.kind.equals(kind)).findFirst().orElseThrow();
    float x=session.villagerX(npc),y=session.villagerY(npc);
    session.hero.x=x+68;session.hero.y=y;
    renderer.update(4.8f-(x*.031f*7%4.8f)+.1f+(n.startsWith("cheer-")?3.5f:0));
    cam.position.set(x+34,y+26,0);
   } else {
    float best=Float.MAX_VALUE,px=0,py=0;
    for(float y=room.originY;y<room.originY+room.heightPx;y+=8)for(float x=room.originX;x<room.originX+room.widthPx;x+=8){
     float cost=(x-room.spawnX(balance.tileSize))*(x-room.spawnX(balance.tileSize))+(y-room.spawnY(balance.tileSize))*(y-room.spawnY(balance.tileSize));
     if(cost>=best)continue;boolean ok=true;
     for(int k=0;k<=128;k+=4)if(!map.free(x+dx*k,y+dy*k,session.hero.hw,session.hero.hh)){ok=false;break;}
     if(ok){best=cost;px=x;py=y;}
    }
    if(best==Float.MAX_VALUE)throw new AssertionError("No clear route "+n);
    session.hero.x=px;session.hero.y=py;session.hero.facing=dx==0&&dy==0?Facing.DOWN:Facing.of(dx,dy);
    session.hero.aimX=dx;session.hero.aimY=dx==0&&dy==0?-1:dy;
    cam.position.set(px+dx*52,py+dy*52+22,0);renderer.update(0);
   }
   cam.update();
  }
  public void render(){
   String n=names.get(scene);
   if(!still){
    Controls input=new Controls();
    if(n.startsWith("walk")&&frame>=12&&frame<66){input.moveX=dx;input.moveY=dy;}
    if(n.startsWith("roll")&&frame==12){input.moveX=dx;input.moveY=dy;input.roll=true;}
    if(n.equals("attack")&&(frame==12||frame==40))input.attack=true;
    session.update(1/60f,input);
    if(frame==12||frame==40){
     if(n.equals("mining"))session.hero.mineSwingT=.22f;
     if(n.equals("skill")){session.hero.swingT=.25f;session.hero.swingKind=1;}
    }
    for(var e:session.pollEvents())renderer.onEvent(e);
    renderer.update(1/60f);
   }
   if(still||frame%2==0){
    renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
    var p=ScreenUtils.getFrameBufferPixmap(0,0,640,360);var writer=new PixmapIO.PNG();writer.setFlipY(true);
    String file=still?n+".png":n+"/"+String.format("%03d",frame)+".png";
    out.child(file).parent().mkdirs();
    try{writer.write(out.child(file),p);}catch(Exception e){throw new RuntimeException(e);}finally{writer.dispose();p.dispose();}
   }
   if(still||++frame>=96){
    System.out.println("MOTION_CAPTURE_OK "+n);
    if(++scene==names.size())Gdx.app.exit();else setup();
   }
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

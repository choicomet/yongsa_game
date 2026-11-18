import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.enemy.Monster;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.ui.Hud;
import com.yongsa.map.*;
import java.util.*;

/** Actual renderer and atlas QA. Hidden window, isolated saves, no player data. */
public final class StorySpriteCapture {
 public static void main(String[] args) {
  System.setProperty("yongsa.saveDir", new java.io.File("build/story-qa-save").getAbsolutePath());
  var c=new Lwjgl3ApplicationConfiguration();c.setWindowedMode(1280,720);c.setInitialVisible(false);c.disableAudio(true);c.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),c);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;int shot;
  final List<String> names=new ArrayList<>(List.of("boss-phase1","boss-phase2-left","boss-phase2-right","boss-special","boss-death-start","boss-death-end","hero-sleep","village-trapped","village-free"));
  Capture(String dir){out=new FileHandle(dir);out.mkdirs();}
  public void create(){
   super.create();MathUtils.random.setSeed(99);cam=new OrthographicCamera(1280,720);
   for(var v:balance.village.villagers){names.add("trapped-"+v.kind);names.add("free-"+v.kind);}
   names.add("boss-atlas");
   try {
    var method=WorldRenderer.class.getDeclaredMethod("bossSheet",String.class,boolean.class);method.setAccessible(true);
    if(!"team/jiyong/boss_lich.png".equals(method.invoke(null,"demonlord",false)))throw new AssertionError("Phase 1 sheet");
    if(!"team/jiyong/boss_lich2.png".equals(method.invoke(null,"demonlord",true)))throw new AssertionError("Phase 2 sheet");
    if(!"team/jiyong/boss_golem.png".equals(method.invoke(null,"golem",true)))throw new AssertionError("Other bosses changed");
   }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
  }
  void setup(String name){
   if(renderer!=null){renderer.dispose();renderer=null;}
   var s=NewGame.create(balance);s.mapVersion=WorldMap.VERSION;s.exitRefusals=6;s.unsealed=true;
   s.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));s.equippedUid=s.newWeapon("holy_awakened").uid;
   s.finalBossDefeated=name.contains("free");var map=newMap();
   var room=map.room(name.startsWith("boss-")?"F":name.equals("hero-sleep")?"CAMP":"V");
   s.resumeX=room.spawnX(balance.tileSize);s.resumeY=room.spawnY(balance.tileSize);
   session=new GameSession(balance,map,s,42);session.monsters.clear();session.devGodMode=true;session.state.hp=session.rules.maxHp(s);
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam.zoom=2f/3f*100f/115f;
   float cx,cy;
   if(name.startsWith("boss-")){
    var m=new Monster(balance.finalBoss,true,true,balance.tileSize);m.x=room.sceneX(.50f);m.y=room.sceneY(.47f);
    m.phase2=!name.equals("boss-phase1");m.dirX=name.endsWith("right")?1:-1;m.enter(Monster.State.CHASE,5f);
    session.monsters.add(m);session.hero.x=m.x-100;session.hero.y=m.y-70;
    if(name.equals("boss-special")){m.enter(Monster.State.CHANNEL,5f);m.channelKind="eyes";}
    if(name.startsWith("boss-death")){
     m.dead=true;var e=GameEvent.fx(GameEvent.Type.MONSTER_DIED,m.x,m.y);e.sprite=m.def.sprite;e.boss=true;e.phase2=true;e.size=2.3f;e.dirX=m.dirX;e.flip=true;
     renderer.onEvent(e);
     try {
      var field=WorldRenderer.class.getDeclaredField("corpses");field.setAccessible(true);var corpse=((List<?>)field.get(renderer)).get(0);
      var sheet=corpse.getClass().getDeclaredField("sheet");sheet.setAccessible(true);
      var scale=corpse.getClass().getDeclaredField("sheetScale");scale.setAccessible(true);
      if(!"team/jiyong/boss_lich2.png".equals(sheet.get(corpse)))throw new AssertionError("Death reverted to old form");
      if(Math.abs(scale.getFloat(corpse)-.75f*1.35f)>.0001f)throw new AssertionError("Death shrank");
     }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
     renderer.update(name.endsWith("start")?.18f:1.15f);
    }else renderer.update(.25f);
    cx=m.x;cy=m.y+55;
   }else if(name.equals("hero-sleep")){
    var bed=map.props.stream().filter(p->p.kind==Prop.Kind.BED).findFirst().orElseThrow();session.startSleep(bed);
    cx=Float.isNaN(bed.sleepX)?bed.centerX(balance.tileSize):bed.sleepX;
    cy=Float.isNaN(bed.sleepY)?bed.centerY(balance.tileSize):bed.sleepY;
    session.hero.x=cx;session.hero.y=cy;renderer.update(.25f);
   }else{
    if(name.startsWith("village-")){cx=room.originX+room.widthPx/2;cy=room.originY+room.heightPx/2;cam.zoom=1.1f;}
    else {String kind=name.substring(name.indexOf('-')+1);var v=balance.village.villagers.stream().filter(x->x.kind.equals(kind)).findFirst().orElseThrow();
     cx=session.villagerX(v);cy=session.villagerY(v)+15;cam.zoom=.35f;
    }
    session.hero.x=cx+60;session.hero.y=cy-10;renderer.update(name.startsWith("free-")?1.1f:.25f);
   }
   cam.position.set(cx,cy,0);cam.update();
  }
  void atlas(){
   Gdx.gl.glClearColor(.18f,.19f,.23f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
   batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,1280,720));var font=new BitmapFont();batch.begin();
   font.draw(batch,"DEMON LORD TRUE FORM / 40 FRAMES / BASELINE +",20,700);
   String[] labels={"IDLE / HIT","WALK","ATTACK","EYES","DEATH"};
   for(int row=0;row<5;row++){font.draw(batch,labels[row],12,630-row*128);
    for(int col=0;col<8;col++){
     var r=art.cell("team/jiyong/boss_lich2.png",8,5,row*8+col);
     if(r.getRegionWidth()!=256||r.getRegionHeight()!=256)throw new AssertionError("Wrong atlas size");
     float x=190+col*140,y=570-row*128;batch.setColor(Color.WHITE);batch.draw(r,x-96,y-24,192,192);font.draw(batch,"+",x-3,y+3);
    }
   }batch.end();font.dispose();
  }
  public void render(){
   String name=names.get(shot);
   if(name.equals("boss-atlas"))atlas();
   else {setup(name);renderer.renderLights(cam);Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);renderer.render(cam);
    if(name.startsWith("boss-")){var hud=new Hud(session,art,fonts,batch,shapes);hud.update(.016f);hud.render(new Matrix4().setToOrtho2D(0,0,1280,720),1280,720);}
   }
   var p=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var writer=new PixmapIO.PNG();writer.setFlipY(true);
   try{writer.write(out.child(name+".png"),p);}catch(Exception e){throw new RuntimeException(e);}finally{writer.dispose();p.dispose();}
   System.out.println("STORY_CAPTURE_OK "+name);if(++shot==names.size())Gdx.app.exit();
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

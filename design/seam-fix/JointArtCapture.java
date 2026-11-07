import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.google.gson.GsonBuilder;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.map.*;
import java.lang.reflect.*;
import java.util.*;

/** Export unlit terrain at exact world coordinates as ImageGen edit targets. */
public final class JointArtCapture {
 public static void main(String[] args) {
  System.setProperty("yongsa.saveDir", new java.io.File("build/joint-art-qa-save").getAbsolutePath());
  var config=new Lwjgl3ApplicationConfiguration();config.setWindowedMode(1024,1024);
  config.setInitialVisible(false);config.disableAudio(true);config.useVsync(false);
  new Lwjgl3Application(new Capture(args[0]),config);
 }
 static class Capture extends YongsaGame {
  FileHandle out;WorldRenderer renderer;GameSession session;OrthographicCamera cam;
  List<Map<String,Object>> entries=new ArrayList<>();int shot;
  Capture(String path){out=new FileHandle(path);out.mkdirs();}
  void add(String id,Door door,float dx,float dy) {
   entries.add(Map.of("id",id,"image","scenes/joints-v1/"+id+".png",
     "rect",new float[]{door.footX+dx-256,door.footY+dy-256,512,512},
     "door",new float[]{door.footX,door.footY},"room",door.room.id,"feather",32));
  }
  public void create() {
   super.create();var state=NewGame.create(balance);var map=newMap();
   session=new GameSession(balance,map,state,42);session.monsters.clear();
   renderer=new WorldRenderer(session,art,fonts,batch,shapes);cam=new OrthographicCamera(512,512);
   for(var link:map.links){
    if(link.a.id.equals("L3")&&link.b.id.equals("L5"))add("mine-north",link.doorB,0,16);
    if(link.a.id.equals("L1")&&link.b.id.equals("L2"))add("mine-side",link.doorA,0,0);
   }
   if(entries.size()!=2)throw new IllegalStateException("Missing reported joins");
   out.child("joints.json").writeString(new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("joints",entries)),false,"UTF-8");
  }
  Object call(Object instance,String name,Class<?>[] types,Object... args)throws Exception {
   Method m=instance.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(instance,args);
  }
  public void render() {
   try {
    var entry=entries.get(shot);float[] r=(float[])entry.get("rect");
    cam.position.set(r[0]+r[2]/2,r[1]+r[3]/2,0);cam.update();
    call(renderer,"computeView",new Class<?>[]{OrthographicCamera.class},cam);
    Field f=WorldRenderer.class.getDeclaredField("passages");f.setAccessible(true);Object passages=f.get(renderer);
    Gdx.gl.glViewport(0,0,1024,1024);
    batch.setProjectionMatrix(cam.combined);Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    batch.begin();batch.setColor(Color.WHITE);
    var sig=new Class<?>[]{com.badlogic.gdx.graphics.g2d.SpriteBatch.class,float.class,float.class,float.class,float.class};
    call(passages,"drawGround",sig,batch,r[0],r[1],r[0]+r[2],r[1]+r[3]);
    call(passages,"drawWalls",sig,batch,r[0],r[1],r[0]+r[2],r[1]+r[3]);
    call(renderer,"drawScenes",new Class<?>[]{});
    if(Boolean.getBoolean("yongsa.jointFootprint")) {
     Field jf=passages.getClass().getDeclaredField("joints");jf.setAccessible(true);
     call(jf.get(passages),"draw",sig,batch,r[0],r[1],r[0]+r[2],r[1]+r[3]);
    }
    batch.end();
    if(Boolean.getBoolean("yongsa.jointFootprint")) {
     shapes.setProjectionMatrix(cam.combined);
     shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
     for(float y=r[1]+8;y<r[1]+r[3];y+=8)for(float x=r[0]+8;x<r[0]+r[2];x+=8)
      if(session.map.free(x,y,9,6)){shapes.setColor(0,1,0,.8f);shapes.circle(x,y,1.2f,6);}
     shapes.end();
    }
    var p=ScreenUtils.getFrameBufferPixmap(0,0,1024,1024);var writer=new PixmapIO.PNG();writer.setFlipY(true);
    try {writer.write(out.child(entry.get("id")+".png"),p);}finally{writer.dispose();p.dispose();}
    System.out.println("JOINT_ART_CAPTURE_OK "+entry.get("id"));if(++shot==entries.size())Gdx.app.exit();
   }catch(Exception e){throw new RuntimeException(e);}
  }
  public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
 }
}

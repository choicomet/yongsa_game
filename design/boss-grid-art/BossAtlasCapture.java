import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.gfx.Art;

/** External GPU preview using the game's Art.cell and boss draw scale/anchor. */
public class BossAtlasCapture extends ApplicationAdapter {
 private Art art; private SpriteBatch batch; private BitmapFont font; private int sheet;
 private final String output;
 private final String[] names={"golem","knight","lich"};
 private final String[] rows={"IDLE / HIT","WALK","ATTACK","SPECIAL","DEATH"};
 public BossAtlasCapture(String out){output=out;}
 public static void main(String[] a){
  var config=new Lwjgl3ApplicationConfiguration();
  config.setWindowedMode(1280,720);config.setInitialVisible(false);config.disableAudio(true);config.useVsync(false);
  new Lwjgl3Application(new BossAtlasCapture(a[0]),config);
 }
 public void create(){art=new Art();batch=new SpriteBatch();font=new BitmapFont();}
 public void render(){
  Gdx.gl.glClearColor(.18f,.19f,.23f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
  batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,1280,720));
  batch.begin();font.draw(batch,"BOSS "+names[sheet]+" | 256px cells | 0.75 game scale | 40 frames",30,700);
  for(int row=0;row<5;row++){
   font.draw(batch,rows[row],16,640-row*128);
   for(int col=0;col<8;col++){
    var region=art.cell("team/jiyong/boss_"+names[sheet]+".png",8,5,row*8+col);
    if(region.getRegionWidth()!=256||region.getRegionHeight()!=256)throw new AssertionError("Bad cell");
    float footX=190+col*140,footY=570-row*128;
    batch.draw(region,footX-96,footY-24,192,192);
    font.setColor(.5f,.6f,.7f,1);font.draw(batch,"+",footX-3,footY+3);font.setColor(Color.WHITE);
   }
  }
  batch.end();
  var pix=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);var writer=new PixmapIO.PNG();writer.setFlipY(true);
  try{writer.write(new FileHandle(output).child(names[sheet]+".png"),pix);}catch(Exception e){throw new RuntimeException(e);}
  finally{writer.dispose();pix.dispose();}
  System.out.println("BOSS_GPU_CAPTURE_OK "+names[sheet]);if(++sheet==3)Gdx.app.exit();
 }
 public void dispose(){art.dispose();batch.dispose();font.dispose();}
}

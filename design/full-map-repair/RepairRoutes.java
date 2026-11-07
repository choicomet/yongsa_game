import com.google.gson.*;
import com.yongsa.map.*;
import java.nio.file.*;
import java.util.*;

/** Derives navigation waypoints from the authored new floor, without changing collision or images. */
public final class RepairRoutes {
 static WorldMap map;
 static final int STEP=4;
 static boolean segment(float ax,float ay,float bx,float by) {
  int steps=Math.max(1,(int)Math.ceil(Math.hypot(bx-ax,by-ay)/2));
  for(int i=0;i<=steps;i++)if(!map.free(ax+(bx-ax)*i/steps,ay+(by-ay)*i/steps,12,9))return false;
  return true;
 }
 static boolean belongs(Corridor c,float x,float y) {
  if(map.corridorAtWorld(x,y)==c)return true;
  return Math.hypot(x-c.link.doorA.footX,y-c.link.doorA.footY)<96
      || Math.hypot(x-c.link.doorB.footX,y-c.link.doorB.footY)<96;
 }
 static List<float[]> route(Corridor c) {
  float sx=c.link.doorA.footX,sy=c.link.doorA.footY,ex=c.link.doorB.footX,ey=c.link.doorB.footY;
  if(!map.free(sx,sy,9,6)||!map.free(ex,ey,9,6))throw new IllegalStateException("Blocked endpoint "+c.key());
  float x0=Math.min(sx,ex)-900,y0=Math.min(sy,ey)-900,x1=Math.max(sx,ex)+900,y1=Math.max(sy,ey)+900;
  int w=(int)((x1-x0)/STEP)+1,h=(int)((y1-y0)/STEP)+1;
  // Anchor the grid at the exact doorway so the initial point is not rounded into a wall.
  int startX=(int)((sx-x0)/STEP),startY=(int)((sy-y0)/STEP);
  x0=sx-startX*STEP;y0=sy-startY*STEP;
  int start=startY*w+startX;
  float[] cost=new float[w*h];Arrays.fill(cost,Float.POSITIVE_INFINITY);cost[start]=0;
  int[] prev=new int[w*h];Arrays.fill(prev,-1);
  byte[] valid=new byte[w*h];
  record Node(int id,float score){}
  var queue=new PriorityQueue<Node>(Comparator.comparingDouble(Node::score));queue.add(new Node(start,0));
  int goal=-1;
  while(!queue.isEmpty()) {
   int id=queue.remove().id(),ix=id%w,iy=id/w;float x=x0+ix*STEP,y=y0+iy*STEP;
   if(Math.hypot(x-ex,y-ey)<STEP*2 && segment(x,y,ex,ey)){goal=id;break;}
   for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
    if(dx==0&&dy==0)continue;
    int nx=ix+dx,ny=iy+dy;if(nx<0||ny<0||nx>=w||ny>=h)continue;
    int next=ny*w+nx;float px=x0+nx*STEP,py=y0+ny*STEP;
    if(valid[next]==0)valid[next]=(byte)(belongs(c,px,py)&&map.free(px,py,12,9)?1:-1);
    if(valid[next]<0||!segment(x,y,px,py))continue;
    float nc=cost[id]+(float)Math.hypot(dx,dy)*STEP;
    if(nc>=cost[next])continue;
    cost[next]=nc;prev[next]=id;
    queue.add(new Node(next,nc+(float)Math.hypot(px-ex,py-ey)));
   }
  }
  if(goal<0) {
   for(var j:map.paintedJoints)if(j.room.equals(c.link.a.id)&&Math.hypot(j.door[0]-sx,j.door[1]-sy)<1)try {
    var im=javax.imageio.ImageIO.read(Path.of("assets",j.image).toFile());var g=im.createGraphics();
    for(int n=0;n<cost.length;n++)if(Float.isFinite(cost[n])) {
     float px=x0+(n%w)*STEP,py=y0+(n/w)*STEP;
     if(!j.visible(px,py,px+1,py+1))continue;
     int u=(int)((px-j.rect[0])/j.rect[2]*im.getWidth()),v=(int)((1-(py-j.rect[1])/j.rect[3])*im.getHeight());
     g.setColor(new java.awt.Color(20,255,255,150));g.fillRect(u,v,3,3);
    }
    g.dispose();javax.imageio.ImageIO.write(im,"png",Path.of("../../outputs/full-map-repair/"+j.id+"-reachable.png").toFile());
   }catch(Exception ignored){}
   throw new IllegalStateException("Disconnected floor "+c.key());
  }
  var raw=new ArrayList<float[]>();raw.add(new float[]{ex,ey});
  for(int n=goal;n>=0;n=prev[n]){raw.add(new float[]{x0+(n%w)*STEP,y0+(n/w)*STEP});if(n==start)break;}
  Collections.reverse(raw);
  var simplified=new ArrayList<float[]>();int a=0;simplified.add(raw.get(0));
  while(a<raw.size()-1) {
   int b=raw.size()-1;
   while(b>a+1&&(Math.hypot(raw.get(b)[0]-raw.get(a)[0],raw.get(b)[1]-raw.get(a)[1])>300
           ||!segment(raw.get(a)[0],raw.get(a)[1],raw.get(b)[0],raw.get(b)[1])))b--;
   simplified.add(raw.get(b));a=b;
  }
  for(var p:simplified){p[0]/=map.tileSize;p[1]/=map.tileSize;}
  return simplified;
 }
 public static void main(String[] args)throws Exception {
  Path file=Path.of("assets/data/map.json");
  String source=Files.readString(file);
  map=new WorldMap(MapDef.parse(source),RoomSceneLayout.parse(Files.readString(Path.of("assets/data/natural-room-scenes.json"))),32);
  for(var l:map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
  var json=JsonParser.parseString(source).getAsJsonObject();var gson=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  boolean failed=false;
  for(var c:map.corridors)try {
   var route=route(c);
   var link=json.getAsJsonArray("links").get(map.links.indexOf(c.link)).getAsJsonObject();
   link.getAsJsonObject("painted").add("route",gson.toJsonTree(route));
   System.out.println("ROUTE "+c.key()+" "+route.size());
  }catch(Exception e){failed=true;System.out.println("FAILED "+e.getMessage());}
  if(failed)throw new IllegalStateException("Unconnected terrain; map file left unchanged");
  Files.writeString(file,gson.toJson(json)+"\n");
 }
}

import com.google.gson.GsonBuilder;
import com.yongsa.map.*;
import java.nio.file.*;
import java.util.*;

/** Samples the actual collision result over every painted join, not just the route center. */
public final class FloorAudit {
 public static void main(String[] args)throws Exception {
  var map=new WorldMap(MapDef.parse(Files.readString(Path.of("assets/data/map.json"))),
      RoomSceneLayout.parse(Files.readString(Path.of("assets/data/natural-room-scenes.json"))),32);
  for(var l:map.links){l.gateClosed=false;l.doorA.lockedByFight=false;l.doorB.lockedByFight=false;}
  var report=new ArrayList<Object>();
  for(var j:map.paintedJoints) {
   var samples=new ArrayList<Object>();int rejected=0;
   for(float v=.01f;v<1;v+=.02f)for(float u=.01f;u<1;u+=.02f) {
    float x=j.x(u),y=j.y(v);boolean free=map.free(x,y,9,6),floor=j.allowsFoot(x,y,9);
    String owner="original";
    for(var other:map.paintedJoints)if(other.owns(x,y))owner=other.id;
    if(floor&&!free)rejected++;
    samples.add(List.of(u,v,free,floor,owner));
   }
   report.add(Map.of("id",j.id,"image",j.image,"rect",j.rect,"samples",samples));
   System.out.println(j.id+" rejected floor probes: "+rejected);
  }
  for(var c:map.corridors) {
   var samples=new ArrayList<Object>();
   for(float v=.01f;v<1;v+=.02f)for(float u=.01f;u<1;u+=.02f) {
    float x=c.sceneX(u),y=c.sceneY(v);String owner="original";
    for(var j:map.paintedJoints)if(j.owns(x,y))owner=j.id;
    if(!owner.equals("original"))continue;
    samples.add(List.of(u,v,map.free(x,y,9,6),c.allowsFoot(x,y,9),owner));
   }
   report.add(Map.of("id","passage-"+c.link.a.id+"-"+c.link.b.id,"image",c.scene.image,
       "rect",new float[]{c.originX,c.originY,c.widthPx,c.heightPx},"samples",samples));
  }
  Files.writeString(Path.of(args[0]),new GsonBuilder().create().toJson(report));
 }
}

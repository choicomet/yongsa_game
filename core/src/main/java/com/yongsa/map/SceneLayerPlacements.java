package com.yongsa.map;

import java.util.*;

/** Shared coordinates and paint ownership for drawing and footing collision. No GL dependency. */
public final class SceneLayerPlacements {
    public static final class Placement {
        public final String key;
        public final SceneLayers.Scene scene;
        public final float x,y,w,h;
        public final boolean flip;
        public final Room room;
        public final Corridor corridor;
        public final PaintedJointLayout.Joint joint;
        final RoomJoinMask join;
        final List<PaintedJointLayout.Joint> overlaps = new ArrayList<>();
        final Map<Room, RoomJoinMask> coveringRooms = new LinkedHashMap<>();
        final List<float[]> footingBounds=new ArrayList<>();
        Placement(String key,SceneLayers.Scene scene,float x,float y,float w,float h,boolean flip,Room room,Corridor corridor,PaintedJointLayout.Joint joint,int tile) {
            this.key=key;this.scene=scene;this.x=x;this.y=y;this.w=w;this.h=h;this.flip=flip;
            this.room=room;this.corridor=corridor;this.joint=joint;join=room==null?null:new RoomJoinMask(room,tile);
            for(var object:scene.objects)if(object.block!=null)footingBounds.add(blockRect(object));
        }
        public float worldX(float u){return x+(flip?1-u:u)*w;}
        public float worldY(float v){return y+(1-v)*h;}
        public float sourceU(float wx){return flip?1-(wx-x)/w:(wx-x)/w;}
        public boolean contains(float wx,float wy){return wx>=x&&wx<=x+w&&wy>=y&&wy<=y+h;}
        public boolean visible(float x0,float y0,float x1,float y1){return x<x1&&x+w>x0&&y<y1&&y+h>y0;}
        public float[] blockRect(SceneLayers.ObjectLayer o) {
            var b=o.block;float x0=worldX(b[0]),x1=worldX(b[2]);
            return new float[]{Math.min(x0,x1),worldY(b[3]),Math.max(x0,x1),worldY(b[1])};
        }
    }
    public final SceneLayers data;
    public final List<Placement> all=new ArrayList<>();


    public SceneLayerPlacements(WorldMap map,SceneLayers data) {
        this.data=data;
        Map<Room, RoomJoinMask> joins = new LinkedHashMap<>();
        for(var r:map.rooms)if(r.scene!=null) {
            joins.put(r,new RoomJoinMask(r,map.tileSize));String key="rooms/"+r.scene.id;
            if(data.scenes.containsKey(key))all.add(new Placement(key,data.scenes.get(key),r.originX,r.originY,r.widthPx,r.heightPx,r.flip,r,null,null,map.tileSize));
        }
        for(var c:map.corridors)if(c.scene!=null) {
            String key="passages/"+c.scene.id;
            if(data.scenes.containsKey(key))all.add(new Placement(key,data.scenes.get(key),c.originX,c.originY,c.widthPx,c.heightPx,c.flip,null,c,null,map.tileSize));
        }
        for(var j:map.paintedJoints) {
            String key="joints/"+j.id;
            if(data.scenes.containsKey(key))all.add(new Placement(key,data.scenes.get(key),j.rect[0],j.rect[1],j.rect[2],j.rect[3],false,null,null,j,map.tileSize));
        }
        // Only these paintings can cover a pixel of the placement.
        for (Placement placement : all) {
            int first = placement.joint == null ? 0 : map.paintedJoints.indexOf(placement.joint) + 1;
            for (int i = first; i < map.paintedJoints.size(); i++) {
                var joint = map.paintedJoints.get(i);
                if (joint.visible(placement.x, placement.y, placement.x + placement.w, placement.y + placement.h))
                    placement.overlaps.add(joint);
            }
            if (placement.corridor != null) for (var entry : joins.entrySet()) {
                var room = entry.getKey();
                if (placement.visible(room.originX, room.originY, room.originX + room.widthPx, room.originY + room.heightPx))
                    placement.coveringRooms.put(room, entry.getValue());
            }
        }
    }
    public boolean has(String key){return data.scenes.containsKey(key);}
    public boolean owns(Placement p,float wx,float wy,boolean collision) {
        if(!p.contains(wx,wy))return false;
        float threshold=collision?.5f:1f;
        if(p.joint!=null&&p.joint.alpha(wx,wy)<threshold)return false;
        for (var joint : p.overlaps) if (joint.alpha(wx,wy) > (collision ? .5f : 0)) return false;
        if(p.room!=null&&p.join.alpha(wx,wy)<threshold)return false;
        if(p.corridor!=null)for(var e:p.coveringRooms.entrySet()) {
            var r=e.getKey();
            if(wx>=r.originX&&wx<=r.originX+r.widthPx&&wy>=r.originY&&wy<=r.originY+r.heightPx&&e.getValue().alpha(wx,wy)>(collision?.5f:0))return false;
        }
        return true;
    }
    public boolean blocks(float x,float y,float hw,float hh) {
        for(var p:all) {
            if(!p.visible(x-hw,y-hh,x+hw+.001f,y+hh+.001f))continue;
            for(var b:p.footingBounds) {
                if(x+hw<=b[0]||x-hw>=b[2]||y+hh<=b[1]||y-hh>=b[3])continue;
                float px=Math.max(b[0],Math.min(b[2],x)),py=Math.max(b[1],Math.min(b[3],y));
                if(owns(p,px,py,true))return true;
            }
        }
        return false;
    }
}

package com.yongsa.map;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;

/** Full-canvas RGBA artwork; coordinates and footings refer to the unchanged source image. */
public final class SceneLayers {
    public int version=1;
    public Map<String,Scene> scenes=new LinkedHashMap<>();
    public static final class Scene {
        public String front;
        public List<ObjectLayer> objects=new ArrayList<>();
    }
    public static final class ObjectLayer {
        public String id,image;
        public float foot;
        public float[][] footLine;
        public float[] block;
        public float footAt(float u) {
            if(footLine==null)return foot;
            if(u<=footLine[0][0])return footLine[0][1];
            for(int i=1;i<footLine.length;i++)if(u<=footLine[i][0]) {
                float t=(u-footLine[i-1][0])/(footLine[i][0]-footLine[i-1][0]);
                return footLine[i-1][1]+t*(footLine[i][1]-footLine[i-1][1]);
            }
            return footLine[footLine.length-1][1];
        }
    }
    public static SceneLayers load() {
        try {
            Path p=Path.of("assets/data/scene-layers.json");
            if(Files.exists(p))return parse(Files.readString(p));
            try(var in=SceneLayers.class.getResourceAsStream("/data/scene-layers.json")) {
                return in==null?new SceneLayers():parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            }
        }catch(java.io.IOException e){throw new IllegalArgumentException("Cannot read scene layers",e);}
    }
    public static SceneLayers parse(String json) {
        SceneLayers data=new Gson().fromJson(json,SceneLayers.class);
        if(data==null||data.version!=1||data.scenes==null)throw new IllegalArgumentException("Invalid scene layers");
        for(var e:data.scenes.entrySet()) {
            Scene s=e.getValue();
            if(!e.getKey().matches("(rooms|passages|joints)/[a-zA-Z0-9-]+")||s==null||s.objects==null)
                throw new IllegalArgumentException("Invalid layer scene: "+e.getKey());
            if(s.front!=null)path(s.front);
            Set<String> ids=new HashSet<>();
            for(var o:s.objects) {
                if(o==null||o.id==null||!o.id.matches("[a-zA-Z0-9-]+")||!ids.add(o.id)||!unit(o.foot))
                    throw new IllegalArgumentException("Invalid object: "+e.getKey());
                path(o.image);
                if(o.block!=null&&(o.block.length!=4||!unit(o.block[0])||!unit(o.block[1])||!unit(o.block[2])||!unit(o.block[3])
                        ||o.block[0]>=o.block[2]||o.block[1]>=o.block[3]))throw new IllegalArgumentException("Invalid footing block: "+o.id);
                if(o.footLine!=null) {
                    if(o.footLine.length<2)throw new IllegalArgumentException("Missing footing line: "+o.id);
                    for(int i=0;i<o.footLine.length;i++) {
                        var q=o.footLine[i];
                        if(q==null||q.length!=2||!unit(q[0])||!unit(q[1])||(i>0&&q[0]<=o.footLine[i-1][0]))
                            throw new IllegalArgumentException("Invalid footing line: "+o.id);
                    }
                }
            }
        }
        return data;
    }
    private static boolean unit(float n){return Float.isFinite(n)&&n>=0&&n<=1;}
    private static void path(String p){if(p==null||p.contains("..")||!(p.startsWith("layers/")||p.startsWith("scenes/natural-v1/"))||!p.endsWith(".png"))throw new IllegalArgumentException("Invalid layer image: "+p);}
}

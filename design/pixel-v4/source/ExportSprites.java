import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Exports the game's text-native pixel grammar; no input image is processed. */
public class ExportSprites {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args.length > 0 ? args[0] : "native/sprites.txt");
        Path out = source.getParent();
        List<String> lines = Files.readAllLines(source);
        Map<Character,Integer> palette = new LinkedHashMap<>();
        List<String> audit = new ArrayList<>();
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n);
            if (line.startsWith("palette ")) {
                String[] p = line.split(" ");
                long rgba = Long.parseLong(p[2],16);
                int argb = (int)(((rgba & 255) << 24) | (rgba >>> 8));
                if ((argb >>> 24) != 0 && (argb >>> 24) != 255) throw new IllegalArgumentException("Partial alpha");
                palette.put(p[1].charAt(0),argb);
            } else if (line.startsWith("sprite ")) {
                String[] p = line.split(" ");
                String name = p[1]; int width = Integer.parseInt(p[2]), height = Integer.parseInt(p[3]);
                BufferedImage image = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                Set<Integer> colors = new HashSet<>();
                int left=width,top=height,right=-1,bottom=-1;
                for (int y=0;y<height;y++) {
                    String row = lines.get(++n);
                    if(row.length()!=width) throw new IllegalArgumentException(name+": incorrect width");
                    for (int x=0;x<width;x++) {
                        Integer color = palette.get(row.charAt(x));
                        if(color==null) throw new IllegalArgumentException("Missing palette entry");
                        image.setRGB(x,y,color);
                        if((color>>>24)!=0) { colors.add(color); left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y); }
                    }
                }
                if(left<=0||top<=0||right>=width-1||bottom>=height-1) throw new IllegalArgumentException(name+": missing margin");
                if(colors.size()>24) throw new IllegalArgumentException(name+": palette exceeds study budget");
                for(int scale : new int[]{1,2,4,8}) {
                    BufferedImage enlarged = new BufferedImage(width*scale,height*scale,BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<height*scale;y++) for(int x=0;x<width*scale;x++) enlarged.setRGB(x,y,image.getRGB(x/scale,y/scale));
                    Path dest=out.resolve(name+(scale==1?"":"-"+scale+"x")+".png");
                    ImageIO.write(enlarged,"png",dest.toFile());
                    BufferedImage readback=ImageIO.read(dest.toFile());
                    for(int y=0;y<height*scale;y++) for(int x=0;x<width*scale;x++)
                        if(readback.getRGB(x,y)!=image.getRGB(x/scale,y/scale)) throw new IllegalStateException("PNG round-trip mismatch");
                }
                audit.add(String.format(Locale.ROOT,"{\"name\":\"%s\",\"width\":%d,\"height\":%d,\"visibleColors\":%d,\"partialAlphaPixels\":0,\"visibleBounds\":[%d,%d,%d,%d],\"integerScalesVerified\":[1,2,4,8]}",name,width,height,colors.size(),left,top,right-left+1,bottom-top+1));
            }
        }
        if(audit.size()!=2) throw new IllegalStateException("Expected two sprites");
        String report="[\n  "+String.join(",\n  ",audit)+"\n]\n";
        Files.writeString(out.resolve("VALIDATION.json"),report);
        System.out.print(report);
    }
}

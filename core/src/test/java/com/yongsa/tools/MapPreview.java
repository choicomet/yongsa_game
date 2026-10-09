package com.yongsa.tools;

import com.yongsa.config.Balance;
import com.yongsa.world.DoorDef;
import com.yongsa.world.DungeonGenerator;
import com.yongsa.world.DungeonMap;
import com.yongsa.world.MapObject;
import com.yongsa.world.RoomDef;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** 개발용: 생성된 던전 전체를 PNG 로 그린다. 사용: MapPreview <씨앗값> <출력.png> */
public class MapPreview {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        Balance b = Balance.loadFromPath("data/balance.json");
        DungeonMap m = DungeonGenerator.generate(seed, b);
        int s = 4;
        BufferedImage img = new BufferedImage(m.widthTiles * s, m.heightTiles * s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Color[] floors = {new Color(150, 120, 90), new Color(120, 100, 80), new Color(90, 105, 130), new Color(110, 90, 140)};
        for (int ty = 0; ty < m.heightTiles; ty++) {
            for (int tx = 0; tx < m.widthTiles; tx++) {
                int py = (m.heightTiles - 1 - ty) * s;
                byte c = m.cell(tx, ty);
                if (c == DungeonMap.FLOOR) g.setColor(floors[m.theme(tx, ty)]);
                else if (c == DungeonMap.WALL) g.setColor(new Color(40, 36, 44));
                else continue;
                g.fillRect(tx * s, py, s, s);
            }
        }
        float k = s / 32f;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        for (RoomDef r : m.rooms) {
            int x = (int) (r.bounds.x * k), y = (int) ((m.heightPx - r.bounds.y - r.bounds.height) * k);
            Color c = r.kind == RoomDef.Kind.BOSS ? Color.RED : r.kind == RoomDef.Kind.ANTE ? Color.MAGENTA
                    : r.kind == RoomDef.Kind.CAMP ? Color.WHITE : r.enemies && r.veins ? Color.ORANGE
                    : r.enemies ? new Color(255, 80, 80) : Color.CYAN;
            g.setColor(c);
            g.drawRect(x, y, (int) (r.bounds.width * k), (int) (r.bounds.height * k));
            g.drawString(r.label() + (r.mainPath ? "" : "*"), x + 3, y + 13);
        }
        g.setColor(Color.YELLOW);
        for (MapObject v : m.veins) g.fillRect((int) (v.x() * k) - 1, (int) ((m.heightPx - v.y()) * k) - 1, 3, 3);
        g.setColor(Color.GREEN);
        for (MapObject sp : m.spawns) g.fillOval((int) (sp.x() * k) - 2, (int) ((m.heightPx - sp.y()) * k) - 2, 5, 5);
        for (DoorDef d : m.doors) {
            g.setColor(d.rule.startsWith("zone:") ? Color.MAGENTA : new Color(200, 200, 200));
            g.fillRect((int) (d.bounds.x * k), (int) ((m.heightPx - d.bounds.y - d.bounds.height) * k),
                    Math.max(2, (int) (d.bounds.width * k)), Math.max(2, (int) (d.bounds.height * k)));
        }
        ImageIO.write(img, "png", new File(args[1]));
        System.out.println("rooms " + (m.rooms.size() - 1));
    }
}

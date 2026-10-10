package com.yongsa;

import com.google.gson.Gson;
import com.yongsa.gfx.HdArt;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class HdArtTest {
    @Test void manifestFitsRealImagesAndKeepsWorldScaleIndependent() throws Exception {
        var manifest = new Gson().fromJson(Files.readString(Path.of("assets/data/hd-art.json")), HdArt.Manifest.class);
        var sheets = new HashMap<String, BufferedImage>();
        for (var item : manifest.sprites.entrySet()) {
            HdArt.Entry e = item.getValue();
            if (!sheets.containsKey(e.sheet)) sheets.put(e.sheet, ImageIO.read(Path.of("assets", e.sheet).toFile()));
            BufferedImage image = sheets.get(e.sheet);
            assertNotNull(image, e.sheet);
            assertDoesNotThrow(() -> e.validate(image.getWidth(), image.getHeight()), item.getKey());
        }
        for (String facing : new String[]{"down", "up", "side"}) {
            HdArt.Entry idle = manifest.sprites.get("hero_" + facing);
            HdArt.Entry walk = manifest.sprites.get("hero_" + facing + "_walk");
            assertTrue(idle.height >= 400);
            assertEquals(idle.drawHeight, walk.drawHeight);
            assertEquals(idle.drawWidth, walk.drawWidth);
            assertTrue(idle.drawHeight <= 32, "HD source must not enlarge the actor to hundreds of world units");
        }
        for (String theme : new String[]{"CAMP", "LEFT", "RIGHT", "TOP"})
            for (int i = 0; i < 16; i++) assertNotNull(manifest.sprites.get("floor_" + theme + "_hd" + i));
        for (String id : new String[]{"hero", "enemies", "props"}) {
            BufferedImage sheet = sheets.get("hd/" + id + ".png");
            assertTrue(sheet.getColorModel().hasAlpha(), id);
            assertEquals(0, sheet.getRGB(0, 0) >>> 24, "Empty sprite background must be transparent: " + id);
        }
        for (String id : new String[]{"holy", "blackiron", "bloodmoon", "skyfall", "holy_awakened", "blackiron_awakened", "bloodmoon_awakened", "skyfall_awakened"}) {
            HdArt.Entry weapon = manifest.sprites.get("sword_" + id);
            assertNotNull(weapon, id);
            assertTrue(sheets.get(weapon.sheet).getColorModel().hasAlpha());
        }
    }

    @Test void rejectsOutOfBoundsAndInvalidWorldSize() {
        HdArt.Entry e = new HdArt.Entry();
        e.sheet = "example.png"; e.width = 256; e.height = 256; e.drawWidth = 16; e.drawHeight = 20;
        assertDoesNotThrow(() -> e.validate(512, 512));
        e.x = 300;
        assertThrows(IllegalArgumentException.class, () -> e.validate(512, 512));
        e.x = 0; e.drawHeight = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> e.validate(512, 512));
    }

    @Test void worldArtCoversEveryIllustratedGameplayAssetWithoutReplacingMenuArt() throws Exception {
        var sprites = new Gson().fromJson(Files.readString(Path.of("assets/data/hd-art.json")), HdArt.Manifest.class).sprites;
        for (var item : sprites.entrySet()) {
            String key = item.getKey();
            if (key.startsWith("world_") || key.equals("title_background")) continue;
            var world = sprites.get("world_" + key);
            assertNotNull(world, "Missing gameplay version: " + key);
            assertTrue(world.sheet.startsWith("world/"), key);
            assertNotEquals(item.getValue().sheet, world.sheet, "Menus must retain their own illustration");
            assertEquals(item.getValue().drawWidth, world.drawWidth, key);
            assertEquals(item.getValue().drawHeight, world.drawHeight, key);
        }
        for (int i = 0; i < 6; i++) assertNotNull(sprites.get("world_smith_" + i));
        var sheets = new HashMap<String, BufferedImage>();
        for (var item : sprites.entrySet()) {
            if (!item.getKey().startsWith("world_")) continue;
            var e = item.getValue();
            if (e.sheet.endsWith("/terrain.png") || e.sheet.endsWith("/forge-stage.png")) continue;
            if (!sheets.containsKey(e.sheet)) sheets.put(e.sheet, ImageIO.read(Path.of("assets", e.sheet).toFile()));
            var im = sheets.get(e.sheet);
            assertTrue(im.getColorModel().hasAlpha());
            // A neighboring sprite or cropped limb must never touch the region's outer border.
            for (int x = e.x; x < e.x + e.width; x++) {
                assertTrue((im.getRGB(x, e.y) >>> 24) <= 32, item.getKey() + " top");
                assertTrue((im.getRGB(x, e.y + e.height - 1) >>> 24) <= 32, item.getKey() + " bottom");
            }
            for (int y = e.y; y < e.y + e.height; y++) {
                assertTrue((im.getRGB(e.x, y) >>> 24) <= 32, item.getKey() + " left");
                assertTrue((im.getRGB(e.x + e.width - 1, y) >>> 24) <= 32, item.getKey() + " right");
            }
        }
    }
}

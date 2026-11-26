package com.yongsa;

import com.yongsa.combat.HeroActor;
import com.yongsa.combat.Physics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneLayerMovementTest {
    @Test void lanternAndDirtUseDifferentFootprintsAtTheMirroredMineEntrance() {
        var map = TestData.map();
        for (var link : map.links) link.gateClosed = false;
        var joint = map.paintedJoints.stream().filter(j -> j.id.equals("exit-L1-L2")).findFirst().orElseThrow();
        assertFalse(map.free(joint.x(250 / 512f), joint.y(38 / 512f), 9, 6), "Timber below the lantern");
        assertTrue(map.free(joint.x(275 / 512f), joint.y(60 / 512f), 9, 6), "Dirt between the uprights");
    }

    @Test void heroCrossesRearSleepersBetweenBothCaveMouths() {
        var map = TestData.map();
        for (var link : map.links) link.gateClosed = false;
        var room = map.rooms.stream().filter(r -> r.scene != null && r.scene.id.equals("cave-mine"))
                .findFirst().orElseThrow();
        float[][] route = {{.065f,.435f},{.12f,.44f},{.25f,.38f},{.35f,.40f},{.45f,.435f},
                {.55f,.465f},{.65f,.50f},{.75f,.525f},{.85f,.55f},{.935f,.52f}};
        var hero = new HeroActor();
        hero.x = room.sceneX(route[0][0]);
        hero.y = room.sceneY(route[0][1]);
        for (int i = 1; i < 2 * route.length - 1; i++) {
            float[] uv = route[i < route.length ? i : 2 * route.length - 2 - i];
            float x = room.sceneX(uv[0]), y = room.sceneY(uv[1]);
            assertFalse(Physics.move(map, hero, x - hero.x, y - hero.y, map.tileSize));
            assertEquals(x, hero.x, .03f);
            assertEquals(y, hero.y, .03f);
        }
    }
}

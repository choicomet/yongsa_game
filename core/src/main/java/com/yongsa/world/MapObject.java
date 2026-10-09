package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

import java.util.Collections;
import java.util.Map;

/** spawns / veins / points / interact / decor 레이어의 일반 오브젝트. */
public class MapObject {
    public final String name;
    public final String type;
    /** 점 오브젝트면 폭과 높이가 0. */
    public final Rectangle bounds;
    public final Map<String, String> props;

    public MapObject(String name, String type, Rectangle bounds, Map<String, String> props) {
        this.name = name;
        this.type = type;
        this.bounds = bounds;
        this.props = props == null ? Collections.emptyMap() : props;
    }

    public float x() {
        return bounds.x + bounds.width / 2f;
    }

    public float y() {
        return bounds.y + bounds.height / 2f;
    }

    public String prop(String key) {
        String v = props.get(key);
        return v == null ? "" : v;
    }
}

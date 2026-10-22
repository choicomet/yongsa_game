package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.config.DungeonDef;
import com.yongsa.model.GameSession;
import com.yongsa.render.MapPainter;
import com.yongsa.world.Room;

/**
 * M 지도 (2026-10-10 결정): 열린 던전의 방 배치와 지금 위치, 다음 할 일까지 가는 길(노란 선).
 * 가 본 방은 채워서, 모르는 방은 어둡게, 보스방은 빨간 삼각형으로 보여 준다.
 */
public class MapWindow extends GameWindow implements Disposable {

    private final GameSession s;
    private final MapView view;
    private final Label goal;

    public MapWindow(Skin skin, GameSession s) {
        super("지도", skin);
        this.s = s;
        view = new MapView();
        goal = new Label("", skin, "large-gold");
        add(goal).left().padBottom(8).row();
        add(view).size(980, 540).row();
        Table legend = new Table();
        legend.add(new Label("■ 가 본 방   ■ 모르는 방   ▲ 보스방   ● 포탈   ━ 다음 할 일까지 길   ▶ 용사", skin, "small")).left();
        add(legend).left().padTop(8).row();
        add(button("닫기 (M / Esc)", this::close)).right().width(200).height(46).padTop(8);
    }

    @Override
    public void refresh() {
        goal.setText("다음 할 일: " + s.goal().text());
        pack();
        centerOnStage();
    }

    @Override
    public boolean keyDown(int key) {
        if (key == Input.Keys.M) {
            close();
            return true;
        }
        return super.keyDown(key);
    }

    /** 캠프 + 열린 던전의 방만 담는 영역 (지도를 크게 보이게). */
    private Rectangle openArea() {
        Rectangle a = new Rectangle(s.map.camp);
        for (Room r : s.rooms) if (s.progress.isOpen(s.balance.dungeon(r.dungeon))) a.merge(r.bounds);
        float pad = 96;
        return new Rectangle(a.x - pad, a.y - pad, a.width + pad * 2, a.height + pad * 2);
    }

    @Override
    public void dispose() {
        view.shapes.dispose();
    }

    private class MapView extends Actor {
        final ShapeRenderer shapes = new ShapeRenderer();
        float time;

        @Override
        public void act(float delta) {
            super.act(delta);
            time += delta;
        }

        @Override
        public void draw(Batch batch, float alpha) {
            batch.end();
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapes.setProjectionMatrix(batch.getProjectionMatrix());
            shapes.setTransformMatrix(batch.getTransformMatrix());
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.04f, 0.035f, 0.06f, 1f);
            shapes.rect(getX(), getY(), getWidth(), getHeight());
            Rectangle area = s.inArena() ? new Rectangle(s.map.arena) : openArea();
            float scale = Math.min((getWidth() - 20) / area.width, (getHeight() - 20) / area.height);
            MapPainter p = new MapPainter(s);
            p.setTransform(area.x + area.width / 2, area.y + area.height / 2, getX() + getWidth() / 2, getY() + getHeight() / 2, scale);
            p.paint(shapes, MapPainter.Mode.FULL, time);
            shapes.end();
            batch.begin();
            // 던전 이름
            if (!s.inArena()) {
                var font = skin.getFont("small");
                for (DungeonDef d : s.balance.dungeons) {
                    boolean open = s.progress.isOpen(d);
                    if (!open) continue; // 봉인된 던전은 빨간 입구 표시로만
                    Room first = null;
                    for (Room r : s.rooms) if (r.dungeon.equals(d.id) && first == null) first = r;
                    if (first == null) continue;
                    float x = p.tx(first.bounds.x), y = p.ty(first.bounds.y + first.bounds.height) + 18;
                    font.setColor(open ? 1f : 0.5f, open ? 0.85f : 0.5f, open ? 0.5f : 0.5f, alpha);
                    font.draw(batch, d.name + (open ? "" : " (봉인)") + (s.progress.defeated(d.id) ? " ✓" : ""), x, y);
                }
                font.setColor(1, 1, 1, alpha);
                font.draw(batch, "캠프", p.tx(s.map.camp.x) + 4, p.ty(s.map.camp.y + s.map.camp.height) - 4);
            }
        }
    }
}

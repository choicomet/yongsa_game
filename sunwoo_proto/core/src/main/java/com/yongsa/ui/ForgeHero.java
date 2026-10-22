package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.yongsa.render.Assets;

/**
 * 대장간 강화 창의 "직접 강화하는 용사" (2026-10-12 결정).
 * 모루 위 검을 망치로 두드리고(깡깡), 결과에 따라 자세가 바뀐다.
 * <ul>
 *   <li>성공: 두 주먹을 불끈 쥔다 (검이 금빛으로 빛난다)</li>
 *   <li>유지: 어깨를 으쓱</li>
 *   <li>하락: 고개를 푹 숙인다 (검이 잿빛, 머리 위 먹구름)</li>
 *   <li>파괴: 검이 와장창 깨지며 조각이 흩어지고, 용사는 무릎 꿇고 좌절</li>
 * </ul>
 * 그림은 hero_forge (tools/chars.py hero_forge: 0 서기, 1 망치 들기, 2 내려치기, 3 성공, 4 하락, 5 파괴, 6 유지).
 */
public class ForgeHero extends Actor {

    public enum State { IDLE, HAMMER, SUCCESS, KEEP, DOWN, DESTROY }

    private final Assets assets;
    private final TextureRegion white;
    private State state = State.IDLE;
    private float time;
    private TextureRegion sword;
    private static final float SCALE = 3f;

    public ForgeHero(Assets assets, TextureRegion white) {
        this.assets = assets;
        this.white = white;
        setSize(230, 230);
    }

    public State state() {
        return state;
    }

    /** 다른 무기를 고르거나 탭을 바꾸면 다시 서 있는 자세로. */
    public void idle() {
        state = State.IDLE;
        time = 0;
    }

    public void setSword(TextureRegion icon) {
        if (state == State.IDLE) sword = icon;
    }

    /** 망치질 시작 (강화 버튼을 누른 순간). */
    public void hammer(TextureRegion swordIcon) {
        sword = swordIcon;
        state = State.HAMMER;
        time = 0;
    }

    /** 결과: "success" / "keep" / "down" / "destroy". */
    public void result(String kind) {
        state = switch (kind) {
            case "success" -> State.SUCCESS;
            case "keep" -> State.KEEP;
            case "down" -> State.DOWN;
            default -> State.DESTROY;
        };
        time = 0;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color old = batch.getColor().cpy();
        float x = getX(), y = getY();
        // 바닥 그림자
        batch.setColor(0, 0, 0, 0.35f * parentAlpha);
        batch.draw(white, x + 20, y + 14, 190, 8);
        // 모루 (오른쪽)
        float ax = x + 140, ay = y + 20;
        batch.setColor(0.32f, 0.32f, 0.38f, parentAlpha);
        batch.draw(white, ax + 14, ay, 34, 26);
        batch.draw(white, ax, ay + 26, 70, 14);
        batch.setColor(0.5f, 0.5f, 0.58f, parentAlpha);
        batch.draw(white, ax, ay + 36, 70, 4);
        batch.draw(white, ax - 10, ay + 30, 12, 8);
        // 모루 위 검
        drawSword(batch, ax + 35, ay + 46, parentAlpha);
        // 용사 (3배)
        int pose = switch (state) {
            case IDLE -> 0;
            case HAMMER -> ((int) (time / 0.15f)) % 2 == 0 ? 1 : 2;
            case SUCCESS -> 3;
            case DOWN -> 4;
            case DESTROY -> time < 0.35f ? 2 : 5;
            case KEEP -> 6;
        };
        TextureRegion[] f = assets.frames("hero_forge");
        TextureRegion hero = f[Math.min(pose, f.length - 1)];
        float hop = state == State.SUCCESS ? Math.abs(MathUtils.sin(time * 9)) * 8 * Math.max(0, 1 - time / 1.6f) : 0;
        float shake = state == State.DESTROY && time > 0.35f && time < 0.7f ? MathUtils.random(-2f, 2f) : 0;
        batch.setColor(1, 1, 1, parentAlpha);
        batch.draw(hero, x + 4 + shake, y + 18 + hop, hero.getRegionWidth() * SCALE, hero.getRegionHeight() * SCALE);
        // 망치질 불꽃
        if (state == State.HAMMER && ((int) (time / 0.15f)) % 2 == 1) {
            for (int i = 0; i < 7; i++) {
                float a = 30 + i * 20 + MathUtils.random(-8f, 8f);
                float d = 8 + MathUtils.random(18f);
                batch.setColor(1f, 0.8f, 0.35f, parentAlpha);
                batch.draw(white, ax + 30 + MathUtils.cosDeg(a) * d, ay + 46 + MathUtils.sinDeg(a) * d, 3, 3);
            }
        }
        // 성공: 반짝이
        if (state == State.SUCCESS) {
            for (int i = 0; i < 8; i++) {
                float ph = (time * 0.9f + i * 0.125f) % 1f;
                batch.setColor(1f, 0.92f, 0.5f, (1 - ph) * parentAlpha);
                batch.draw(white, x + 30 + (i * 37) % 170, y + 60 + ph * 120, 3, 3);
            }
        }
        batch.setColor(old);
    }

    private void drawSword(Batch batch, float cx, float cy, float alpha) {
        if (sword == null) return;
        float w = 64, h = 64;
        if (state == State.DESTROY && time > 0.35f) {
            // 와장창: 두 동강 나 튕겨 나가고 조각이 흩어진다
            float t = Math.min(1.2f, time - 0.35f);
            int half = sword.getRegionWidth() / 2;
            TextureRegion a = new TextureRegion(sword, 0, 0, half, sword.getRegionHeight());
            TextureRegion b = new TextureRegion(sword, half, 0, sword.getRegionWidth() - half, sword.getRegionHeight());
            float fall = 60 * t * t;
            float fade = Math.max(0, 1 - t / 1.2f);
            batch.setColor(0.8f, 0.8f, 0.85f, alpha * fade);
            batch.draw(a, cx - w / 2 - 40 * t, cy - h / 2 + 30 * t - fall, w / 4, h / 2, w / 2, h, 1, 1, 160 * t);
            batch.draw(b, cx - 40 * t * -1, cy - h / 2 + 22 * t - fall, w / 4, h / 2, w / 2, h, 1, 1, -200 * t);
            for (int i = 0; i < 14; i++) {
                float ang = i * 26 + 10;
                float d = 10 + 90 * t * (0.5f + (i % 4) * 0.2f);
                batch.setColor(0.85f, 0.9f, 1f, alpha * fade);
                batch.draw(white, cx + MathUtils.cosDeg(ang) * d, cy + MathUtils.sinDeg(ang) * d - fall * 0.6f, 3, 3);
            }
            return;
        }
        Color c = new Color(1, 1, 1, alpha);
        if (state == State.SUCCESS) {
            float g = 0.5f + 0.5f * MathUtils.sin(time * 8);
            c.set(1f, 0.92f + 0.08f * g, 0.6f + 0.4f * g, alpha);
            batch.setColor(1f, 0.9f, 0.4f, 0.35f * alpha);
            batch.draw(white, cx - 34, cy - 4, 68, 8);
        } else if (state == State.DOWN) {
            c.set(0.55f, 0.55f, 0.6f, alpha);
        }
        batch.setColor(c);
        // 모루 위에 눕혀서
        batch.draw(sword, cx - w / 2, cy - h / 2 + 4, w / 2, h / 2, w, h, 1, 1, -45);
    }
}

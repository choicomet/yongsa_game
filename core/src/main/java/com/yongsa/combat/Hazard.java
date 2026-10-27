package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;

/**
 * 바닥·허공에 생기는 위험 지대. 모두 먼저 예고를 보여주고, 그 자리에서 터진다 (따라오지 않는다).
 * <ul>
 *   <li>BONE_SPIKE: 리치의 뼈 가시. 예고 뒤 솟아오른다.</li>
 *   <li>LIGHTNING: 마룡의 번개. 맞으면 감전.</li>
 *   <li>TENTACLE: 마왕 2페이즈의 촉수.</li>
 *   <li>POISON_CLOUD: 마룡의 독구름. 한동안 남아 있고 닿으면 중독(지속 시간 초기화).</li>
 *   <li>SWAMP: 리치의 늪. 점점 넓어지고, 밟으면 지속 피해 + 둔화.</li>
 *   <li>BONE_SPEAR / LASER / WARP: 선분(띠) 모양. 예고 뒤 한 번 친다.</li>
 *   <li>METEOR_FIELD: 마룡 광폭화 메테오. area 전체를 치고, (x, y, radius) 작은 원만 안전.</li>
 *   <li>EYE: 마왕 2의 눈알. 빛을 뿜는 순간 눈을 바라보고 있으면 벌을 받는다.</li>
 *   <li>ABYSS_SIGIL: 반전 마법진. 밟는 동안 키가 뒤바뀐다.</li>
 *   <li>VOID: 공허 균열. 전투 내내 남고 닿으면 큰 지속 피해.</li>
 *   <li>BONE_STORM: 뼛가루 폭풍. 맵 전체 지속 피해, 바위(rock) 뒤 그늘에 있으면 안전. 끝나면 바위가 부서진다.</li>
 *   <li>FLESH_TIDE: 살점 해일. dir 방향으로 area를 쓸고 지나가며, 열린 CRACK 안에 있어야 산다.</li>
 *   <li>CRACK: 숨을 수 있는 좁은 균열. 한 번 쓰면 닫힌다.</li>
 *   <li>SWARM_ROW: 군체의 한 줄. dir 방향으로 지나가며, gaps의 빈틈에 있어야 안 맞는다.</li>
 * </ul>
 */
public class Hazard {

    public enum Type {
        BONE_SPIKE, LIGHTNING, TENTACLE, POISON_CLOUD, SWAMP, BONE_SPEAR,
        LASER, WARP, METEOR_FIELD, EYE, ABYSS_SIGIL, VOID, BONE_STORM, FLESH_TIDE, CRACK, SWARM_ROW
    }

    /** 터진 뒤 그림을 보여주는 시간. */
    public static final float STRIKE_TIME = 0.45f;
    /** 오래 남는 것들이 사라지는 시간. */
    public static final float FADE_TIME = 0.8f;
    /** 공허처럼 끝나지 않는 것의 수명. */
    public static final float FOREVER = 1e9f;

    public final Type type;
    public final float x, y, radius;
    /** 나타나기 전 기다리는 시간. */
    public final float delay;
    /** 예고 시간 (이동해서 피할 시간). */
    public final float warning;
    /** 오래 남는 것의 지속 시간 (나머지는 0). */
    public final float life;
    public final int damage;
    public float time;
    public boolean fired;

    /** 늪: 처음 반지름과 다 자라는 데 걸리는 시간 (radius가 최대). */
    public float startRadius, growTime;
    /** 띠 모양: (x, y)에서 (x2, y2)까지, 폭은 radius × 2. */
    public float x2, y2;
    /** 넓은 범위 (메테오·해일·군체·폭풍). */
    public Rectangle area;
    /** 진행 방향 (해일·군체·폭풍 바람). */
    public float dirX, dirY;
    /** 지속 피해 비율 (용사 최대 체력 대비 초당). */
    public float ratio;
    /** 해일·군체가 움직이는 속도. */
    public float speed;
    /** 군체 줄의 빈틈: 줄을 따라 잰 가운데 위치들 (area 기준 0~길이), 폭은 gapWidth. */
    public float[] gaps;
    public float gapWidth;
    /** 폭풍의 바위 (닿으면 막힘). */
    public Rectangle rock;
    public boolean rockPlaced;
    /** 균열: 이미 한 번 숨겨 줬다 (닫히는 중). */
    public boolean used;
    public float usedTime;
    /** 공간 왜곡 촉수: 보여주는 가짜 쪽 (마왕이 찌르는 방향의 벽). */
    public float decoyX, decoyY;
    /** 눈알: 바라본다고 치는 반각(도), 벌로 거는 기절·봉인 시간. */
    public float eyeAngle, eyeStun, eyeSeal;

    public Hazard(Type type, float x, float y, float radius, float delay, float warning, float life, int damage) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.delay = delay;
        this.warning = warning;
        this.life = life;
        this.damage = damage;
    }

    /** 늪을 만든다: startRadius에서 radius까지 growTime 동안 넓어진다. */
    public static Hazard swamp(float x, float y, float startRadius, float maxRadius, float growTime, float delay,
                               float warning, float life) {
        Hazard h = new Hazard(Type.SWAMP, x, y, maxRadius, delay, warning, life, 0);
        h.startRadius = startRadius;
        h.growTime = growTime;
        return h;
    }

    /** 띠 (뼈창·레이저·왜곡 촉수·공허): (x1,y1)→(x2,y2), 폭 width. */
    public static Hazard line(Type type, float x1, float y1, float x2, float y2, float width, float delay, float warning,
                              float life, int damage) {
        Hazard h = new Hazard(type, x1, y1, width / 2f, delay, warning, life, damage);
        h.x2 = x2;
        h.y2 = y2;
        return h;
    }

    public static Hazard spear(float x1, float y1, float x2, float y2, float width, float delay, float warning, int damage) {
        return line(Type.BONE_SPEAR, x1, y1, x2, y2, width, delay, warning, 0, damage);
    }

    /** 메테오: area 전체, 안전한 작은 원 하나. */
    public static Hazard meteor(Rectangle area, float safeX, float safeY, float safeRadius, float warning, int damage) {
        Hazard h = new Hazard(Type.METEOR_FIELD, safeX, safeY, safeRadius, 0, warning, 0, damage);
        h.area = new Rectangle(area);
        return h;
    }

    /** 해일·군체: area를 dir 방향으로 speed로 지나간다. */
    public static Hazard sweep(Type type, Rectangle area, float dirX, float dirY, float speed, float delay, float warning,
                               int damage) {
        Hazard h = new Hazard(type, area.x + area.width / 2, area.y + area.height / 2, 0, delay, warning, 0, damage);
        h.area = new Rectangle(area);
        h.dirX = dirX;
        h.dirY = dirY;
        h.speed = speed;
        return h;
    }

    // ------------------------------------------------------------------ 시간

    /** 나타난 뒤 지난 시간 (음수면 아직 안 나타남). */
    public float local() {
        return time - delay;
    }

    public boolean visible() {
        return local() >= 0;
    }

    /** 예고 중이면 0~1 진행도, 아니면 -1. */
    public float warnProgress() {
        float t = local();
        if (t < 0 || t >= warning) return -1;
        return t / warning;
    }

    /** 터진 뒤(또는 퍼진 뒤) 지난 시간. */
    public float activeTime() {
        return local() - warning;
    }

    /** 오래 남는 종류. */
    public boolean lingering() {
        return switch (type) {
            case POISON_CLOUD, SWAMP, VOID, ABYSS_SIGIL, BONE_STORM, CRACK -> true;
            default -> false;
        };
    }

    /** 지나가는 종류 (해일·군체): 범위를 다 지나가면 끝. */
    public boolean sweeping() {
        return type == Type.FLESH_TIDE || type == Type.SWARM_ROW;
    }

    public boolean cloudActive() {
        if (type == Type.CRACK && used) return false;
        return lingering() && activeTime() >= 0 && activeTime() < life;
    }

    public boolean done() {
        float a = activeTime();
        if (type == Type.CRACK && used) return time - usedTime >= 0.6f;
        if (lingering()) return a >= life + FADE_TIME;
        if (sweeping()) return a >= sweepLength() / speed + 0.6f;
        if (type == Type.METEOR_FIELD) return a >= 0.8f;
        return a >= STRIKE_TIME;
    }

    // ------------------------------------------------------------------ 지나가는 것: 앞줄 위치

    /** dir 방향으로 area를 잰 길이. */
    public float sweepLength() {
        return Math.abs(dirX) > 0.5f ? area.width : area.height;
    }

    /** 앞줄이 지나간 거리 (0 = 시작 가장자리). */
    public float front() {
        return Math.max(0, activeTime()) * speed;
    }

    /** 점 (px, py)가 dir 방향으로 시작 가장자리에서 얼마나 떨어져 있는지. */
    public float along(float px, float py) {
        if (dirX > 0.5f) return px - area.x;
        if (dirX < -0.5f) return area.x + area.width - px;
        if (dirY > 0.5f) return py - area.y;
        return area.y + area.height - py;
    }

    /** 줄을 따라 잰 위치 (군체 빈틈 판정용). */
    public float across(float px, float py) {
        return Math.abs(dirX) > 0.5f ? py - area.y : px - area.x;
    }

    public boolean inGap(float px, float py) {
        if (gaps == null) return false;
        float c = across(px, py);
        for (float g : gaps) if (Math.abs(c - g) <= gapWidth / 2f) return true;
        return false;
    }

    // ------------------------------------------------------------------ 판정

    /** 지금 반지름: 늪은 점점 넓어진다 (예고 중에는 처음 크기). */
    public float radiusNow() {
        if (type != Type.SWAMP) return radius;
        float t = Math.max(0, activeTime());
        float k = growTime <= 0 ? 1f : Math.min(1f, t / growTime);
        return startRadius + (radius - startRadius) * k;
    }

    public boolean isLine() {
        return type == Type.BONE_SPEAR || type == Type.LASER || type == Type.WARP || type == Type.VOID;
    }

    public boolean contains(float px, float py) {
        if (isLine()) {
            float vx = x2 - x, vy = y2 - y;
            float len2 = vx * vx + vy * vy;
            float k = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x) * vx + (py - y) * vy) / len2));
            float dx = px - (x + vx * k), dy = py - (y + vy * k);
            return dx * dx + dy * dy <= radius * radius;
        }
        if (type == Type.METEOR_FIELD) {
            float dx = px - x, dy = py - y;
            boolean safe = dx * dx + dy * dy <= radius * radius;
            return area.contains(px, py) && !safe;
        }
        float r = radiusNow();
        float dx = px - x, dy = (py - y) / 0.65f; // 바닥 위 타원 (위아래로 납작, 그림과 같은 비율)
        return dx * dx + dy * dy <= r * r;
    }

    /** 메테오의 안전 원 안인지. */
    public boolean inSafe(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= radius * radius;
    }

    /** 폭풍: 바위 뒤 그늘(바람이 불어 가는 쪽) 안에 있는지. */
    public boolean sheltered(float px, float py, float shelterLength) {
        if (rock == null) return false;
        float cx = rock.x + rock.width / 2, cy = rock.y + rock.height / 2;
        float rel = (px - cx) * dirX + (py - cy) * dirY;          // 바람 방향으로 얼마나 뒤에 있나
        float side = Math.abs((px - cx) * -dirY + (py - cy) * dirX); // 옆으로 얼마나 벗어났나
        float half = rock.width / 2f + 4; // 바위는 둥근 덩어리: 어느 방향이든 폭만큼 그늘
        return rel > 0 && rel < shelterLength && side <= half;
    }
}
